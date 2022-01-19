package no.nav.helse.person

import no.nav.helse.Fødselsnummer
import no.nav.helse.Toggle
import no.nav.helse.hendelser.*
import no.nav.helse.hendelser.utbetaling.*
import no.nav.helse.person.Arbeidsgiver.Companion.beregnFeriepengerForAlleArbeidsgivere
import no.nav.helse.person.Arbeidsgiver.Companion.beregnSykepengegrunnlag
import no.nav.helse.person.Arbeidsgiver.Companion.ghostPeriode
import no.nav.helse.person.Arbeidsgiver.Companion.grunnlagForSammenligningsgrunnlag
import no.nav.helse.person.Arbeidsgiver.Companion.harArbeidsgivereMedOverlappendeUtbetaltePerioder
import no.nav.helse.person.Arbeidsgiver.Companion.harNødvendigInntekt
import no.nav.helse.person.Arbeidsgiver.Companion.harUtbetaltPeriode
import no.nav.helse.person.Arbeidsgiver.Companion.harVedtaksperiodeFor
import no.nav.helse.person.Arbeidsgiver.Companion.kanOverstyres
import no.nav.helse.person.Arbeidsgiver.Companion.minstEttSykepengegrunnlagSomIkkeKommerFraSkatt
import no.nav.helse.person.Arbeidsgiver.Companion.nåværendeVedtaksperioder
import no.nav.helse.person.Vedtaksperiode.Companion.ALLE
import no.nav.helse.person.filter.Brukerutbetalingfilter
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.utbetalingstidslinje.*
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler.Companion.NormalArbeidstaker
import no.nav.helse.økonomi.Inntekt
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.math.roundToInt

class Person private constructor(
    private val aktørId: String,
    private val fødselsnummer: Fødselsnummer,
    private val arbeidsgivere: MutableList<Arbeidsgiver>,
    internal val aktivitetslogg: Aktivitetslogg,
    private val opprettet: LocalDateTime,
    private val infotrygdhistorikk: Infotrygdhistorikk,
    private val vilkårsgrunnlagHistorikk: VilkårsgrunnlagHistorikk,
    private var dødsdato: LocalDate?
) : Aktivitetskontekst {
    private companion object {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
    }

    constructor(
        aktørId: String,
        fødselsnummer: Fødselsnummer
    ) : this(aktørId, fødselsnummer, mutableListOf(), Aktivitetslogg(), LocalDateTime.now(), Infotrygdhistorikk(), VilkårsgrunnlagHistorikk(), null)

    private val observers = mutableListOf<PersonObserver>()

    fun håndter(sykmelding: Sykmelding) = håndter(sykmelding, "sykmelding")

    fun håndter(søknad: Søknad) = håndter(søknad, "søknad")

    fun håndter(inntektsmelding: Inntektsmelding) = håndter(inntektsmelding, "inntektsmelding")

    fun håndter(inntektsmelding: InntektsmeldingReplay) {
        registrer(inntektsmelding, "Behandler replay av inntektsmelding")
        finnArbeidsgiver(inntektsmelding).håndter(inntektsmelding)
    }

    private fun håndter(
        hendelse: SykdomstidslinjeHendelse,
        hendelsesmelding: String
    ) {
        registrer(hendelse, "Behandler $hendelsesmelding")
        val arbeidsgiver = finnEllerOpprettArbeidsgiver(hendelse)
        hendelse.fortsettÅBehandle(arbeidsgiver)
    }

    fun håndter(utbetalingshistorikk: Utbetalingshistorikk) {
        utbetalingshistorikk.kontekst(this)
        utbetalingshistorikk.oppdaterHistorikk(infotrygdhistorikk)
        finnArbeidsgiver(utbetalingshistorikk).håndter(utbetalingshistorikk, infotrygdhistorikk)
    }

    fun håndter(utbetalingshistorikk: UtbetalingshistorikkForFeriepenger) {
        utbetalingshistorikk.kontekst(this)

        if (utbetalingshistorikk.skalBeregnesManuelt) {
            val msg = "Person er markert for manuell beregning av feriepenger - aktørId: $aktørId"
            sikkerLogg.info(msg)
            utbetalingshistorikk.info(msg)
            return
        }

        val feriepengeberegner = Feriepengeberegner(
            alder = fødselsnummer.alder(),
            opptjeningsår = utbetalingshistorikk.opptjeningsår,
            utbetalingshistorikkForFeriepenger = utbetalingshistorikk,
            person = this
        )

        val feriepengepengebeløpPersonUtbetaltAvInfotrygd = utbetalingshistorikk.utbetalteFeriepengerTilPerson()
        val beregnetFeriepengebeløpPersonInfotrygd = feriepengeberegner.beregnFeriepengerForInfotrygdPerson().roundToInt()

        if (beregnetFeriepengebeløpPersonInfotrygd != 0 && beregnetFeriepengebeløpPersonInfotrygd !in feriepengepengebeløpPersonUtbetaltAvInfotrygd) {
            sikkerLogg.info(
                """
                Beregnet feriepengebeløp til person i IT samsvarer ikke med faktisk utbetalt beløp
                AktørId: $aktørId
                Faktisk utbetalt beløp: $feriepengepengebeløpPersonUtbetaltAvInfotrygd
                Beregnet beløp: $beregnetFeriepengebeløpPersonInfotrygd
                """.trimIndent()
            )
        }

        utbetalingshistorikk.sikreAtArbeidsgivereEksisterer {
            arbeidsgivere.finnEllerOpprett(it) {
                utbetalingshistorikk.info("Ny arbeidsgiver med organisasjonsnummer %s for denne personen", it)
                Arbeidsgiver(this, it)
            }
        }
        arbeidsgivere.beregnFeriepengerForAlleArbeidsgivere(aktørId, feriepengeberegner, utbetalingshistorikk)

        if (Toggle.SendFeriepengeOppdrag.enabled) {
            aktivitetslogg.info("Feriepenger er utbetalt")
        }
    }

    fun håndter(ytelser: Ytelser) {
        registrer(ytelser, "Behandler historiske utbetalinger og inntekter")
        ytelser.oppdaterHistorikk(infotrygdhistorikk)
        ytelser.lagreDødsdato(this)

        finnArbeidsgiver(ytelser).håndter(ytelser, { arbeidsgiverUtbetalinger(hendelse = it) }, infotrygdhistorikk)
    }

    internal fun arbeidsgiverperiodeFor(orgnummer: String, sykdomstidslinje: Sykdomstidslinje, kuttdato: LocalDate, periode: Periode): Arbeidsgiverperiode? {
        val builder = ArbeidsgiverperiodeBuilder(NormalArbeidstaker)
        infotrygdhistorikk.builder(orgnummer, builder).build(sykdomstidslinje, kuttdato til kuttdato)
        return builder.result(periode)
    }

    private fun arbeidsgiverUtbetalinger(regler: ArbeidsgiverRegler = NormalArbeidstaker, hendelse: IAktivitetslogg): ArbeidsgiverUtbetalinger {
        val skjæringstidspunkter = skjæringstidspunkter()
        return ArbeidsgiverUtbetalinger(
            regler = regler,
            arbeidsgivere = arbeidsgivereMedSykdom().associateWith {
                infotrygdhistorikk.builder(
                    organisasjonsnummer = it.organisasjonsnummer(),
                    builder = it.builder(regler, skjæringstidspunkter, vilkårsgrunnlagHistorikk.inntektsopplysningPerSkjæringstidspunktPerArbeidsgiver(), hendelse)
                )
            },
            infotrygdhistorikk = infotrygdhistorikk,
            alder = fødselsnummer.alder(),
            dødsdato = dødsdato,
            vilkårsgrunnlagHistorikk = vilkårsgrunnlagHistorikk
        )
    }


    fun håndter(utbetalingsgodkjenning: Utbetalingsgodkjenning) {
        registrer(utbetalingsgodkjenning, "Behandler utbetalingsgodkjenning")
        finnArbeidsgiver(utbetalingsgodkjenning).håndter(utbetalingsgodkjenning)
    }

    fun håndter(vilkårsgrunnlag: Vilkårsgrunnlag) {
        registrer(vilkårsgrunnlag, "Behandler vilkårsgrunnlag")
        finnArbeidsgiver(vilkårsgrunnlag).håndter(vilkårsgrunnlag)
    }

    fun håndter(utbetalingsgrunnlag: Utbetalingsgrunnlag) {
        registrer(utbetalingsgrunnlag, "Behandler utbetalingsgrunnlag")
        finnArbeidsgiver(utbetalingsgrunnlag).håndter(utbetalingsgrunnlag)
    }

    fun håndter(simulering: Simulering) {
        registrer(simulering, "Behandler simulering")
        finnArbeidsgiver(simulering).håndter(simulering)
    }

    fun håndter(utbetaling: UtbetalingOverført) {
        registrer(utbetaling, "Behandler utbetaling overført")
        finnArbeidsgiver(utbetaling).håndter(utbetaling)
    }

    fun håndter(utbetaling: UtbetalingHendelse) {
        registrer(utbetaling, "Behandler utbetaling")
        finnArbeidsgiver(utbetaling).håndter(utbetaling)
    }

    fun håndter(påminnelse: Utbetalingpåminnelse) {
        påminnelse.kontekst(this)
        finnArbeidsgiver(påminnelse).håndter(påminnelse)
    }

    fun håndter(påminnelse: PersonPåminnelse) {
        påminnelse.kontekst(this)
        påminnelse.info("Håndterer påminnelse for person")
    }

    fun håndter(påminnelse: Påminnelse) {
        try {
            påminnelse.kontekst(this)
            if (finnArbeidsgiver(påminnelse).håndter(påminnelse)) return
        } catch (err: Aktivitetslogg.AktivitetException) {
            påminnelse.error("Fikk påminnelse uten at vi fant arbeidsgiver eller vedtaksperiode")
        }
        observers.forEach { påminnelse.vedtaksperiodeIkkeFunnet(it) }
    }

    fun håndter(avstemming: Avstemming) {
        avstemming.kontekst(this)
        avstemming.info("Avstemmer utbetalinger og vedtaksperioder")
        val result = Avstemmer(this).toMap()
        observers.forEach { it.avstemt(avstemming.hendelseskontekst(), result) }
    }

    fun håndter(hendelse: OverstyrTidslinje) {
        hendelse.kontekst(this)
        if (arbeidsgivere.kanOverstyres(hendelse)) {
            finnArbeidsgiver(hendelse).håndter(hendelse)
        } else {
            hendelse.error("Kan ikke overstyre en pågående behandling der én eller flere perioder er behandlet ferdig")
        }
        if (hendelse.hasErrorsOrWorse()) {
            observers.forEach { it.revurderingAvvist(hendelse.hendelseskontekst(), hendelse.tilRevurderingAvvistEvent()) }
        }
    }

    fun håndter(hendelse: OverstyrInntekt) {
        hendelse.kontekst(this)
        finnArbeidsgiver(hendelse).håndter(hendelse)

        if (hendelse.hasErrorsOrWorse()) {
            observers.forEach { it.revurderingAvvist(hendelse.hendelseskontekst(), hendelse.tilRevurderingAvvistEvent()) }
        }
    }

    fun håndter(overstyrArbeidsforhold: OverstyrArbeidsforhold) {
        overstyrArbeidsforhold.kontekst(this)
    }

    fun annullert(hendelseskontekst: Hendelseskontekst, event: PersonObserver.UtbetalingAnnullertEvent) {
        observers.forEach { it.annullering(hendelseskontekst, event) }
    }

    internal fun nyInntekt(hendelse: OverstyrInntekt) = finnArbeidsgiver(hendelse).addInntekt(hendelse)

    internal fun overstyrUtkastRevurdering(hendelse: OverstyrTidslinje) {
        val førstePeriode = finnArbeidsgiver(hendelse).førstePeriodeTilRevurdering(hendelse)
        igangsettRevurdering(hendelse, førstePeriode)
    }

    internal fun overstyrUtkastRevurdering(hendelse: Påminnelse) {
        val førstePeriode = finnArbeidsgiver(hendelse).førstePeriodeTilRevurdering(hendelse)
        igangsettRevurdering(hendelse, førstePeriode)
    }

    internal fun overstyrUtkastRevurdering(hendelse: OverstyrInntekt) {
        val førstePeriode = finnArbeidsgiver(hendelse).førstePeriodeTilRevurdering(hendelse)
        igangsettRevurdering(hendelse, førstePeriode)
    }

    internal fun igangsettRevurdering(hendelse: OverstyrTidslinje, vedtaksperiode: Vedtaksperiode) {
        arbeidsgivere.forEach { it.startRevurderingForAlleBerørtePerioder(hendelse, vedtaksperiode) }
        if (hendelse.hasErrorsOrWorse()) return
        vedtaksperiode.revurder(hendelse)
    }

    internal fun igangsettRevurdering(hendelse: Påminnelse, vedtaksperiode: Vedtaksperiode) {
        arbeidsgivere.forEach { it.startRevurderingForAlleBerørtePerioder(hendelse, vedtaksperiode) }
        if (hendelse.hasErrorsOrWorse()) return
        vedtaksperiode.revurder(hendelse)
    }

    internal fun igangsettRevurdering(hendelse: OverstyrInntekt, vedtaksperiode: Vedtaksperiode) {
        arbeidsgivere.forEach { it.startRevurderingForAlleBerørtePerioder(hendelse, vedtaksperiode) }
        if (hendelse.hasErrorsOrWorse()) return
        vedtaksperiode.revurder(hendelse)
    }

    fun vedtaksperiodePåminnet(påminnelse: Påminnelse) {
        observers.forEach { it.vedtaksperiodePåminnet(påminnelse.hendelseskontekst(), påminnelse) }
    }

    fun vedtaksperiodeIkkePåminnet(påminnelse: Påminnelse, tilstandType: TilstandType) {
        observers.forEach { it.vedtaksperiodeIkkePåminnet(påminnelse.hendelseskontekst(), tilstandType) }
    }

    fun opprettOppgaveForSpeilsaksbehandlere(aktivitetslogg: IAktivitetslogg, event: PersonObserver.OpprettOppgaveForSpeilsaksbehandlereEvent) {
        observers.forEach { it.opprettOppgaveForSpeilsaksbehandlere(aktivitetslogg.hendelseskontekst(), event) }
    }

    fun opprettOppgave(aktivitetslogg: IAktivitetslogg, event: PersonObserver.OpprettOppgaveEvent) {
        observers.forEach { it.opprettOppgave(aktivitetslogg.hendelseskontekst(), event) }
    }

    fun vedtaksperiodeAvbrutt(aktivitetslogg: IAktivitetslogg, event: PersonObserver.VedtaksperiodeAvbruttEvent) {
        observers.forEach { it.vedtaksperiodeAvbrutt(aktivitetslogg.hendelseskontekst(), event) }
    }

    @Deprecated("Fjernes til fordel for utbetaling_utbetalt")
    fun vedtaksperiodeUtbetalt(hendelseskontekst: Hendelseskontekst, event: PersonObserver.UtbetaltEvent) {
        observers.forEach { it.vedtaksperiodeUtbetalt(hendelseskontekst, event) }
    }

    fun vedtaksperiodeEndret(aktivitetslogg: IAktivitetslogg, event: PersonObserver.VedtaksperiodeEndretEvent) {
        observers.forEach {
            it.vedtaksperiodeEndret(aktivitetslogg.hendelseskontekst(), event)
            it.personEndret(aktivitetslogg.hendelseskontekst())
        }
    }

    fun vedtaksperiodeReberegnet(hendelseskontekst: Hendelseskontekst) {
        observers.forEach {
            it.vedtaksperiodeReberegnet(hendelseskontekst)
        }
    }

    fun inntektsmeldingReplay(vedtaksperiodeId: UUID) {
        observers.forEach {
            it.inntektsmeldingReplay(fødselsnummer, vedtaksperiodeId)
        }
    }

    fun trengerInntektsmelding(hendelseskontekst: Hendelseskontekst, orgnr: String, event: PersonObserver.ManglendeInntektsmeldingEvent) {
        observers.forEach { it.manglerInntektsmelding(hendelseskontekst, orgnr, event) }
    }

    fun trengerIkkeInntektsmelding(hendelseskontekst: Hendelseskontekst, event: PersonObserver.TrengerIkkeInntektsmeldingEvent) {
        observers.forEach { it.trengerIkkeInntektsmelding(hendelseskontekst, event) }
    }

    internal fun utbetalingUtbetalt(hendelseskontekst: Hendelseskontekst, event: PersonObserver.UtbetalingUtbetaltEvent) {
        observers.forEach { it.utbetalingUtbetalt(hendelseskontekst, event) }
    }

    internal fun utbetalingUtenUtbetaling(hendelseskontekst: Hendelseskontekst, event: PersonObserver.UtbetalingUtbetaltEvent) {
        observers.forEach { it.utbetalingUtenUtbetaling(hendelseskontekst, event) }
    }

    internal fun utbetalingEndret(hendelseskontekst: Hendelseskontekst, event: PersonObserver.UtbetalingEndretEvent) {
        observers.forEach { it.utbetalingEndret(hendelseskontekst, event) }
    }

    internal fun vedtakFattet(hendelseskontekst: Hendelseskontekst, vedtakFattetEvent: PersonObserver.VedtakFattetEvent) {
        observers.forEach { it.vedtakFattet(hendelseskontekst, vedtakFattetEvent) }
    }

    internal fun feriepengerUtbetalt(hendelseskontekst: Hendelseskontekst, feriepengerUtbetaltEvent: PersonObserver.FeriepengerUtbetaltEvent) {
        observers.forEach { it.feriepengerUtbetalt(hendelseskontekst, feriepengerUtbetaltEvent) }
    }

    fun håndter(hendelse: AnnullerUtbetaling) {
        hendelse.kontekst(this)
        arbeidsgivere.finn(hendelse.organisasjonsnummer())?.håndter(hendelse)
            ?: hendelse.error("Finner ikke arbeidsgiver")
    }

    fun håndter(hendelse: Grunnbeløpsregulering) {
        hendelse.kontekst(this)
        arbeidsgivere.finn(hendelse.organisasjonsnummer())?.håndter(arbeidsgivere, hendelse, vilkårsgrunnlagHistorikk)
            ?: hendelse.error("Finner ikke arbeidsgiver")
    }

    fun addObserver(observer: PersonObserver) {
        observers.add(observer)
    }

    internal fun nyesteIdForVilkårsgrunnlagHistorikk() =
        vilkårsgrunnlagHistorikk.sisteId()

    internal fun skjæringstidspunkt(orgnummer: String, sykdomstidslinje: Sykdomstidslinje, periode: Periode) =
        infotrygdhistorikk.skjæringstidspunkt(orgnummer, periode, sykdomstidslinje)

    internal fun skjæringstidspunkt(periode: Periode) =
        Arbeidsgiver.skjæringstidspunkt(arbeidsgivere, periode, infotrygdhistorikk)

    internal fun skjæringstidspunkter() =
        Arbeidsgiver.skjæringstidspunkter(arbeidsgivere, infotrygdhistorikk)

    internal fun skjæringstidspunkterFraSpleis(orgnummer: String) = vilkårsgrunnlagHistorikk.skjæringstidspunkterFraSpleis(orgnummer)

    internal fun kanOverskriveVilkårsgrunnlag(skjæringstidspunkt: LocalDate) = !arbeidsgivere.harUtbetaltPeriode(skjæringstidspunkt)

    internal fun trengerHistorikkFraInfotrygd(hendelse: IAktivitetslogg, cutoff: LocalDateTime? = null): Boolean {
        val tidligsteDato = arbeidsgivereMedSykdom().minOf { it.tidligsteDato() }
        return infotrygdhistorikk.oppfriskNødvendig(hendelse, tidligsteDato, cutoff)
    }

    internal fun trengerHistorikkFraInfotrygd(hendelse: IAktivitetslogg, vedtaksperiode: Vedtaksperiode, cutoff: LocalDateTime? = null) {
        if (trengerHistorikkFraInfotrygd(hendelse, cutoff)) return hendelse.info("Må oppfriske Infotrygdhistorikken")
        hendelse.info("Trenger ikke oppfriske Infotrygdhistorikken, bruker lagret historikk")
        vedtaksperiode.håndterHistorikkFraInfotrygd(hendelse, infotrygdhistorikk)
    }

    internal fun harInfotrygdUtbetalt(orgnummer: String, skjæringstidspunkt: LocalDate) =
        infotrygdhistorikk.harBetalt(orgnummer, skjæringstidspunkt)

    internal fun brukerutbetalingfilter() = Brukerutbetalingfilter.Builder(fødselsnummer)

    internal fun accept(visitor: PersonVisitor) {
        visitor.preVisitPerson(this, opprettet, aktørId, fødselsnummer, dødsdato, vilkårsgrunnlagHistorikk)
        visitor.visitPersonAktivitetslogg(aktivitetslogg)
        aktivitetslogg.accept(visitor)
        visitor.preVisitArbeidsgivere()
        arbeidsgivere.forEach { it.accept(visitor) }
        visitor.postVisitArbeidsgivere()
        infotrygdhistorikk.accept(visitor)
        vilkårsgrunnlagHistorikk.accept(visitor)
        visitor.postVisitPerson(this, opprettet, aktørId, fødselsnummer, dødsdato, vilkårsgrunnlagHistorikk)
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return SpesifikkKontekst("Person", mapOf("fødselsnummer" to fødselsnummer.toString(), "aktørId" to aktørId))
    }

    private fun registrer(hendelse: PersonHendelse, melding: String) {
        hendelse.kontekst(this)
        hendelse.info(melding)
    }

    internal fun invaliderAllePerioder(hendelse: IAktivitetslogg, feilmelding: String?) {
        feilmelding?.also(hendelse::error)
        arbeidsgivere.forEach { it.søppelbøtte(hendelse, ALLE, ForkastetÅrsak.IKKE_STØTTET) }
    }

    internal fun revurderingHarFeilet(event: IAktivitetslogg) {
        arbeidsgivere.forEach { it.håndterRevurderingFeilet(event) }
    }

    private fun finnEllerOpprettArbeidsgiver(hendelse: ArbeidstakerHendelse) =
        finnEllerOpprettArbeidsgiver(hendelse.organisasjonsnummer(), hendelse)

    private fun finnEllerOpprettArbeidsgiver(orgnummer: String, aktivitetslogg: IAktivitetslogg) =
        arbeidsgivere.finnEllerOpprett(orgnummer) {
            aktivitetslogg.info("Ny arbeidsgiver med organisasjonsnummer %s for denne personen", orgnummer)
            Arbeidsgiver(this, orgnummer)
        }

    private fun finnArbeidsgiver(hendelse: ArbeidstakerHendelse) =
        hendelse.organisasjonsnummer().let { orgnr ->
            arbeidsgivere.finn(orgnr) ?: hendelse.severe("Finner ikke arbeidsgiver")
        }

    private fun MutableList<Arbeidsgiver>.finn(orgnr: String) = find { it.organisasjonsnummer() == orgnr }

    private fun MutableList<Arbeidsgiver>.finnEllerOpprett(orgnr: String, creator: () -> Arbeidsgiver) =
        finn(orgnr) ?: run {
            val newValue = creator()
            add(newValue)
            newValue
        }

    internal fun nåværendeVedtaksperioder(filter: VedtaksperiodeFilter) = arbeidsgivere.nåværendeVedtaksperioder(filter).sorted()

    internal fun ghostPeriode(skjæringstidspunkt: LocalDate) = arbeidsgivere.ghostPeriode(skjæringstidspunkt)

    internal fun harNærliggendeUtbetaling(periode: Periode): Boolean {
        val arbeidsgiverperiode = 16L
        val farligOmråde = periode.let { it.start.minusDays(arbeidsgiverperiode) til it.endInclusive.plusYears(3) }
        return arbeidsgivere.any { it.harOverlappendeUtbetaling(farligOmråde)}
    }

    internal fun lagreDødsdato(dødsdato: LocalDate) {
        this.dødsdato = dødsdato
    }

    internal fun vilkårsgrunnlagFor(skjæringstidspunkt: LocalDate) = vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt)

    internal fun lagreVilkårsgrunnlag(skjæringstidspunkt: LocalDate, vilkårsgrunnlag: Vilkårsgrunnlag) {
        vilkårsgrunnlagHistorikk.lagre(skjæringstidspunkt, vilkårsgrunnlag)
    }

    internal fun lagreVilkårsgrunnlagFraInfotrygd(skjæringstidspunkt: LocalDate, periode: Periode, hendelse: IAktivitetslogg) {
        infotrygdhistorikk.lagreVilkårsgrunnlag(skjæringstidspunkt, vilkårsgrunnlagHistorikk, ::kanOverskriveVilkårsgrunnlag) {
            beregnSykepengegrunnlagForInfotrygd(it, periode.start, hendelse)
        }
    }

    internal fun lagreGrunnlagForSykepengegrunnlag(
        orgnummer: String,
        arbeidsgiverInntekt: ArbeidsgiverInntekt,
        skjæringstidspunkt: LocalDate,
        hendelse: PersonHendelse
    ) {
        finnArbeidsgiverForInntekter(orgnummer, hendelse).lagreSykepengegrunnlag(arbeidsgiverInntekt, skjæringstidspunkt, hendelse)
    }

    internal fun lagreGrunnlagForSammenligningsgrunnlag(
        orgnummer: String,
        arbeidsgiverInntekt: ArbeidsgiverInntekt,
        skjæringstidspunkt: LocalDate,
        hendelse: PersonHendelse
    ) {
        finnArbeidsgiverForInntekter(orgnummer, hendelse).lagreSammenligningsgrunnlag(arbeidsgiverInntekt, skjæringstidspunkt, hendelse)
    }

    internal fun lagreSykepengegrunnlagFraInfotrygd(
        orgnummer: String,
        inntektsopplysninger: List<Inntektsopplysning>,
        aktivitetslogg: IAktivitetslogg,
        hendelseId: UUID
    ) {
        finnArbeidsgiverForInntekter(orgnummer, aktivitetslogg).lagreSykepengegrunnlagFraInfotrygd(inntektsopplysninger, hendelseId)
    }

    internal fun beregnSykepengegrunnlag(skjæringstidspunkt: LocalDate, aktivitetslogg: IAktivitetslogg): Sykepengegrunnlag {
        return Sykepengegrunnlag.opprett(arbeidsgivere.beregnSykepengegrunnlag(skjæringstidspunkt), skjæringstidspunkt, aktivitetslogg)
    }

    private fun beregnSykepengegrunnlagForInfotrygd(
        skjæringstidspunkt: LocalDate,
        personensSisteKjenteSykedagIDenSammenhengdendeSykeperioden: LocalDate,
        hendelse: IAktivitetslogg
    ) =
        Sykepengegrunnlag.opprettForInfotrygd(arbeidsgivere.beregnSykepengegrunnlag(skjæringstidspunkt, personensSisteKjenteSykedagIDenSammenhengdendeSykeperioden), skjæringstidspunkt, hendelse)

    internal fun beregnSammenligningsgrunnlag(skjæringstidspunkt: LocalDate) =
        Sammenligningsgrunnlag(arbeidsgivere.grunnlagForSammenligningsgrunnlag(skjæringstidspunkt))

    private fun finnArbeidsgiverForInntekter(arbeidsgiver: String, aktivitetslogg: IAktivitetslogg): Arbeidsgiver {
        return arbeidsgivere.finnEllerOpprett(arbeidsgiver) {
            aktivitetslogg.info("Ny arbeidsgiver med organisasjonsnummer %s for denne personen", arbeidsgiver)
            Arbeidsgiver(this, arbeidsgiver)
        }
    }

    internal fun kanRevurdereInntekt(skjæringstidspunkt: LocalDate) = vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt) != null

    internal fun harVedtaksperiodeForAnnenArbeidsgiver(arbeidsgiver: Arbeidsgiver, skjæringstidspunkt: LocalDate) = arbeidsgivere
        .filter { it != arbeidsgiver }
        .harVedtaksperiodeFor(skjæringstidspunkt)

    internal fun harNødvendigInntekt(skjæringstidspunkt: LocalDate) = arbeidsgivere.harNødvendigInntekt(skjæringstidspunkt)

    internal fun minstEttSykepengegrunnlagSomIkkeKommerFraSkatt(skjæringstidspunkt: LocalDate) = arbeidsgivere.minstEttSykepengegrunnlagSomIkkeKommerFraSkatt(skjæringstidspunkt)

    internal fun harFlereArbeidsgivereMedSykdom() = arbeidsgivereMedSykdom().count() > 1

    private fun arbeidsgivereMedSykdom() = arbeidsgivere.filter(Arbeidsgiver::harSykdom)

    internal fun minimumInntekt(skjæringstidspunkt: LocalDate): Inntekt = fødselsnummer.alder().minimumInntekt(skjæringstidspunkt)

    internal fun kunOvergangFraInfotrygd(vedtaksperiode: Vedtaksperiode) =
        Arbeidsgiver.kunOvergangFraInfotrygd(arbeidsgivere, vedtaksperiode)

    internal fun ingenUkjenteArbeidsgivere(vedtaksperiode: Vedtaksperiode, skjæringstidspunkt: LocalDate) =
        Arbeidsgiver.ingenUkjenteArbeidsgivere(arbeidsgivere, vedtaksperiode, infotrygdhistorikk, skjæringstidspunkt)

    internal fun søppelbøtte(hendelse: IAktivitetslogg, periode: Periode) {
        infotrygdhistorikk.tøm()
        arbeidsgivere.forEach { it.søppelbøtte(hendelse, it.tidligereOgEttergølgende(periode), ForkastetÅrsak.IKKE_STØTTET) }
    }

    internal fun oppdaterHarMinimumInntekt(
        skjæringstidspunkt: LocalDate,
        grunnlagsdata: VilkårsgrunnlagHistorikk.Grunnlagsdata,
        oppfyltKravTilMinimumInntekt: Boolean
    ) {
        vilkårsgrunnlagHistorikk.oppdaterMinimumInntektsvurdering(skjæringstidspunkt, grunnlagsdata, oppfyltKravTilMinimumInntekt)
    }

    internal fun emitOpprettOppgaveForSpeilsaksbehandlereEvent(hendelse: SykdomstidslinjeHendelse) {
        observers.forEach {
            it.opprettOppgaveForSpeilsaksbehandlere(
                hendelse.hendelseskontekst(),
                PersonObserver.OpprettOppgaveForSpeilsaksbehandlereEvent(
                    setOf(hendelse.meldingsreferanseId())
                )
            )
        }
    }

    internal fun emitOpprettOppgaveEvent(hendelse: SykdomstidslinjeHendelse) {
        observers.forEach {
            it.opprettOppgave(
                hendelse.hendelseskontekst(),
                PersonObserver.OpprettOppgaveEvent(
                    setOf(hendelse.meldingsreferanseId())
                )
            )
        }
    }

    internal fun emitHendelseIkkeHåndtert(hendelse: SykdomstidslinjeHendelse) {
        val errorMeldinger = mutableListOf<String>()
        aktivitetslogg.accept(object : AktivitetsloggVisitor {
            override fun visitError(kontekster: List<SpesifikkKontekst>, aktivitet: Aktivitetslogg.Aktivitet.Error, melding: String, tidsstempel: String) {
                if (kontekster
                        .mapNotNull { it.kontekstMap["meldingsreferanseId"] }
                        .map(UUID::fromString)
                        .contains(hendelse.meldingsreferanseId())
                ) {
                    errorMeldinger.add(melding)
                }
            }
        })

        observers.forEach {
            it.hendelseIkkeHåndtert(
                hendelse.hendelseskontekst(),
                PersonObserver.HendelseIkkeHåndtertEvent(
                    hendelse.meldingsreferanseId(),
                    errorMeldinger,
                )
            )
        }
    }

    internal fun harArbeidsgivereMedOverlappendeUtbetaltePerioder(organisasjonsnummer: String, periode: Periode) =
        arbeidsgivere.harArbeidsgivereMedOverlappendeUtbetaltePerioder(organisasjonsnummer, periode)

    internal fun lagreArbeidsforhold(orgnummer: String, arbeidsforhold: List<Vilkårsgrunnlag.Arbeidsforhold>, aktivitetslogg: IAktivitetslogg, skjæringstidspunkt: LocalDate) {
        finnEllerOpprettArbeidsgiver(orgnummer, aktivitetslogg).lagreArbeidsforhold(arbeidsforhold, skjæringstidspunkt)
    }

    internal fun brukOuijaBrettForÅKommunisereMedPotensielleSpøkelser(orgnummerFraAAreg: List<String>, skjæringstidspunkt: LocalDate) {
        val arbeidsgivereMedSykdom = arbeidsgivere.filter { it.harSykdomFor(skjæringstidspunkt) }.map(Arbeidsgiver::organisasjonsnummer)
        if (arbeidsgivereMedSykdom.containsAll(orgnummerFraAAreg)) {
            sikkerLogg.info("Ingen spøkelser, har sykdom hos alle kjente arbeidsgivere antall=${arbeidsgivereMedSykdom.size}")
        } else {
            sikkerLogg.info("Vi har kontakt med spøkelser, fnr=$fødselsnummer, antall=${orgnummerFraAAreg.size}")
        }
    }

    internal fun loggUkjenteOrgnummere(orgnummerFraAAreg: List<String>) {
        val kjenteOrgnummer = arbeidsgivereMedSykdom().map { it.organisasjonsnummer() }
            .filter { it != "0" }
        val orgnummerMedSpleisSykdom = arbeidsgivere.filter { it.harSpleisSykdom() }.map { it.organisasjonsnummer() }

        val manglerIAAReg = kjenteOrgnummer.filter { !orgnummerFraAAreg.contains(it) }
        val spleisOrgnummerManglerIAAreg = kjenteOrgnummer.filter { !orgnummerMedSpleisSykdom.contains(it) }
        val nyeOrgnummer = orgnummerFraAAreg.filter { !kjenteOrgnummer.contains(it) }
        if (spleisOrgnummerManglerIAAreg.isNotEmpty()) {
            sikkerLogg.info("Fant arbeidsgivere i spleis som ikke er i AAReg(${manglerIAAReg}), opprettet(${nyeOrgnummer}) for $fødselsnummer")
        } else if (manglerIAAReg.isNotEmpty()) {
            sikkerLogg.info("Fant arbeidsgivere i IT som ikke er i AAReg(${manglerIAAReg}), opprettet(${nyeOrgnummer}) for $fødselsnummer")
        } else {
            sikkerLogg.info("AAReg kjenner til alle arbeidsgivere i spleis, opprettet (${nyeOrgnummer}) for $fødselsnummer")
        }
    }

    internal fun fyllUtPeriodeMedForventedeDager(hendelse: PersonHendelse, periode: Periode, skjæringstidspunkt: LocalDate) {
        vilkårsgrunnlagFor(skjæringstidspunkt)!!.inntektsopplysningPerArbeidsgiver().keys
            .map { arbeidsgivere.finn(it)!!  }
            .forEach { it.fyllUtPeriodeMedForventedeDager(hendelse, periode) }
    }

    private fun arbeidsgivereMedRelevanteArbeidsforhold(skjæringstidspunkt: LocalDate): List<Arbeidsgiver> =
        arbeidsgivere.filter { it.harRelevantArbeidsforhold(skjæringstidspunkt) }

    internal fun antallArbeidsgivereMedRelevantArbeidsforhold(skjæringstidspunkt: LocalDate) = arbeidsgivereMedRelevanteArbeidsforhold(skjæringstidspunkt).size

    internal fun harVedtaksperiodeForArbeidsgiverMedUkjentArbeidsforhold(skjæringstidspunkt: LocalDate) =
        arbeidsgivere.any { it.harVedtaksperiodeMedUkjentArbeidsforhold(skjæringstidspunkt) }

    internal fun orgnummereMedRelevanteArbeidsforhold(skjæringstidspunkt: LocalDate) = arbeidsgivere
        .filter { it.harRelevantArbeidsforhold(skjæringstidspunkt) }
        .map { it.organisasjonsnummer() }

    internal fun harFlereArbeidsforholdMedUlikStartdato(skjæringstidspunkt: LocalDate) =
        arbeidsgivere.mapNotNull { it.finnFørsteFraværsdag(skjæringstidspunkt) }.sorted().distinct().count() > 1

    internal fun harKunEttAnnetRelevantArbeidsforholdEnn(skjæringstidspunkt: LocalDate, orgnummer: String): Boolean {
        val aktiveArbeidsforhold = arbeidsgivereMedRelevanteArbeidsforhold(skjæringstidspunkt)
        return aktiveArbeidsforhold.size == 1 && aktiveArbeidsforhold.single().organisasjonsnummer() != orgnummer
    }

    internal fun vilkårsprøvEtterNyInntekt(hendelse: OverstyrInntekt) {
        val skjæringstidspunkt = hendelse.skjæringstidspunkt
        val sykepengegrunnlag = beregnSykepengegrunnlag(skjæringstidspunkt, hendelse)
        val sammenligningsgrunnlag = beregnSammenligningsgrunnlag(skjæringstidspunkt)
            ?: hendelse.severe("Fant ikke sammenligningsgrunnlag for skjæringstidspunkt: ${skjæringstidspunkt}. Kan ikke revurdere inntekt.")
        val avviksprosent = sykepengegrunnlag.avviksprosent(sammenligningsgrunnlag.sammenligningsgrunnlag)

        val harAkseptabeltAvvik = Inntektsvurdering.validerAvvik(
            hendelse,
            avviksprosent,
            sykepengegrunnlag.grunnlagForSykepengegrunnlag,
            sammenligningsgrunnlag.sammenligningsgrunnlag
        ) { _, maksimaltTillattAvvik ->
            warn("Har mer enn %.0f %% avvik. Dette støttes foreløpig ikke i Speil. Du må derfor annullere periodene.", maksimaltTillattAvvik)
        }

        when (val grunnlag = vilkårsgrunnlagHistorikk.vilkårsgrunnlagFor(skjæringstidspunkt)) {
            is VilkårsgrunnlagHistorikk.Grunnlagsdata -> {
                val harMinimumInntekt = validerMinimumInntekt(hendelse, fødselsnummer, hendelse.skjæringstidspunkt, sykepengegrunnlag)
                val grunnlagselement = grunnlag.kopierGrunnlagsdataMed(
                    sykepengegrunnlag = sykepengegrunnlag,
                    sammenligningsgrunnlag = sammenligningsgrunnlag,
                    sammenligningsgrunnlagVurdering = harAkseptabeltAvvik,
                    avviksprosent = avviksprosent,
                    minimumInntektVurdering = harMinimumInntekt,
                    meldingsreferanseId = hendelse.meldingsreferanseId()
                )
                hendelse.kontekst(grunnlagselement)
                vilkårsgrunnlagHistorikk.lagre(skjæringstidspunkt, grunnlagselement)
            }
            is VilkårsgrunnlagHistorikk.InfotrygdVilkårsgrunnlag -> hendelse.error("Vilkårsgrunnlaget ligger i infotrygd. Det er ikke støttet i revurdering.")
            else -> hendelse.error("Fant ikke vilkårsgrunnlag. Kan ikke revurdere inntekt.")
        }
    }

    internal fun infotrygdUtbetalingstidslinje(organisasjonsnummer: String) = infotrygdhistorikk.utbetalingstidslinje(organisasjonsnummer)

    internal fun loggførHendelsesreferanse(orgnummer: String, skjæringstidspunkt: LocalDate, meldingsreferanseId: UUID) = arbeidsgivere.forEach { it.loggførHendelsesreferanse(orgnummer, skjæringstidspunkt, meldingsreferanseId) }

}
