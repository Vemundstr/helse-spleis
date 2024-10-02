package no.nav.helse.person.beløp

import java.time.LocalDate
import java.util.SortedMap
import no.nav.helse.dto.BeløpstidslinjeDto
import no.nav.helse.dto.serialisering.UtbetalingstidslinjeUtDto
import no.nav.helse.forrigeDag
import no.nav.helse.hendelser.Avsender
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.nesteDag
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.daglig

data class Beløpstidslinje private constructor(private val dager: SortedMap<LocalDate, Beløpsdag>) : Iterable<Dag> {
    private constructor(dager: Map<LocalDate, Beløpsdag>): this(dager.toSortedMap())

    internal constructor(dager: List<Beløpsdag> = emptyList()): this(dager.associateBy { it.dato }.toSortedMap().also {
        require(dager.size == it.size) { "Forsøkte å opprette en beløpstidslinje med duplikate datoer. Det blir for rart for meg." }
    })

    internal constructor(vararg dager: Beløpsdag):this(dager.toList())

    private val periode = if (dager.isEmpty()) null else dager.firstKey() til dager.lastKey()

    internal operator fun get(dato: LocalDate): Dag = dager[dato] ?: UkjentDag

    override operator fun iterator(): Iterator<Dag> {
        if (periode == null) return emptyList<Dag>().iterator()
        return object : Iterator<Dag> {
            private val periodeIterator = periode.iterator()
            override fun hasNext() = periodeIterator.hasNext()
            override fun next() = this@Beløpstidslinje[periodeIterator.next()]
        }
    }

    internal operator fun plus(other: Beløpstidslinje): Beløpstidslinje {
        val results = this.dager.toMutableMap()
        other.dager.forEach { (key, dag) ->
            results.merge(key, dag, nyesteTidsstempel)
        }
        return Beløpstidslinje(results)
    }
    internal operator fun minus(datoer: Iterable<LocalDate>) = Beløpstidslinje(this.dager.filterKeys { it !in datoer })
    internal operator fun minus(dato: LocalDate) = Beløpstidslinje(this.dager.filterKeys { it != dato })

    internal fun subset(periode: Periode): Beløpstidslinje {
        if (this.periode == null || !this.periode.overlapperMed(periode)) return Beløpstidslinje()
        return Beløpstidslinje(dager.subMap(periode.start, periode.endInclusive.nesteDag))
    }

    private fun snute(snute: LocalDate): Beløpstidslinje {
        val førsteBeløpsdag = periode?.start?.let { dager.getValue(it) } ?: return Beløpstidslinje()
        if (snute >= førsteBeløpsdag.dato) return Beløpstidslinje()
        return Beløpstidslinje((snute til førsteBeløpsdag.dato.forrigeDag).map { Beløpsdag(it, førsteBeløpsdag.beløp, førsteBeløpsdag.kilde) })
    }

    private fun hale(hale: LocalDate): Beløpstidslinje {
        val sisteBeløpsdag = periode?.endInclusive?.let { dager.getValue(it) } ?: return Beløpstidslinje()
        if (hale <= sisteBeløpsdag.dato) return Beløpstidslinje()
        return Beløpstidslinje((sisteBeløpsdag.dato.nesteDag til hale).map { Beløpsdag(it, sisteBeløpsdag.beløp, sisteBeløpsdag.kilde) })
    }

    internal fun strekk(periode: Periode) = snute(periode.start) + this + hale(periode.endInclusive)

    internal fun dto() = BeløpstidslinjeDto(dager.map { (_, dag) ->
        BeløpstidslinjeDto.BeløpstidslinjedagDto(
            dato = dag.dato,
            dagligBeløp = dag.beløp.daglig,
            kilde = BeløpstidslinjeDto.BeløpstidslinjedagKildeDto(
                meldingsreferanseId = dag.kilde.meldingsreferanseId,
                avsender = dag.kilde.avsender.dto(),
                tidsstempel = dag.kilde.tidsstempel
            )
        )
    })

    internal companion object {
        private val nyesteTidsstempel = { a: Beløpsdag, b: Beløpsdag ->
            if (a.kilde.tidsstempel > b.kilde.tidsstempel) a else b
        }
        internal fun fra(periode: Periode, beløp: Inntekt, kilde: Kilde) = Beløpstidslinje(periode.map { Beløpsdag(it, beløp, kilde) })
        internal fun gjenopprett(dto: BeløpstidslinjeDto) = Beløpstidslinje(dto.dager.map {
            Beløpsdag(
                dato = it.dato,
                beløp = it.dagligBeløp.daglig,
                kilde = Kilde(
                    meldingsreferanseId = it.kilde.meldingsreferanseId,
                    avsender = Avsender.gjenopprett(it.kilde.avsender),
                    tidsstempel = it.kilde.tidsstempel
                )
            )
        })
    }
}

sealed interface Dag {
    val dato: LocalDate
    val beløp: Inntekt
    val kilde: Kilde
}

data class Beløpsdag(
    override val dato: LocalDate,
    override val beløp: Inntekt,
    override val kilde: Kilde
): Dag

data object UkjentDag : Dag {
    override val dato get() = error("En ukjent dag har ikke en dato")
    override val beløp get() = error("En ukjent dag har ikke et beløp")
    override val kilde get() = error("En ukjent dag har ikke et kilde")
}
