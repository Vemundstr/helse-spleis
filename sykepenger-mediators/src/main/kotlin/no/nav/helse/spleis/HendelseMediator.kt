package no.nav.helse.spleis

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import no.nav.helse.Personidentifikator
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.etterlevelse.Subsumsjonslogg.Companion.EmptyLog
import no.nav.helse.hendelser.AnmodningOmForkasting
import no.nav.helse.hendelser.AnnullerUtbetaling
import no.nav.helse.hendelser.AvbruttSøknad
import no.nav.helse.hendelser.Dødsmelding
import no.nav.helse.hendelser.ForkastSykmeldingsperioder
import no.nav.helse.hendelser.GjenopplivVilkårsgrunnlag
import no.nav.helse.hendelser.Grunnbeløpsregulering
import no.nav.helse.hendelser.IdentOpphørt
import no.nav.helse.hendelser.Infotrygdendring
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.InntektsmeldingerReplay
import no.nav.helse.hendelser.Migrate
import no.nav.helse.hendelser.MinimumSykdomsgradsvurderingMelding
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.PersonHendelse
import no.nav.helse.hendelser.PersonPåminnelse
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.SkjønnsmessigFastsettelse
import no.nav.helse.hendelser.SykepengegrunnlagForArbeidsgiver
import no.nav.helse.hendelser.Sykmelding
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.hendelser.Utbetalingpåminnelse
import no.nav.helse.hendelser.Utbetalingsgodkjenning
import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.hendelser.UtbetalingshistorikkEtterInfotrygdendring
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.person.Person
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.spleis.db.HendelseRepository
import no.nav.helse.spleis.db.PersonDao
import no.nav.helse.spleis.meldinger.model.AnmodningOmForkastingMessage
import no.nav.helse.spleis.meldinger.model.AnnulleringMessage
import no.nav.helse.spleis.meldinger.model.AvbruttArbeidsledigSøknadMessage
import no.nav.helse.spleis.meldinger.model.AvbruttSøknadMessage
import no.nav.helse.spleis.meldinger.model.AvstemmingMessage
import no.nav.helse.spleis.meldinger.model.DødsmeldingMessage
import no.nav.helse.spleis.meldinger.model.ForkastSykmeldingsperioderMessage
import no.nav.helse.spleis.meldinger.model.GjenopplivVilkårsgrunnlagMessage
import no.nav.helse.spleis.meldinger.model.GrunnbeløpsreguleringMessage
import no.nav.helse.spleis.meldinger.model.HendelseMessage
import no.nav.helse.spleis.meldinger.model.IdentOpphørtMessage
import no.nav.helse.spleis.meldinger.model.InfotrygdendringMessage
import no.nav.helse.spleis.meldinger.model.InntektsmeldingMessage
import no.nav.helse.spleis.meldinger.model.InntektsmeldingerReplayMessage
import no.nav.helse.spleis.meldinger.model.MigrateMessage
import no.nav.helse.spleis.meldinger.model.MinimumSykdomsgradVurdertMessage
import no.nav.helse.spleis.meldinger.model.NyArbeidsledigSøknadMessage
import no.nav.helse.spleis.meldinger.model.NyFrilansSøknadMessage
import no.nav.helse.spleis.meldinger.model.NySelvstendigSøknadMessage
import no.nav.helse.spleis.meldinger.model.NySøknadMessage
import no.nav.helse.spleis.meldinger.model.OverstyrArbeidsforholdMessage
import no.nav.helse.spleis.meldinger.model.OverstyrArbeidsgiveropplysningerMessage
import no.nav.helse.spleis.meldinger.model.OverstyrTidslinjeMessage
import no.nav.helse.spleis.meldinger.model.PersonPåminnelseMessage
import no.nav.helse.spleis.meldinger.model.PåminnelseMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadArbeidsgiverMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadArbeidsledigMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadFrilansMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadNavMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadSelvstendigMessage
import no.nav.helse.spleis.meldinger.model.SimuleringMessage
import no.nav.helse.spleis.meldinger.model.SkjønnsmessigFastsettelseMessage
import no.nav.helse.spleis.meldinger.model.SykepengegrunnlagForArbeidsgiverMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingpåminnelseMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingsgodkjenningMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingshistorikkEtterInfotrygdendringMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingshistorikkForFeriepengerMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingshistorikkMessage
import no.nav.helse.spleis.meldinger.model.VilkårsgrunnlagMessage
import no.nav.helse.spleis.meldinger.model.YtelserMessage
import org.slf4j.LoggerFactory

// Understands how to communicate messages to other objects
// Acts like a GoF Mediator to forward messages to observers
// Uses GoF Observer pattern to notify events
internal class HendelseMediator(
    private val hendelseRepository: HendelseRepository,
    private val personDao: PersonDao,
    private val versjonAvKode: String,
    private val støtterIdentbytte: Boolean = false
) : IHendelseMediator {
    private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
    private val behovMediator = BehovMediator(sikkerLogg)

    override fun behandle(message: HendelseMessage, context: MessageContext) {
        try {
            message.behandle(this, context)
        } catch (err: Aktivitetslogg.AktivitetException) {
            withMDC(err.kontekst()) {
                sikkerLogg.error("alvorlig feil i aktivitetslogg: ${err.message}\n\t${message.toJson()}", err)
            }
        }
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: NySøknadMessage,
        sykmelding: Sykmelding,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        opprettPersonOgHåndter(personopplysninger, message, sykmelding, context, historiskeFolkeregisteridenter) { person, aktivitetslogg ->
            HendelseProbe.onSykmelding()
            person.håndter(sykmelding, aktivitetslogg)
        }
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: NyFrilansSøknadMessage,
        sykmelding: Sykmelding,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        opprettPersonOgHåndter(personopplysninger, message, sykmelding, context, historiskeFolkeregisteridenter) { person, aktivitetslogg ->
            HendelseProbe.onSykmelding()
            person.håndter(sykmelding, aktivitetslogg)
        }
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: NySelvstendigSøknadMessage,
        sykmelding: Sykmelding,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        opprettPersonOgHåndter(personopplysninger, message, sykmelding, context, historiskeFolkeregisteridenter) { person, aktivitetslogg ->
            HendelseProbe.onSykmelding()
            person.håndter(sykmelding, aktivitetslogg)
        }
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: NyArbeidsledigSøknadMessage,
        sykmelding: Sykmelding,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        opprettPersonOgHåndter(personopplysninger, message, sykmelding, context, historiskeFolkeregisteridenter) { person, aktivitetslogg ->
            HendelseProbe.onSykmelding()
            person.håndter(sykmelding, aktivitetslogg)
        }
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadArbeidsgiverMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        opprettPersonOgHåndter(personopplysninger, message, søknad, context, historiskeFolkeregisteridenter) { person, aktivitetslogg ->
            HendelseProbe.onSøknadArbeidsgiver()
            person.håndter(søknad, aktivitetslogg)
        }
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadNavMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        opprettPersonOgHåndter(personopplysninger, message, søknad, context, historiskeFolkeregisteridenter) { person, aktivitetslogg ->
            HendelseProbe.onSøknadNav()
            person.håndter(søknad, aktivitetslogg)
        }
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadFrilansMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        opprettPersonOgHåndter(personopplysninger, message, søknad, context, historiskeFolkeregisteridenter) { person, aktivitetslogg ->
            HendelseProbe.onSøknadFrilans()
            person.håndter(søknad, aktivitetslogg)
        }
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadSelvstendigMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        opprettPersonOgHåndter(personopplysninger, message, søknad, context, historiskeFolkeregisteridenter) { person, aktivitetslogg ->
            HendelseProbe.onSøknadFrilans()
            person.håndter(søknad, aktivitetslogg)
        }
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadArbeidsledigMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        opprettPersonOgHåndter(personopplysninger, message, søknad, context, historiskeFolkeregisteridenter) { person, aktivitetslogg ->
            HendelseProbe.onSøknadFrilans()
            person.håndter(søknad, aktivitetslogg)
        }
    }

    override fun behandle(personopplysninger: Personopplysninger, message: InntektsmeldingMessage, inntektsmelding: Inntektsmelding, context: MessageContext) {
        opprettPersonOgHåndter(personopplysninger, message, inntektsmelding, context, emptySet()) { person, aktivitetslogg ->
            HendelseProbe.onInntektsmelding()
            person.håndter(inntektsmelding, aktivitetslogg)
        }
    }

    override fun behandle(message: InntektsmeldingerReplayMessage, replays: InntektsmeldingerReplay, context: MessageContext) {
        hentPersonOgHåndter(message, replays, context) { person, aktivitetslogg ->
            HendelseProbe.onInntektsmeldingReplay()
            person.håndter(replays, aktivitetslogg)
        }
    }

    override fun behandle(message: UtbetalingshistorikkMessage, utbetalingshistorikk: Utbetalingshistorikk, context: MessageContext) {
        hentPersonOgHåndter(message, utbetalingshistorikk, context) { person, aktivitetslogg ->
            HendelseProbe.onUtbetalingshistorikk()
            person.håndter(utbetalingshistorikk, aktivitetslogg)
        }
    }

    override fun behandle(message: UtbetalingshistorikkForFeriepengerMessage, utbetalingshistorikkForFeriepenger: UtbetalingshistorikkForFeriepenger, context: MessageContext) {
        hentPersonOgHåndter(message, utbetalingshistorikkForFeriepenger, context) { person, aktivitetslogg ->
            HendelseProbe.onUtbetalingshistorikkForFeriepenger()
            person.håndter(utbetalingshistorikkForFeriepenger, aktivitetslogg)
        }
    }

    override fun behandle(message: YtelserMessage, ytelser: Ytelser, context: MessageContext) {
        hentPersonOgHåndter(message, ytelser, context) { person, aktivitetslogg ->
            HendelseProbe.onYtelser()
            person.håndter(ytelser, aktivitetslogg)
        }
    }

    override fun behandle(
        message: SykepengegrunnlagForArbeidsgiverMessage,
        sykepengegrunnlagForArbeidsgiver: SykepengegrunnlagForArbeidsgiver,
        context: MessageContext
    ) {
        hentPersonOgHåndter(message, sykepengegrunnlagForArbeidsgiver, context) { person, aktivitetslogg ->
            person.håndter(sykepengegrunnlagForArbeidsgiver, aktivitetslogg)
        }
    }

    override fun behandle(message: VilkårsgrunnlagMessage, vilkårsgrunnlag: Vilkårsgrunnlag, context: MessageContext) {
        hentPersonOgHåndter(message, vilkårsgrunnlag, context) { person, aktivitetslogg ->
            HendelseProbe.onVilkårsgrunnlag()
            person.håndter(vilkårsgrunnlag, aktivitetslogg)
        }
    }

    override fun behandle(message: SimuleringMessage, simulering: Simulering, context: MessageContext) {
        hentPersonOgHåndter(message, simulering, context) { person, aktivitetslogg ->
            HendelseProbe.onSimulering()
            person.håndter(simulering, aktivitetslogg)
        }
    }

    override fun behandle(message: UtbetalingsgodkjenningMessage, utbetalingsgodkjenning: Utbetalingsgodkjenning, context: MessageContext) {
        hentPersonOgHåndter(message, utbetalingsgodkjenning, context) { person, aktivitetslogg ->
            HendelseProbe.onUtbetalingsgodkjenning()
            person.håndter(utbetalingsgodkjenning, aktivitetslogg)
        }
    }

    override fun behandle(message: UtbetalingMessage, utbetaling: UtbetalingHendelse, context: MessageContext) {
        hentPersonOgHåndter(message, utbetaling, context) { person, aktivitetslogg ->
            HendelseProbe.onUtbetaling()
            person.håndter(utbetaling, aktivitetslogg)
        }
    }

    override fun behandle(message: UtbetalingpåminnelseMessage, påminnelse: Utbetalingpåminnelse, context: MessageContext) {
        hentPersonOgHåndter(message, påminnelse, context) { person, aktivitetslogg ->
            person.håndter(påminnelse, aktivitetslogg)
        }
    }

    override fun behandle(message: PåminnelseMessage, påminnelse: Påminnelse, context: MessageContext) {
        hentPersonOgHåndter(message, påminnelse, context) { person, aktivitetslogg ->
            HendelseProbe.onPåminnelse(påminnelse)
            person.håndter(påminnelse, aktivitetslogg)
        }
    }

    override fun behandle(message: PersonPåminnelseMessage, påminnelse: PersonPåminnelse, context: MessageContext) {
        hentPersonOgHåndter(message, påminnelse, context) { person, aktivitetslogg ->
            person.håndter(påminnelse, aktivitetslogg)
        }
    }

    override fun behandle(
        message: AnmodningOmForkastingMessage,
        anmodning: AnmodningOmForkasting,
        context: MessageContext
    ) {
        hentPersonOgHåndter(message, anmodning, context) {person, aktivitetslogg ->
            person.håndter(anmodning, aktivitetslogg)
        }
    }

    override fun behandle(message: AnnulleringMessage, annullerUtbetaling: AnnullerUtbetaling, context: MessageContext) {
        hentPersonOgHåndter(message, annullerUtbetaling, context) { person, aktivitetslogg ->
            HendelseProbe.onAnnullerUtbetaling()
            person.håndter(annullerUtbetaling, aktivitetslogg)
        }
    }

    override fun behandle(message: AvstemmingMessage, personidentifikator: Personidentifikator, aktørId: String, context: MessageContext) {
        person(personidentifikator, message, aktørId, emptySet(), EmptyLog, null) { person  ->
            val dto = person.dto()
            val avstemmer = Avstemmer(dto)
            context.publish(avstemmer.tilJsonMessage().toJson().also {
                sikkerLogg.info("sender person_avstemt:\n$it")
            })
        }
    }

    override fun behandle(message: MigrateMessage, migrate: Migrate, context: MessageContext) {
        hentPersonOgHåndter(message, migrate, context) { _, _ -> /* intentionally left blank */ }
    }

    override fun behandle(message: OverstyrTidslinjeMessage, overstyrTidslinje: OverstyrTidslinje, context: MessageContext) {
        hentPersonOgHåndter(message, overstyrTidslinje, context) { person, aktivitetslogg ->
            HendelseProbe.onOverstyrTidslinje()
            person.håndter(overstyrTidslinje, aktivitetslogg)
        }
    }

    override fun behandle(message: OverstyrArbeidsgiveropplysningerMessage, overstyrArbeidsgiveropplysninger: OverstyrArbeidsgiveropplysninger, context: MessageContext) {
        hentPersonOgHåndter(message, overstyrArbeidsgiveropplysninger, context) { person, aktivitetslogg ->
            HendelseProbe.onOverstyrArbeidsgiveropplysninger()
            person.håndter(overstyrArbeidsgiveropplysninger, aktivitetslogg)
        }
    }

    override fun behandle(message: OverstyrArbeidsforholdMessage, overstyrArbeidsforhold: OverstyrArbeidsforhold, context: MessageContext) {
        hentPersonOgHåndter(message, overstyrArbeidsforhold, context) { person, aktivitetslogg ->
            HendelseProbe.onOverstyrArbeidsforhold()
            person.håndter(overstyrArbeidsforhold, aktivitetslogg)
        }
    }

    override fun behandle(message: GrunnbeløpsreguleringMessage, grunnbeløpsregulering: Grunnbeløpsregulering, context: MessageContext) {
        hentPersonOgHåndter(message, grunnbeløpsregulering, context) { person, aktivitetslogg ->
            person.håndter(grunnbeløpsregulering, aktivitetslogg)
        }
    }

    override fun behandle(message: DødsmeldingMessage, dødsmelding: Dødsmelding, context: MessageContext) {
        hentPersonOgHåndter(message, dødsmelding, context) { person, aktivitetslogg ->
            person.håndter(dødsmelding, aktivitetslogg)
        }
    }

    override fun behandle(
        nyPersonidentifikator: Personidentifikator,
        message: IdentOpphørtMessage,
        identOpphørt: IdentOpphørt,
        nyAktørId: String,
        gamleIdenter: Set<Personidentifikator>,
        context: MessageContext
    ) {
        hentPersonOgHåndter(nyPersonidentifikator, null, message, identOpphørt, context, gamleIdenter) { person, aktivitetslogg ->
            if (støtterIdentbytte) {
                person.håndter(identOpphørt, aktivitetslogg, nyPersonidentifikator, nyAktørId)
            } else {
                person.håndter(identOpphørt, aktivitetslogg, nyPersonidentifikator)
            }
            context.publish(JsonMessage.newMessage("slackmelding", mapOf(
                "melding" to "Det er en person som har byttet ident."
            )).toJson())
        }
    }

    override fun behandle(
        message: InfotrygdendringMessage,
        infotrygdEndring: Infotrygdendring,
        context: MessageContext
    ) {
        hentPersonOgHåndter(message, infotrygdEndring, context) { person, aktivitetslogg ->
            HendelseProbe.onInfotrygdendring()
            person.håndter(infotrygdEndring, aktivitetslogg)
        }
    }

    override fun behandle(
        message: UtbetalingshistorikkEtterInfotrygdendringMessage,
        utbetalingshistorikkEtterInfotrygdendring: UtbetalingshistorikkEtterInfotrygdendring,
        context: MessageContext
    ) {
        hentPersonOgHåndter(message, utbetalingshistorikkEtterInfotrygdendring, context) { person, aktivitetslogg ->
            HendelseProbe.onUtbetalingshistorikkEtterInfotrygdendring()
            person.håndter(utbetalingshistorikkEtterInfotrygdendring, aktivitetslogg)
        }
    }

    override fun behandle(
        message: ForkastSykmeldingsperioderMessage,
        forkastSykmeldingsperioder: ForkastSykmeldingsperioder,
        context: MessageContext
    ) {
        hentPersonOgHåndter(message, forkastSykmeldingsperioder, context) { person, aktivitetslogg ->
            HendelseProbe.onForkastSykmeldingsperioder()
            person.håndter(forkastSykmeldingsperioder, aktivitetslogg)
        }
    }
    override fun behandle(
        message: AvbruttSøknadMessage,
        avbruttSøknad: AvbruttSøknad,
        context: MessageContext
    ) {
        hentPersonOgHåndter(message, avbruttSøknad, context) { person, aktivitetslogg ->
            HendelseProbe.onAvbruttSøknad()
            person.håndter(avbruttSøknad, aktivitetslogg)
        }
    }
    override fun behandle(
        message: AvbruttArbeidsledigSøknadMessage,
        avbruttSøknad: AvbruttSøknad,
        context: MessageContext
    ) {
        hentPersonOgHåndter(message, avbruttSøknad, context) { person, aktivitetslogg ->
            HendelseProbe.onAvbruttSøknad()
            person.håndter(avbruttSøknad, aktivitetslogg)
        }
    }

    override fun behandle(
        message: GjenopplivVilkårsgrunnlagMessage,
        gjenopplivVilkårsgrunnlag: GjenopplivVilkårsgrunnlag,
        context: MessageContext
    ) {
        hentPersonOgHåndter(message, gjenopplivVilkårsgrunnlag, context) { person, aktivitetslogg ->
            person.håndter(gjenopplivVilkårsgrunnlag, aktivitetslogg)
        }
    }

    override fun behandle(
        message: SkjønnsmessigFastsettelseMessage,
        skjønnsmessigFastsettelse: SkjønnsmessigFastsettelse,
        context: MessageContext
    ) {
        hentPersonOgHåndter(message, skjønnsmessigFastsettelse, context) { person, aktivitetslogg ->
            person.håndter(skjønnsmessigFastsettelse, aktivitetslogg)
        }
    }

    override fun behandle(
        message: MinimumSykdomsgradVurdertMessage,
        minimumSykdomsgradsvurdering: MinimumSykdomsgradsvurderingMelding,
        context: MessageContext
    ) {
        if (minimumSykdomsgradsvurdering.valider()) {
            hentPersonOgHåndter(message, minimumSykdomsgradsvurdering, context) { person, aktivitetslogg ->
                person.håndter(minimumSykdomsgradsvurdering, aktivitetslogg)
            }
        }
    }

    private fun <Hendelse : PersonHendelse> opprettPersonOgHåndter(
        personopplysninger: Personopplysninger,
        message: HendelseMessage,
        hendelse: Hendelse,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>,
        handler: (Person, IAktivitetslogg) -> Unit
    ) {
        val personidentifikator = Personidentifikator(hendelse.behandlingsporing.fødselsnummer)
        hentPersonOgHåndter(personidentifikator, personopplysninger, message, hendelse, context, historiskeFolkeregisteridenter, handler)
    }

    private fun <Hendelse : PersonHendelse> hentPersonOgHåndter(
        message: HendelseMessage,
        hendelse: Hendelse,
        context: MessageContext,
        handler: (Person, IAktivitetslogg) -> Unit
    ) {
        val personidentifikator = Personidentifikator(hendelse.behandlingsporing.fødselsnummer)
        hentPersonOgHåndter(personidentifikator, null, message, hendelse, context, handler = handler)
    }
    private fun <Hendelse : PersonHendelse> hentPersonOgHåndter(
        personidentifikator: Personidentifikator,
        personopplysninger: Personopplysninger?,
        message: HendelseMessage,
        hendelse: Hendelse,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator> = emptySet(),
        handler: (Person, IAktivitetslogg) -> Unit
    ) {
        val aktivitetslogg = Aktivitetslogg()
        aktivitetslogg.kontekst(hendelse)

        val subsumsjonMediator = SubsumsjonMediator(hendelse.behandlingsporing.fødselsnummer, message, versjonAvKode)
        val personMediator = PersonMediator(message, hendelse)
        val datadelingMediator = DatadelingMediator(aktivitetslogg, hendelse.metadata.meldingsreferanseId, hendelse.behandlingsporing.fødselsnummer, hendelse.behandlingsporing.aktørId)
        person(personidentifikator, message, hendelse.behandlingsporing.aktørId, historiskeFolkeregisteridenter, subsumsjonMediator, personopplysninger) { person  ->
            person.addObserver(personMediator)
            person.addObserver(VedtaksperiodeProbe)
            handler(person, aktivitetslogg)
        }
        ferdigstill(context, personMediator, subsumsjonMediator, datadelingMediator, hendelse, aktivitetslogg)
    }

    private fun person(personidentifikator: Personidentifikator, message: HendelseMessage, aktørId: String, historiskeFolkeregisteridenter: Set<Personidentifikator>, subsumsjonslogg: Subsumsjonslogg, personopplysninger: Personopplysninger?, block: (Person) -> Unit) {
        personDao.hentEllerOpprettPerson(
            subsumsjonslogg = subsumsjonslogg,
            personidentifikator = personidentifikator,
            historiskeFolkeregisteridenter = historiskeFolkeregisteridenter,
            aktørId = aktørId,
            message = message,
            hendelseRepository = hendelseRepository,
            lagNyPerson = { personopplysninger?.person(subsumsjonslogg) },
            håndterPerson = { person -> person.also(block) }
        )
    }

    private fun ferdigstill(
        context: MessageContext,
        personMediator: PersonMediator,
        subsumsjonMediator: SubsumsjonMediator,
        datadelingMediator: DatadelingMediator,
        hendelse: PersonHendelse,
        aktivitetslogg: IAktivitetslogg
    ) {
        personMediator.ferdigstill(context)
        subsumsjonMediator.ferdigstill(context)
        datadelingMediator.ferdigstill(context)
        if (!aktivitetslogg.harAktiviteter()) return
        if (aktivitetslogg.harFunksjonelleFeilEllerVerre()) sikkerLogg.info("aktivitetslogg inneholder errors:\n${aktivitetslogg.toString()}")
        else sikkerLogg.info("aktivitetslogg inneholder meldinger:\n${aktivitetslogg.toString()}")
        behovMediator.håndter(context, hendelse, aktivitetslogg)
    }
}

internal interface IHendelseMediator {
    fun behandle(message: HendelseMessage, context: MessageContext)
    fun behandle(
        personopplysninger: Personopplysninger,
        message: NySøknadMessage,
        sykmelding: Sykmelding,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    )
    fun behandle(
        personopplysninger: Personopplysninger,
        message: NyFrilansSøknadMessage,
        sykmelding: Sykmelding,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    )
    fun behandle(
        personopplysninger: Personopplysninger,
        message: NySelvstendigSøknadMessage,
        sykmelding: Sykmelding,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    )
    fun behandle(
        personopplysninger: Personopplysninger,
        message: NyArbeidsledigSøknadMessage,
        sykmelding: Sykmelding,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    )
    fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadArbeidsgiverMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    )
    fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadFrilansMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    )
    fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadSelvstendigMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    )
    fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadArbeidsledigMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    )
    fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadNavMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    )
    fun behandle(personopplysninger: Personopplysninger, message: InntektsmeldingMessage, inntektsmelding: Inntektsmelding, context: MessageContext)
    fun behandle(message: InntektsmeldingerReplayMessage, replays: InntektsmeldingerReplay, context: MessageContext)
    fun behandle(message: UtbetalingpåminnelseMessage, påminnelse: Utbetalingpåminnelse, context: MessageContext)
    fun behandle(message: PåminnelseMessage, påminnelse: Påminnelse, context: MessageContext)
    fun behandle(message: PersonPåminnelseMessage, påminnelse: PersonPåminnelse, context: MessageContext)
    fun behandle(message: AnmodningOmForkastingMessage, anmodning: AnmodningOmForkasting, context: MessageContext)
    fun behandle(message: UtbetalingshistorikkMessage, utbetalingshistorikk: Utbetalingshistorikk, context: MessageContext)
    fun behandle(message: UtbetalingshistorikkForFeriepengerMessage, utbetalingshistorikkForFeriepenger: UtbetalingshistorikkForFeriepenger, context: MessageContext)
    fun behandle(message: YtelserMessage, ytelser: Ytelser, context: MessageContext)
    fun behandle(message: SykepengegrunnlagForArbeidsgiverMessage, sykepengegrunnlagForArbeidsgiver: SykepengegrunnlagForArbeidsgiver, context: MessageContext)
    fun behandle(message: VilkårsgrunnlagMessage, vilkårsgrunnlag: Vilkårsgrunnlag, context: MessageContext)
    fun behandle(message: UtbetalingsgodkjenningMessage, utbetalingsgodkjenning: Utbetalingsgodkjenning, context: MessageContext)
    fun behandle(message: UtbetalingMessage, utbetaling: UtbetalingHendelse, context: MessageContext)
    fun behandle(message: SimuleringMessage, simulering: Simulering, context: MessageContext)
    fun behandle(message: AnnulleringMessage, annullerUtbetaling: AnnullerUtbetaling, context: MessageContext)
    fun behandle(message: AvstemmingMessage, personidentifikator: Personidentifikator, aktørId: String, context: MessageContext)
    fun behandle(message: MigrateMessage, migrate: Migrate, context: MessageContext)
    fun behandle(message: OverstyrTidslinjeMessage, overstyrTidslinje: OverstyrTidslinje, context: MessageContext)
    fun behandle(message: OverstyrArbeidsgiveropplysningerMessage, overstyrArbeidsgiveropplysninger: OverstyrArbeidsgiveropplysninger, context: MessageContext)
    fun behandle(message: OverstyrArbeidsforholdMessage, overstyrArbeidsforhold: OverstyrArbeidsforhold, context: MessageContext)
    fun behandle(message: GrunnbeløpsreguleringMessage, grunnbeløpsregulering: Grunnbeløpsregulering, context: MessageContext)
    fun behandle(message: InfotrygdendringMessage, infotrygdEndring: Infotrygdendring, context: MessageContext)
    fun behandle(message: DødsmeldingMessage, dødsmelding: Dødsmelding, context: MessageContext)
    fun behandle(nyPersonidentifikator: Personidentifikator, message: IdentOpphørtMessage, identOpphørt: IdentOpphørt, nyAktørId: String, gamleIdenter: Set<Personidentifikator>, context: MessageContext)
    fun behandle(message: UtbetalingshistorikkEtterInfotrygdendringMessage, utbetalingshistorikkEtterInfotrygdendring: UtbetalingshistorikkEtterInfotrygdendring, context: MessageContext)
    fun behandle(message: ForkastSykmeldingsperioderMessage, forkastSykmeldingsperioder: ForkastSykmeldingsperioder, context: MessageContext)
    fun behandle(avbruttSøknadMessage: AvbruttSøknadMessage, avbruttSøknad: AvbruttSøknad, context: MessageContext)
    fun behandle(avbruttArbeidsledigSøknadMessage: AvbruttArbeidsledigSøknadMessage, avbruttSøknad: AvbruttSøknad, context: MessageContext)
    fun behandle(message: GjenopplivVilkårsgrunnlagMessage, gjenopplivVilkårsgrunnlag: GjenopplivVilkårsgrunnlag, context: MessageContext)
    fun behandle(message: SkjønnsmessigFastsettelseMessage, skjønnsmessigFastsettelse: SkjønnsmessigFastsettelse, context: MessageContext)
    fun behandle(message: MinimumSykdomsgradVurdertMessage, minimumSykdomsgradsvurdering: MinimumSykdomsgradsvurderingMelding, context: MessageContext)
}
