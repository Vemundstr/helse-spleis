package no.nav.helse.hendelser

import java.io.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.Alder
import no.nav.helse.Toggle
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.etterlevelse.SubsumsjonObserver
import no.nav.helse.hendelser.Periode.Companion.delvisOverlappMed
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Companion.inneholderDagerEtter
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Companion.subsumsjonsFormat
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.Person
import no.nav.helse.person.SykdomstidslinjeVisitor
import no.nav.helse.person.Sykmeldingsperioder
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.VilkårsgrunnlagHistorikk.VilkårsgrunnlagElement
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.aktivitetslogg.Varselkode.*
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.`Arbeidsledigsøknad er lagt til grunn`
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.`Støtter ikke førstegangsbehandlinger for arbeidsledigsøknader`
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.`Støtter ikke søknadstypen`
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.Companion.replace
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.sykdomstidslinje.merge
import no.nav.helse.tournament.Dagturnering
import no.nav.helse.økonomi.Prosentdel
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import no.nav.helse.økonomi.Økonomi
import org.slf4j.LoggerFactory

class Søknad(
    meldingsreferanseId: UUID,
    fnr: String,
    aktørId: String,
    orgnummer: String,
    private val perioder: List<Søknadsperiode>,
    private val andreInntektskilder: Boolean,
    private val sendtTilNAVEllerArbeidsgiver: LocalDateTime,
    private val permittert: Boolean,
    private val merknaderFraSykmelding: List<Merknad>,
    sykmeldingSkrevet: LocalDateTime,
    private val opprinneligSendt: LocalDateTime?,
    private val utenlandskSykmelding: Boolean,
    private val arbeidUtenforNorge: Boolean,
    private val sendTilGosys: Boolean,
    private val yrkesskade: Boolean,
    private val egenmeldinger: List<Søknadsperiode.Arbeidsgiverdag>,
    private val søknadstype: Søknadstype = Søknadstype.Arbeidstaker,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
) : SykdomstidslinjeHendelse(meldingsreferanseId, fnr, aktørId, orgnummer, sykmeldingSkrevet, Søknad::class, aktivitetslogg) {

    private val sykdomsperiode: Periode
    private val sykdomstidslinje: Sykdomstidslinje
    private val egenmeldingsperiode: Periode?
    private val egenmeldingstidslinje: Sykdomstidslinje
    private var egenmeldingsstart: LocalDate?
    private var egenmeldingsslutt: LocalDate?

    internal companion object {
        internal const val tidslinjegrense = 40L
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        if (perioder.isEmpty()) logiskFeil("Søknad må inneholde perioder")
        sykdomsperiode = Søknadsperiode.sykdomsperiode(perioder) ?: logiskFeil("Søknad inneholder ikke sykdomsperioder")
        if (perioder.inneholderDagerEtter(sykdomsperiode.endInclusive)) logiskFeil("Søknad inneholder dager etter siste sykdomsdag")
        sykdomstidslinje = perioder
            .map { it.sykdomstidslinje(sykdomsperiode, avskjæringsdato(), kilde) }
            .filter { it.periode()?.start?.isAfter(sykdomsperiode.start.minusDays(tidslinjegrense)) ?: false }
            .merge(Dagturnering.SØKNAD::beste)
            .subset(sykdomsperiode)

        egenmeldingsperiode = Søknadsperiode.egenmeldingsperiode(egenmeldinger)
        egenmeldingsstart = egenmeldingsperiode?.start
        egenmeldingsslutt = egenmeldingsperiode?.endInclusive
        egenmeldingstidslinje = egenmeldingsperiode?.let {
            egenmeldinger
                .map { it.sykdomstidslinje(egenmeldingsperiode, avskjæringsdato(), kilde) }
                .merge(Dagturnering.SØKNAD::beste)
                .subset(egenmeldingsperiode)
        } ?: Sykdomstidslinje()

    }

    internal fun erRelevant(other: Periode) = other.overlapperMed(sykdomsperiode)

    override fun sykdomstidslinje(): Sykdomstidslinje {
        val egenmeldingCutoffStart = egenmeldingsstart
        val egenmeldingCutoffEnd = egenmeldingsslutt
        if (Toggle.Egenmelding.enabled) {
            return if (egenmeldingCutoffStart != null && egenmeldingCutoffEnd != null) {
                egenmeldingstidslinje.subset(egenmeldingCutoffStart til egenmeldingCutoffEnd)
                    .merge(sykdomstidslinje, replace)
            } else {
                sykdomstidslinje
            }
        }
        return sykdomstidslinje
    }

    fun loggEgenmeldingsstrekking()  {
        if(egenmeldingstidslinje.periode() != null) {
            val egenmeldingCutoffStart = egenmeldingsstart
            val egenmeldingCutoffEnd = egenmeldingsslutt
            val nySykdomstidslinje = if (egenmeldingCutoffStart != null && egenmeldingCutoffEnd != null) {
                egenmeldingstidslinje.subset(egenmeldingCutoffStart til egenmeldingCutoffEnd)
                    .merge(sykdomstidslinje, replace)
            } else {
                sykdomstidslinje
            }

            sikkerlogg.info("Sykdomstidslinjen ble strukket av egenmelding med {}.\ngammelSykdomstidslinje=$sykdomstidslinje\negenmeldingstidslinje=$egenmeldingstidslinje\nnySykdomstidslinje=$nySykdomstidslinje\n{}",
                StructuredArguments.keyValue("dager", if(nySykdomstidslinje.periode() != null && sykdomstidslinje.periode() != null) ChronoUnit.DAYS.between(nySykdomstidslinje.førsteDag(), sykdomstidslinje.førsteDag()) else "N/A"),
                StructuredArguments.keyValue("søknadId", meldingsreferanseId())
            )
        }
    }

    internal fun delvisOverlappende(other: Periode) = other.delvisOverlappMed(sykdomsperiode)

    override fun valider(periode: Periode, subsumsjonObserver: SubsumsjonObserver): IAktivitetslogg {
        perioder.forEach { it.subsumsjon(this.perioder.subsumsjonsFormat(), subsumsjonObserver) }
        perioder.forEach { it.valider(this) }
        if (permittert) varsel(RV_SØ_1)
        merknaderFraSykmelding.forEach { it.valider(this) }
        val foreldedeDager = ForeldetSubsumsjonsgrunnlag(sykdomstidslinje).build()
        if (foreldedeDager.isNotEmpty()) {
            subsumsjonObserver.`§ 22-13 ledd 3`(avskjæringsdato(), foreldedeDager)
            varsel(RV_SØ_2)
        }
        if (arbeidUtenforNorge) {
            varsel(RV_MV_3)
        }
        if (utenlandskSykmelding) funksjonellFeil(RV_SØ_29)
        if (sendTilGosys) funksjonellFeil(RV_SØ_30)
        if (yrkesskade) varsel(RV_YS_1)
        return this
    }

    internal fun valider(vilkårsgrunnlag: VilkårsgrunnlagElement?): IAktivitetslogg {
        validerInntektskilder(vilkårsgrunnlag)
        søknadstype.valider(this, vilkårsgrunnlag, organisasjonsnummer)
        return this
    }

    private fun validerInntektskilder(vilkårsgrunnlag: VilkårsgrunnlagElement?) {
        if (!andreInntektskilder) return
        if (vilkårsgrunnlag == null) return this.funksjonellFeil(RV_SØ_10)
        this.varsel(RV_SØ_10)
    }

    internal fun utenlandskSykmelding(): Boolean {
        if (utenlandskSykmelding) return true
        return false
    }

    internal fun sendtTilGosys(): Boolean {
        if (sendTilGosys) return true
        return false
    }

    internal fun forUng(alder: Alder) = alder.forUngForÅSøke(sendtTilNAVEllerArbeidsgiver.toLocalDate()).also {
        if (it) funksjonellFeil(RV_SØ_17)
    }
    private fun avskjæringsdato(): LocalDate =
        (opprinneligSendt ?: sendtTilNAVEllerArbeidsgiver).toLocalDate().minusMonths(3).withDayOfMonth(1)

    override fun leggTil(hendelseIder: MutableSet<Dokumentsporing>) =
        hendelseIder.add(Dokumentsporing.søknad(meldingsreferanseId()))


    internal fun lagVedtaksperiode(person: Person, arbeidsgiver: Arbeidsgiver, jurist: MaskinellJurist): Vedtaksperiode {
        val periode = requireNotNull(sykdomstidslinje.periode()) { "ugyldig søknad: tidslinjen er tom" }
        return Vedtaksperiode(
            søknad = this,
            person = person,
            arbeidsgiver = arbeidsgiver,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = organisasjonsnummer,
            sykdomstidslinje = sykdomstidslinje,
            dokumentsporing = Dokumentsporing.søknad(meldingsreferanseId()),
            periode = periode,
            jurist = jurist
        )
    }

    internal fun slettSykmeldingsperioderSomDekkes(arbeidsgiveren: Sykmeldingsperioder) {
        arbeidsgiveren.fjern(sykdomsperiode)
    }

    internal fun trimEgenmeldingsdager(trimFør: LocalDate?, trimEtter: LocalDate) {
        if (trimFør != null) trimEgenmeldingsdagerFør(trimFør)
        trimEgenmeldingsdagerEtter(trimEtter)
    }

    private fun trimEgenmeldingsdagerFør(dato: LocalDate) {
        if (egenmeldingsperiode != null && egenmeldingsperiode.endInclusive <= dato) {
            egenmeldingsstart = null
            egenmeldingsslutt = null
        } else if (egenmeldingsperiode != null && egenmeldingsperiode.start <= dato) {
            egenmeldingsstart = egenmeldingstidslinje.førsteArbeidsgiverdagEtter(dato)
        }
    }

    private fun trimEgenmeldingsdagerEtter(dato: LocalDate) {
        if(egenmeldingsperiode != null && egenmeldingsperiode.start >= dato) {
            egenmeldingsstart = null
            egenmeldingsslutt = null
        } else if (egenmeldingsperiode != null && egenmeldingsperiode.endInclusive >= dato) {
            egenmeldingsslutt = dato.minusDays(1)
        }
    }

    class Merknad(private val type: String) {
        internal fun valider(aktivitetslogg: IAktivitetslogg) {
            if (type == "UGYLDIG_TILBAKEDATERING" || type == "TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER") {
                aktivitetslogg.varsel(RV_SØ_3)
            }
        }
    }

    class Søknadstype(private val type: String) {
        internal fun valider(aktivitetslogg: IAktivitetslogg, vilkårsgrunnlag: VilkårsgrunnlagElement?, organisasjonsnummer: String) {
            if (this == Arbeidstaker) return
            if (this != Arbeidledig) return aktivitetslogg.funksjonellFeil(`Støtter ikke søknadstypen`)
            if (vilkårsgrunnlag == null) return aktivitetslogg.funksjonellFeil(`Støtter ikke førstegangsbehandlinger for arbeidsledigsøknader`)
            if (!vilkårsgrunnlag.validerAnnenSøknadstype(aktivitetslogg, organisasjonsnummer)) return
            aktivitetslogg.varsel(`Arbeidsledigsøknad er lagt til grunn`)
        }
        override fun equals(other: Any?): Boolean {
            if (other !is Søknadstype) return false
            return this.type == other.type
        }
        override fun hashCode() = type.hashCode()
        companion object {
            val Arbeidstaker = Søknadstype("ARBEIDSTAKERE")
            val Arbeidledig = Søknadstype("ARBEIDSLEDIG")
        }
    }

    sealed class Søknadsperiode(fom: LocalDate, tom: LocalDate, private val navn: String) {
        protected val periode = Periode(fom, tom)

        internal companion object {
            fun sykdomsperiode(liste: List<Søknadsperiode>) =
                søknadsperiode(liste.filterIsInstance<Sykdom>())

            fun egenmeldingsperiode(liste: List<Søknadsperiode>) =
                søknadsperiode(liste.filterIsInstance<Arbeidsgiverdag>())

            fun List<Søknadsperiode>.inneholderDagerEtter(sisteSykdomsdato: LocalDate) =
                any { it.periode.endInclusive > sisteSykdomsdato }

            fun List<Søknadsperiode>.subsumsjonsFormat(): List<Map<String, Serializable>> {
                return map { mapOf("fom" to it.periode.start, "tom" to it.periode.endInclusive, "type" to it.navn) }
            }

            fun søknadsperiode(liste: List<Søknadsperiode>) =
                liste
                    .map(Søknadsperiode::periode)
                    .takeIf(List<*>::isNotEmpty)
                    ?.let {
                        it.reduce { champion, challenger ->
                            Periode(
                                fom = minOf(champion.start, challenger.start),
                                tom = maxOf(champion.endInclusive, challenger.endInclusive)
                            )
                        }
                    }
        }

        internal abstract fun sykdomstidslinje(sykdomsperiode: Periode, avskjæringsdato: LocalDate, kilde: Hendelseskilde): Sykdomstidslinje

        internal open fun valider(søknad: Søknad) {}

        internal fun valider(søknad: Søknad, varselkode: Varselkode) {
            if (periode.utenfor(søknad.sykdomsperiode)) søknad.varsel(varselkode)
        }

        internal open fun subsumsjon(søknadsperioder: List<Map<String, Serializable>>, subsumsjonObserver: SubsumsjonObserver) {}

        // TODO: trenger en søknadsperiode for arbeidsgiverdag
        class Sykdom(
            fom: LocalDate,
            tom: LocalDate,
            sykmeldingsgrad: Prosentdel,
            arbeidshelse: Prosentdel? = null
        ) : Søknadsperiode(fom, tom, "sykdom") {
            private val søknadsgrad = arbeidshelse?.not()
            private val sykdomsgrad = søknadsgrad ?: sykmeldingsgrad

            init {
                if (søknadsgrad != null && søknadsgrad > sykmeldingsgrad) throw IllegalStateException("Bruker har oppgitt at de har jobbet mindre enn sykmelding tilsier")
            }

            override fun sykdomstidslinje(sykdomsperiode: Periode, avskjæringsdato: LocalDate, kilde: Hendelseskilde) =
                Sykdomstidslinje.sykedager(periode.start, periode.endInclusive, avskjæringsdato, sykdomsgrad, kilde)
        }

        class Ferie(fom: LocalDate, tom: LocalDate) : Søknadsperiode(fom, tom, "ferie") {
            override fun sykdomstidslinje(sykdomsperiode: Periode, avskjæringsdato: LocalDate, kilde: Hendelseskilde) =
                Sykdomstidslinje.feriedager(periode.start, periode.endInclusive, kilde).subset(sykdomsperiode.oppdaterTom(periode))
        }

        class Arbeidsgiverdag(fom: LocalDate, tom: LocalDate) : Søknadsperiode(fom, tom, "arbeidsgiverdag") {
            override fun sykdomstidslinje(sykdomsperiode: Periode, avskjæringsdato: LocalDate, kilde: Hendelseskilde) =
                Sykdomstidslinje.arbeidsgiverdager(periode.start, periode.endInclusive, 100.prosent, kilde).subset(sykdomsperiode.oppdaterTom(periode))
        }

        class Papirsykmelding(fom: LocalDate, tom: LocalDate) : Søknadsperiode(fom, tom, "papirsykmelding") {
            override fun sykdomstidslinje(sykdomsperiode: Periode, avskjæringsdato: LocalDate, kilde: Hendelseskilde) =
                Sykdomstidslinje.problemdager(periode.start, periode.endInclusive, kilde, "Papirdager ikke støttet")

            override fun valider(søknad: Søknad) =
                søknad.funksjonellFeil(RV_SØ_22)
        }

        class Utdanning(fom: LocalDate, tom: LocalDate) : Søknadsperiode(fom, tom, "utdanning") {
            override fun sykdomstidslinje(sykdomsperiode: Periode, avskjæringsdato: LocalDate, kilde: Hendelseskilde) =
                Sykdomstidslinje.ukjent(periode.start, periode.endInclusive, kilde)

            override fun valider(søknad: Søknad) = søknad.varsel(RV_SØ_4)
        }

        class Permisjon(fom: LocalDate, tom: LocalDate) : Søknadsperiode(fom, tom, "permisjon") {
            override fun sykdomstidslinje(sykdomsperiode: Periode, avskjæringsdato: LocalDate, kilde: Hendelseskilde) =
                Sykdomstidslinje.permisjonsdager(periode.start, periode.endInclusive, kilde)

            override fun valider(søknad: Søknad) {
                valider(søknad, RV_SØ_5)
            }
        }

        class Arbeid(fom: LocalDate, tom: LocalDate) : Søknadsperiode(fom, tom, "arbeid") {
            override fun valider(søknad: Søknad) =
                valider(søknad, RV_SØ_7)

            override fun sykdomstidslinje(sykdomsperiode: Periode, avskjæringsdato: LocalDate, kilde: Hendelseskilde) =
                Sykdomstidslinje.arbeidsdager(periode.start, periode.endInclusive, kilde)
        }

        class Utlandsopphold(fom: LocalDate, tom: LocalDate) : Søknadsperiode(fom, tom, "utlandsopphold") {
            override fun sykdomstidslinje(sykdomsperiode: Periode, avskjæringsdato: LocalDate, kilde: Hendelseskilde) =
                Sykdomstidslinje.ukjent(periode.start, periode.endInclusive, kilde)

            override fun valider(søknad: Søknad) {
                if (alleUtlandsdagerErFerie(søknad)) return
                søknad.varsel(RV_SØ_8)
            }

            override fun subsumsjon(søknadsperioder: List<Map<String, Serializable>>, subsumsjonObserver: SubsumsjonObserver) {
                subsumsjonObserver.`§ 8-9 ledd 1`(false, periode, søknadsperioder)
            }

            private fun alleUtlandsdagerErFerie(søknad:Søknad):Boolean {
                val feriePerioder = søknad.perioder.filterIsInstance<Ferie>()
                return this.periode.all { utlandsdag -> feriePerioder.any { ferie -> ferie.periode.contains(utlandsdag)} }
            }
        }
    }

    private class ForeldetSubsumsjonsgrunnlag(sykdomstidslinje: Sykdomstidslinje) : SykdomstidslinjeVisitor {
        private val foreldedeDager = mutableListOf<LocalDate>()
        init {
            sykdomstidslinje.accept(this)
        }

        fun build() = foreldedeDager.grupperSammenhengendePerioder()

        override fun visitDag(dag: Dag.ForeldetSykedag, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
            foreldedeDager.add(dato)
        }
    }
}
