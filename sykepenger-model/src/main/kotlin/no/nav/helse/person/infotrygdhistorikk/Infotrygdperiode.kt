package no.nav.helse.person.infotrygdhistorikk

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Objects
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.IAktivitetslogg
import no.nav.helse.person.InfotrygdhistorikkVisitor
import no.nav.helse.person.Varselkode.RV_IT_5
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning.Companion.fjern
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning.Companion.harInntekterFor
import no.nav.helse.sykdomstidslinje.Dag.Companion.replace
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje

abstract class Infotrygdperiode(fom: LocalDate, tom: LocalDate) : Periode(fom, tom) {
    internal open fun sykdomstidslinje(kilde: SykdomstidslinjeHendelse.Hendelseskilde): Sykdomstidslinje = Sykdomstidslinje()
    internal open fun utbetalingstidslinje(): Utbetalingstidslinje = Utbetalingstidslinje()

    internal abstract fun accept(visitor: InfotrygdhistorikkVisitor)
    internal open fun valider(aktivitetslogg: IAktivitetslogg, periode: Periode, skjæringstidspunkt: LocalDate, nødnummer: Nødnummer) {}
    internal open fun validerOverlapp(aktivitetslogg: IAktivitetslogg, periode: Periode) {}

    internal fun historikkFor(orgnummer: String, sykdomstidslinje: Sykdomstidslinje, kilde: SykdomstidslinjeHendelse.Hendelseskilde): Sykdomstidslinje {
        if (!gjelder(orgnummer)) return sykdomstidslinje
        return sykdomstidslinje.merge(sykdomstidslinje(kilde), replace)
    }

    internal open fun gjelder(nødnummer: Nødnummer) = false
    internal open fun gjelder(orgnummer: String) = true
    internal open fun utbetalingEtter(orgnumre: List<String>, dato: LocalDate) = false

    override fun hashCode() = Objects.hash(this::class, start, endInclusive)
    override fun equals(other: Any?): Boolean {
        if (other !is Infotrygdperiode) return false
        if (this::class != other::class) return false
        return super.equals(other)
    }

    companion object {
        internal fun Iterable<Infotrygdperiode>.validerInntektForPerioder(aktivitetslogg: IAktivitetslogg, inntekter: List<Inntektsopplysning>, nødnummer: Nødnummer) {
            // Liste med første utbetalingsdag i hver periode etter:
            // filtrering av irrellevante perioder, padding av helgedager og sammenslåing av perioder som henger sammen
            val førsteUtbetalingsdager = this
                .filterNot { it.gjelder(nødnummer) }
                .filter { it is Utbetalingsperiode || it is Friperiode }
                .flatten()
                .fold(emptyList<LocalDate>()) { acc, nesteDag ->
                    if (nesteDag.dayOfWeek == DayOfWeek.FRIDAY) acc + (0..2L).map { nesteDag.plusDays(it) }
                    else acc + nesteDag
                }
                .grupperSammenhengendePerioder()
                .mapNotNull { periode ->
                    periode.firstOrNull {
                        it in this.filterIsInstance<Utbetalingsperiode>().flatten()
                    }
                }

            if (inntekter.fjern(nødnummer).harInntekterFor(førsteUtbetalingsdager)) return
            aktivitetslogg.info("Mangler inntekt for første utbetalingsdag i en av infotrygdperiodene: $førsteUtbetalingsdager")
            aktivitetslogg.funksjonellFeil(RV_IT_5)
        }

        internal fun sorter(perioder: List<Infotrygdperiode>) =
            perioder.sortedWith(compareBy( { it.start }, { it.hashCode() }))

    }
}
