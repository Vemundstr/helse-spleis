package no.nav.helse.utbetalingstidslinje

import no.nav.helse.Grunnbeløp
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Etterlevelse.Vurderingsresultat.Companion.`§8-3 ledd 1 punktum 2`
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Etterlevelse.Vurderingsresultat.Companion.`§8-33 ledd 3`
import no.nav.helse.person.IAktivitetslogg
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year
import java.time.temporal.ChronoUnit.YEARS

internal class Alder(private val fødselsdato: LocalDate) {
    internal val søttiårsdagen: LocalDate = fødselsdato.plusYears(70)
    internal val sisteVirkedagFørFylte70år: LocalDate = søttiårsdagen.sisteVirkedagFør()
    private val redusertYtelseAlder: LocalDate = fødselsdato.plusYears(67)
    private val forhøyetInntektskravAlder: LocalDate = fødselsdato.plusYears(67)

    private companion object {
        private const val ALDER_FOR_FORHØYET_FERIEPENGESATS = 59
        private const val MINSTEALDER_UTEN_FULLMAKT_FRA_VERGE = 18
        private fun LocalDate.sisteVirkedagFør(): LocalDate = this.minusDays(
            when (this.dayOfWeek) {
                DayOfWeek.SUNDAY -> 2
                DayOfWeek.MONDAY -> 3
                else -> 1
            }
        )
    }

    internal fun innenfor67årsgrense(dato: LocalDate) = dato <= redusertYtelseAlder
    internal fun innenfor70årsgrense(dato: LocalDate) = dato <= sisteVirkedagFørFylte70år
    internal fun harNådd70årsgrense(dato: LocalDate) = dato >= sisteVirkedagFørFylte70år
    internal fun mistetSykepengerett(dato: LocalDate) = dato >= søttiårsdagen

    internal fun alderPåDato(dato: LocalDate) = YEARS.between(fødselsdato, dato).toInt()

    private fun alderVedSluttenAvÅret(year: Year) = YEARS.between(Year.from(fødselsdato), year).toInt()

    internal fun minimumInntekt(dato: LocalDate) = (if (forhøyetInntektskrav(dato)) Grunnbeløp.`2G` else Grunnbeløp.halvG).beløp(dato)

    // Forhøyet inntektskrav gjelder fra dagen _etter_ 67-årsdagen - se §8-51 andre ledd der det spesifiseres _mellom_.
    internal fun forhøyetInntektskrav(dato: LocalDate) = dato > forhøyetInntektskravAlder

    internal fun begrunnelseForMinimumInntekt(skjæringstidspunkt: LocalDate) =
        if (forhøyetInntektskrav(skjæringstidspunkt)) Begrunnelse.MinimumInntektOver67 else Begrunnelse.MinimumInntekt

    internal fun begrunnelseForAlder(dagen: LocalDate): Begrunnelse {
        if (innenfor67årsgrense(dagen)) return Begrunnelse.SykepengedagerOppbrukt
        if (innenfor70årsgrense(dagen)) return Begrunnelse.SykepengedagerOppbruktOver67
        return Begrunnelse.Over70
    }

    internal fun forUngForÅSøke(søknadstidspunkt: LocalDate) = alderPåDato(søknadstidspunkt) < MINSTEALDER_UTEN_FULLMAKT_FRA_VERGE

    internal fun beregnFeriepenger(opptjeningsår: Year, beløp: Int): Double {
        val alderVedSluttenAvÅret = alderVedSluttenAvÅret(opptjeningsår)
        val prosentsats = if (alderVedSluttenAvÅret < ALDER_FOR_FORHØYET_FERIEPENGESATS) 0.102 else 0.125
        val feriepenger = beløp * prosentsats
        Aktivitetslogg().`§8-33 ledd 3`(beløp, opptjeningsår, prosentsats, alderVedSluttenAvÅret, feriepenger)
        return feriepenger
    }

    internal fun etterlevelse70år(aktivitetslogg: IAktivitetslogg, periode: Periode, avvisteDatoer: List<LocalDate>) {
        førFylte70(aktivitetslogg, periode)
        fraOgMedFylte70(aktivitetslogg, periode, avvisteDatoer)
    }

    private fun førFylte70(aktivitetslogg: IAktivitetslogg, periode: Periode) {
        if (periode.start >= søttiårsdagen) return
        aktivitetslogg.`§8-3 ledd 1 punktum 2`(
            oppfylt = true,
            syttiårsdagen = søttiårsdagen,
            vurderingFom = periode.start,
            vurderingTom = minOf(søttiårsdagen.minusDays(1), periode.endInclusive),
            tidslinjeFom = periode.start,
            tidslinjeTom = periode.endInclusive,
            avvisteDager = emptyList()
        )
    }

    private fun fraOgMedFylte70(aktivitetslogg: IAktivitetslogg, periode: Periode, avvisteDatoer: List<LocalDate>) {
        val avvisteDagerFraOgMedSøtti = avvisteDatoer.filter { it >= søttiårsdagen }
        if (avvisteDagerFraOgMedSøtti.isEmpty()) return
        aktivitetslogg.info("Utbetaling stoppet etter $søttiårsdagen, søker fylte 70 år.")
        aktivitetslogg.`§8-3 ledd 1 punktum 2`(
            oppfylt = false,
            syttiårsdagen = søttiårsdagen,
            vurderingFom = maxOf(søttiårsdagen, periode.start),
            vurderingTom = periode.endInclusive,
            tidslinjeFom = periode.start,
            tidslinjeTom = periode.endInclusive,
            avvisteDager = avvisteDagerFraOgMedSøtti
        )
    }
}
