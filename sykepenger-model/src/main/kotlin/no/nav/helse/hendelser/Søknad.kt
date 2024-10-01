package no.nav.helse.hendelser

import java.io.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.helse.Alder
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.etterlevelse.`§ 22-13 ledd 3`
import no.nav.helse.etterlevelse.`§ 8-9 ledd 1`
import no.nav.helse.hendelser.Avsender.SYKMELDT
import no.nav.helse.hendelser.Periode.Companion.delvisOverlappMed
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Companion.inneholderDagerEtter
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Companion.subsumsjonsFormat
import no.nav.helse.hendelser.Søknad.TilkommenInntekt.Companion.orgnummereMedTilkomneInntekter
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.Person
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
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.InntektFraSøknad
import no.nav.helse.person.inntekt.Inntektsgrunnlag
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.SykdomshistorikkHendelse.Hendelseskilde
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.sykdomstidslinje.merge
import no.nav.helse.tournament.Dagturnering
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Prosentdel

class Søknad(
    meldingsreferanseId: UUID,
    fnr: String,
    aktørId: String,
    private val orgnummer: String,
    private val perioder: List<Søknadsperiode>,
    private val andreInntektskilder: Boolean,
    private val ikkeJobbetIDetSisteFraAnnetArbeidsforhold: Boolean,
    private val sendtTilNAVEllerArbeidsgiver: LocalDateTime,
    private val permittert: Boolean,
    private val merknaderFraSykmelding: List<Merknad>,
    sykmeldingSkrevet: LocalDateTime,
    private val opprinneligSendt: LocalDateTime?,
    private val utenlandskSykmelding: Boolean,
    private val arbeidUtenforNorge: Boolean,
    private val sendTilGosys: Boolean,
    private val yrkesskade: Boolean,
    private val egenmeldinger: List<Periode>,
    private val søknadstype: Søknadstype,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
    private val registrert: LocalDateTime,
    private val tilkomneInntekter: List<TilkommenInntekt>
) : SykdomstidslinjeHendelse(meldingsreferanseId, fnr, aktørId, orgnummer, sykmeldingSkrevet, Søknad::class, aktivitetslogg) {

    private val sykdomsperiode: Periode
    private var sykdomstidslinje: Sykdomstidslinje

    internal companion object {
        internal const val tidslinjegrense = 40L
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
    }

    override fun erRelevant(other: Periode) = other.overlapperMed(sykdomsperiode)

    override fun sykdomstidslinje(): Sykdomstidslinje {
        return sykdomstidslinje
    }

    internal fun egenmeldingsperioder(): List<Periode> {
        return egenmeldinger
    }

    override fun dokumentsporing() =
        Dokumentsporing.søknad(meldingsreferanseId())

    override fun innsendt() = sendtTilNAVEllerArbeidsgiver
    override fun registrert() = registrert
    override fun avsender() = SYKMELDT

    internal fun delvisOverlappende(other: Periode) = other.delvisOverlappMed(sykdomsperiode)

    internal fun valider(vilkårsgrunnlag: VilkårsgrunnlagElement?, subsumsjonslogg: Subsumsjonslogg): IAktivitetslogg {
        valider(subsumsjonslogg)
        validerInntektskilder(vilkårsgrunnlag)
        søknadstype.valider(this, vilkårsgrunnlag, orgnummer, sykdomstidslinje.periode())
        return this
    }

    private fun valider(subsumsjonslogg: Subsumsjonslogg): IAktivitetslogg {
        val utlandsopphold = perioder.filterIsInstance<Søknadsperiode.Utlandsopphold>().map { it.periode }
        subsumsjonslogg.logg(`§ 8-9 ledd 1`(false, utlandsopphold, this.perioder.subsumsjonsFormat()))
        perioder.forEach { it.valider(this) }
        if (permittert) varsel(RV_SØ_1)
        if (tilkomneInntekter.isNotEmpty()) varsel(RV_SV_5)
        merknaderFraSykmelding.forEach { it.valider(this) }
        val foreldedeDager = ForeldetSubsumsjonsgrunnlag(sykdomstidslinje).build()
        if (foreldedeDager.isNotEmpty()) {
            subsumsjonslogg.logg(`§ 22-13 ledd 3`(avskjæringsdato(), foreldedeDager))
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

    private fun validerInntektskilder(vilkårsgrunnlag: VilkårsgrunnlagElement?) {
        if (ikkeJobbetIDetSisteFraAnnetArbeidsforhold) varsel(RV_SØ_44)
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


    internal fun lagVedtaksperiode(person: Person, arbeidsgiver: Arbeidsgiver, subsumsjonslogg: Subsumsjonslogg): Vedtaksperiode {
        requireNotNull(sykdomstidslinje.periode()) { "ugyldig søknad: tidslinjen er tom" }
        return Vedtaksperiode(
            søknad = this,
            person = person,
            arbeidsgiver = arbeidsgiver,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = organisasjonsnummer,
            sykdomstidslinje = sykdomstidslinje,
            dokumentsporing = Dokumentsporing.søknad(meldingsreferanseId()),
            sykmeldingsperiode = sykdomsperiode,
            subsumsjonslogg = subsumsjonslogg
        )
    }

    internal fun slettSykmeldingsperioderSomDekkes(arbeidsgiveren: Sykmeldingsperioder) {
        arbeidsgiveren.fjern(sykdomsperiode)
    }

    internal fun orgnummereMedTilkomneInntekter() = tilkomneInntekter.orgnummereMedTilkomneInntekter()

    internal fun nyeInntekter(
        builder: Inntektsgrunnlag.ArbeidsgiverInntektsopplysningerOverstyringer,
        skjæringstidspunkt: LocalDate
    ) {
        tilkomneInntekter.forEach { inntekt ->
            inntekt.nyInntekt(builder, skjæringstidspunkt, meldingsreferanseId(), registrert)
        }
    }

    class Merknad(private val type: String) {
        private companion object {
            private val tilbakedateringer = setOf(
                "UGYLDIG_TILBAKEDATERING",
                "TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER",
                "UNDER_BEHANDLING",
                "DELVIS_GODKJENT"
            )
        }
        internal fun valider(aktivitetslogg: IAktivitetslogg) {
            if (type !in tilbakedateringer) return
            aktivitetslogg.varsel(RV_SØ_3)
        }
    }

    class TilkommenInntekt(
        private val fom: LocalDate,
        private val tom: LocalDate,
        private val orgnummer: String,
        private val beløp: Inntekt,
    ) {
        private fun gjelder() = fom til tom

        internal fun nyInntekt(
            builder: Inntektsgrunnlag.ArbeidsgiverInntektsopplysningerOverstyringer,
            skjæringstidspunkt: LocalDate,
            meldingsreferanseId: UUID,
            registrert: LocalDateTime
        ) {
            builder.leggTilInntekt(
                ArbeidsgiverInntektsopplysning(
                    orgnummer = orgnummer,
                    gjelder = gjelder(),
                    inntektsopplysning = InntektFraSøknad(
                        id = UUID.randomUUID(),
                        dato = skjæringstidspunkt,
                        hendelseId = meldingsreferanseId,
                        beløp = beløp,
                        tidsstempel = registrert
                    ),
                    refusjonsopplysninger = Refusjonsopplysning.Refusjonsopplysninger()
                )
            )
        }

        companion object {
            fun List<TilkommenInntekt>.orgnummereMedTilkomneInntekter() = map { it.orgnummer }
        }
    }

    class Søknadstype(private val type: String) {
        internal fun valider(
            aktivitetslogg: IAktivitetslogg,
            vilkårsgrunnlag: VilkårsgrunnlagElement?,
            orgnummer: String,
            periode: Periode?
        ) {
            if (this == Arbeidstaker) return
            if (this != Arbeidsledig) return aktivitetslogg.funksjonellFeil(`Støtter ikke søknadstypen`)
            if (vilkårsgrunnlag == null) return aktivitetslogg.funksjonellFeil(`Støtter ikke førstegangsbehandlinger for arbeidsledigsøknader`)
            if (vilkårsgrunnlag.refusjonsopplysninger(orgnummer).overlappendeEllerSenereRefusjonsopplysninger(periode).all { it.beløp == Inntekt.INGEN }) {
                return aktivitetslogg.info("Arbeidsledigsøknad lagt til grunn og vi har ikke registrert refusjon i søknadstidsrommet")
            }
            aktivitetslogg.varsel(`Arbeidsledigsøknad er lagt til grunn`)
        }
        override fun equals(other: Any?): Boolean {
            if (other !is Søknadstype) return false
            return this.type == other.type
        }
        override fun hashCode() = type.hashCode()
        companion object {
            val Arbeidstaker = Søknadstype("ARBEIDSTAKERE")
            val Arbeidsledig = Søknadstype("ARBEIDSLEDIG")
        }
    }

    sealed class Søknadsperiode(fom: LocalDate, tom: LocalDate, private val navn: String) {
        val periode = Periode(fom, tom)

        internal companion object {
            fun sykdomsperiode(liste: List<Søknadsperiode>) =
                søknadsperiode(liste.filterIsInstance<Sykdom>())

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

        class Papirsykmelding(fom: LocalDate, tom: LocalDate) : Søknadsperiode(fom, tom, "papirsykmelding") {
            override fun sykdomstidslinje(sykdomsperiode: Periode, avskjæringsdato: LocalDate, kilde: Hendelseskilde) =
                Sykdomstidslinje.problemdager(periode.start, periode.endInclusive, kilde, "Papirdager ikke støttet")

            override fun valider(søknad: Søknad) =
                søknad.funksjonellFeil(RV_SØ_22)
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

            private fun alleUtlandsdagerErFerie(søknad:Søknad):Boolean {
                val feriePerioder = søknad.perioder.filterIsInstance<Ferie>()
                return this.periode.all { utlandsdag -> feriePerioder.any { ferie -> ferie.periode.contains(utlandsdag)} }
            }
        }
    }

    private class ForeldetSubsumsjonsgrunnlag(sykdomstidslinje: Sykdomstidslinje) {
        private val foreldedeDager = sykdomstidslinje.filterIsInstance<Dag.ForeldetSykedag>().map { it.dato }

        fun build() = foreldedeDager.grupperSammenhengendePerioder()
    }
}
