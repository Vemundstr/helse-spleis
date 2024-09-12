package no.nav.helse.hendelser


import java.time.LocalDate
import java.util.UUID
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.Revurderingseventyr
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.sykdomstidslinje.Dag.Companion.default
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.sykdomstidslinje.SykdomshistorikkHendelse
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.merge

class Ytelser(
    meldingsreferanseId: UUID,
    aktørId: String,
    fødselsnummer: String,
    organisasjonsnummer: String,
    private val vedtaksperiodeId: String,
    private val foreldrepenger: Foreldrepenger,
    private val svangerskapspenger: Svangerskapspenger,
    private val pleiepenger: Pleiepenger,
    private val omsorgspenger: Omsorgspenger,
    private val opplæringspenger: Opplæringspenger,
    private val institusjonsopphold: Institusjonsopphold,
    private val arbeidsavklaringspenger: Arbeidsavklaringspenger,
    private val dagpenger: Dagpenger,
    aktivitetslogg: Aktivitetslogg
) : ArbeidstakerHendelse(meldingsreferanseId, fødselsnummer, aktørId, organisasjonsnummer, aktivitetslogg), SykdomshistorikkHendelse {

    private val YTELSER_SOM_KAN_OPPDATERE_HISTORIKK: List<AnnenYtelseSomKanOppdatereHistorikk> = listOf(
        foreldrepenger
    )
    private lateinit var sykdomstidslinje: Sykdomstidslinje

    companion object {
        internal val Periode.familieYtelserPeriode get() = oppdaterFom(start.minusWeeks(4))
    }

    internal fun erRelevant(other: UUID) = other.toString() == vedtaksperiodeId

    internal fun valider(periode: Periode, skjæringstidspunkt: LocalDate, maksdato: LocalDate, erForlengelse: Boolean ): Boolean {
        if (periode.start > maksdato) return true

        val periodeForOverlappsjekk = periode.start til minOf(periode.endInclusive, maksdato)
        arbeidsavklaringspenger.valider(this, skjæringstidspunkt, periodeForOverlappsjekk)
        dagpenger.valider(this, skjæringstidspunkt, periodeForOverlappsjekk)
        if (foreldrepenger.overlapper(this, periodeForOverlappsjekk, erForlengelse)) varsel(Varselkode.`Overlapper med foreldrepenger`)
        if (svangerskapspenger.overlapper(this, periodeForOverlappsjekk, erForlengelse)) varsel(Varselkode.`Overlapper med svangerskapspenger`)
        if (pleiepenger.overlapper(this, periodeForOverlappsjekk, erForlengelse)) varsel(Varselkode.`Overlapper med pleiepenger`)
        if (omsorgspenger.overlapper(this, periodeForOverlappsjekk, erForlengelse)) varsel(Varselkode.`Overlapper med omsorgspenger`)
        if (opplæringspenger.overlapper(this, periodeForOverlappsjekk, erForlengelse)) varsel(Varselkode.`Overlapper med opplæringspenger`)
        if (institusjonsopphold.overlapper(this, periodeForOverlappsjekk)) funksjonellFeil(Varselkode.`Overlapper med institusjonsopphold`)

        return !harFunksjonelleFeilEllerVerre()
    }

    internal fun oppdaterHistorikk(
        periode: Periode,
        skjæringstidspunkt: LocalDate,
        periodeRettEtter: Periode?,
        oppdaterHistorikk: () -> Unit
    ) {
        val sykdomstidslinjer = YTELSER_SOM_KAN_OPPDATERE_HISTORIKK.mapNotNull { ytelse ->
            if (!ytelse.skalOppdatereHistorikk(this, ytelse, periode, skjæringstidspunkt, periodeRettEtter)) null
            else ytelse.sykdomstidslinje(meldingsreferanseId(), registrert())
        }
        if (sykdomstidslinjer.isEmpty()) return
        this.sykdomstidslinje = sykdomstidslinjer.merge(beste = default)
        oppdaterHistorikk()
    }

    override fun dokumentsporing(): Dokumentsporing {
        return Dokumentsporing.andreYtelser(meldingsreferanseId())
    }

    override fun revurderingseventyr(skjæringstidspunkt: LocalDate, periode: Periode) =
        Revurderingseventyr.andreYtelser(this, skjæringstidspunkt, periode)

    override fun oppdaterFom(other: Periode): Periode {
        return other
    }

    override fun element(): Sykdomshistorikk.Element {
        return Sykdomshistorikk.Element.opprett(meldingsreferanseId(), sykdomstidslinje)
    }

    internal fun avgrensTil(periode: Periode): Ytelser {
        sykdomstidslinje = sykdomstidslinje.fraOgMed(periode.start).fremTilOgMed(periode.endInclusive)
        return this
    }
}

class GradertPeriode(internal val periode: Periode, internal val grad: Int)