package no.nav.helse.utbetalingslinjer

import java.time.LocalDate
import no.nav.helse.erHelg
import no.nav.helse.hendelser.til
import no.nav.helse.utbetalingslinjer.Endringskode.ENDR
import no.nav.helse.utbetalingslinjer.Endringskode.NY
import no.nav.helse.utbetalingslinjer.Endringskode.UEND
import no.nav.helse.utbetalingslinjer.Klassekode.RefusjonIkkeOpplysningspliktig

class Utbetalingslinje internal constructor(
    internal var fom: LocalDate,
    internal var tom: LocalDate,
    internal var satstype: Satstype = Satstype.Daglig,
    internal var beløp: Int?,
    internal var aktuellDagsinntekt: Int?,
    internal val grad: Int?,
    internal var refFagsystemId: String? = null,
    private var delytelseId: Int = 1,
    private var refDelytelseId: Int? = null,
    private var endringskode: Endringskode = NY,
    private var klassekode: Klassekode = RefusjonIkkeOpplysningspliktig,
    private var datoStatusFom: LocalDate? = null
) : Iterable<LocalDate> {

    internal companion object {
        internal fun stønadsdager(linjer: List<Utbetalingslinje>): Int {
            return linjer
                .filterNot { it.erOpphør() }
                .flatten()
                .distinct()
                .filterNot { it.erHelg() }
                .size
        }

        internal fun ferdigUtbetalingslinje(
            fom: LocalDate,
            tom: LocalDate,
            satstype: Satstype,
            sats: Int,
            lønn: Int?,
            grad: Int?,
            refFagsystemId: String?,
            delytelseId: Int,
            refDelytelseId: Int?,
            endringskode: Endringskode,
            klassekode: Klassekode,
            datoStatusFom: LocalDate?
        ): Utbetalingslinje = Utbetalingslinje(
            fom = fom,
            tom = tom,
            satstype = satstype,
            beløp = sats,
            aktuellDagsinntekt = lønn,
            grad = grad,
            refFagsystemId = refFagsystemId,
            delytelseId = delytelseId,
            refDelytelseId = refDelytelseId,
            endringskode = endringskode,
            klassekode = klassekode,
            datoStatusFom = datoStatusFom
        )
    }

    private val statuskode get() = datoStatusFom?.let { "OPPH" }

    internal val periode get() = fom til tom

    override operator fun iterator() = periode.iterator()

    override fun toString() = "$fom til $tom $endringskode $grad ${datoStatusFom?.let { "opphører fom $it" }}"

    internal fun accept(visitor: UtbetalingslinjeVisitor) {
        visitor.visitUtbetalingslinje(
            this,
            fom,
            tom,
            stønadsdager(),
            totalbeløp(),
            satstype,
            beløp,
            aktuellDagsinntekt,
            grad,
            delytelseId,
            refDelytelseId,
            refFagsystemId,
            endringskode,
            datoStatusFom,
            statuskode,
            klassekode
        )
    }

    internal fun kobleTil(other: Utbetalingslinje) {
        this.delytelseId = other.delytelseId + 1
        this.refDelytelseId = other.delytelseId
    }

    internal fun datoStatusFom() = datoStatusFom
    internal fun totalbeløp() = satstype.totalbeløp(beløp ?: 0, stønadsdager())
    internal fun stønadsdager() = if (!erOpphør()) filterNot(LocalDate::erHelg).size else 0

    internal fun dager() = fom
        .datesUntil(tom.plusDays(1))
        .filter { !it.erHelg() }
        .toList()

    override fun equals(other: Any?) = other is Utbetalingslinje && this.equals(other)

    private fun equals(other: Utbetalingslinje) =
        this.fom == other.fom &&
            this.tom == other.tom &&
            this.beløp == other.beløp &&
            this.grad == other.grad &&
            this.datoStatusFom == other.datoStatusFom

    internal fun kanEndreEksisterendeLinje(other: Utbetalingslinje, sisteLinjeITidligereOppdrag: Utbetalingslinje) =
        other == sisteLinjeITidligereOppdrag &&
        this.fom == other.fom &&
            this.beløp == other.beløp &&
            this.grad == other.grad &&
            this.datoStatusFom == other.datoStatusFom

    internal fun skalOpphøreOgErstatte(other: Utbetalingslinje, sisteLinjeITidligereOppdrag: Utbetalingslinje) =
        other == sisteLinjeITidligereOppdrag &&
        (this.fom > other.fom)

    override fun hashCode(): Int {
        return fom.hashCode() * 37 +
            tom.hashCode() * 17 +
            beløp.hashCode() * 41 +
            grad.hashCode() * 61 +
            endringskode.name.hashCode() * 59 +
            datoStatusFom.hashCode() * 23
    }

    internal fun markerUendret(tidligere: Utbetalingslinje) = copyWith(UEND, tidligere)

    internal fun endreEksisterendeLinje(tidligere: Utbetalingslinje) = copyWith(ENDR, tidligere)
        .also {
            this.refDelytelseId = null
            this.refFagsystemId = null
        }

    private fun copyWith(linjetype: Endringskode, tidligere: Utbetalingslinje) {
        this.refFagsystemId = tidligere.refFagsystemId
        this.delytelseId = tidligere.delytelseId
        this.refDelytelseId = tidligere.refDelytelseId
        this.klassekode = tidligere.klassekode
        this.endringskode = linjetype
        this.datoStatusFom = tidligere.datoStatusFom
    }

    internal fun erForskjell() = endringskode != UEND

    internal fun opphørslinje(datoStatusFom: LocalDate) =
        Utbetalingslinje(
            fom = fom,
            tom = tom,
            beløp = beløp,
            aktuellDagsinntekt = aktuellDagsinntekt,
            grad = grad,
            refFagsystemId = null,
            delytelseId = delytelseId,
            refDelytelseId = null,
            endringskode = ENDR,
            datoStatusFom = datoStatusFom,
            satstype = satstype,
            klassekode = klassekode
        )

    internal fun erOpphør() = datoStatusFom != null

    internal fun toHendelseMap() = mapOf<String, Any?>(
        "fom" to fom.toString(),
        "tom" to tom.toString(),
        "satstype" to "$satstype",
        "sats" to beløp,
        // TODO: Skal bort etter apper er migrert over til sats
        "dagsats" to beløp,
        "lønn" to aktuellDagsinntekt,
        "grad" to grad?.toDouble(), // backwards-compatibility mot andre systemer som forventer double: må gjennomgås
        "stønadsdager" to stønadsdager(),
        "totalbeløp" to totalbeløp(),
        "endringskode" to endringskode.toString(),
        "delytelseId" to delytelseId,
        "refDelytelseId" to refDelytelseId,
        "refFagsystemId" to refFagsystemId,
        "statuskode" to statuskode,
        "datoStatusFom" to datoStatusFom?.toString(),
        "klassekode" to klassekode.verdi
    )
}

interface UtbetalingslinjeVisitor {
    fun visitUtbetalingslinje(
        linje: Utbetalingslinje,
        fom: LocalDate,
        tom: LocalDate,
        stønadsdager: Int,
        totalbeløp: Int,
        satstype: Satstype,
        beløp: Int?,
        aktuellDagsinntekt: Int?,
        grad: Int?,
        delytelseId: Int,
        refDelytelseId: Int?,
        refFagsystemId: String?,
        endringskode: Endringskode,
        datoStatusFom: LocalDate?,
        statuskode: String?,
        klassekode: Klassekode
    ) {
    }
}