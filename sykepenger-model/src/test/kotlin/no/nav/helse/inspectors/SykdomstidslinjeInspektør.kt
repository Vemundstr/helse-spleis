package no.nav.helse.inspectors

import java.time.LocalDate
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.SykdomstidslinjeVisitor
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.Arbeidsdag
import no.nav.helse.sykdomstidslinje.Dag.ArbeidsgiverHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Arbeidsgiverdag
import no.nav.helse.sykdomstidslinje.Dag.Feriedag
import no.nav.helse.sykdomstidslinje.Dag.ForeldetSykedag
import no.nav.helse.sykdomstidslinje.Dag.FriskHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Permisjonsdag
import no.nav.helse.sykdomstidslinje.Dag.ProblemDag
import no.nav.helse.sykdomstidslinje.Dag.SykHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.sykdomstidslinje.Dag.UkjentDag
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomshistorikkHendelse.Hendelseskilde
import no.nav.helse.økonomi.Økonomi
import kotlin.reflect.KClass

internal val Sykdomstidslinje.inspektør get() = SykdomstidslinjeInspektør(this)

internal class SykdomstidslinjeInspektør(tidslinje: Sykdomstidslinje) : SykdomstidslinjeVisitor {
    internal val dager = mutableMapOf<LocalDate, Dag>()
    internal val kilder = mutableMapOf<LocalDate, Hendelseskilde>()
    internal val grader = mutableMapOf<LocalDate, Int>()
    internal val problemdagmeldinger = mutableMapOf<LocalDate, String>()
    internal val låstePerioder = mutableListOf<Periode>()
    internal val dagteller = mutableMapOf<KClass<out Dag>, Int>()
    internal val førsteIkkeUkjenteDag get() = dager.filterNot { (_, b) -> b is UkjentDag }.keys.minOrNull()

    init {
        tidslinje.accept(this)
    }

    internal operator fun get(dato: LocalDate) = dager[dato]
        ?: throw IllegalArgumentException("No dag for $dato")

    internal val size get() = dager.size

    private fun set(dag: Dag, dato: LocalDate, kilde: Hendelseskilde) {
        dager[dato] = dag
        kilder[dato] = kilde
        dagteller.compute(dag::class) { _, value -> 1 + (value ?: 0) }
    }

    private fun set(dag: Dag, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
        this.grader[dato] = økonomi.inspektør.grad.toDouble().toInt()
        set(dag, dato, kilde)
    }

    private fun set(dag: Dag, dato: LocalDate, kilde: Hendelseskilde, melding: String) {
        problemdagmeldinger[dato] = melding
        set(dag, dato, kilde)
    }

    override fun preVisitSykdomstidslinje(tidslinje: Sykdomstidslinje, låstePerioder: List<Periode>) {
        this.låstePerioder.addAll(låstePerioder)
    }

    override fun visitDag(dag: Dag.SykedagNav, dato: LocalDate, økonomi: Økonomi, kilde: Hendelseskilde) {
        set(dag, dato, kilde)
    }

    override fun visitDag(dag: UkjentDag, dato: LocalDate, kilde: Hendelseskilde) =
        set(dag, dato, kilde)

    override fun visitDag(dag: Arbeidsdag, dato: LocalDate, kilde: Hendelseskilde) =
        set(dag, dato, kilde)

    override fun visitDag(
        dag: Arbeidsgiverdag,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: Hendelseskilde
    ) =
        set(dag, dato, økonomi, kilde)

    override fun visitDag(dag: Feriedag, dato: LocalDate, kilde: Hendelseskilde) =
        set(dag, dato, kilde)

    override fun visitDag(dag: FriskHelgedag, dato: LocalDate, kilde: Hendelseskilde) =
        set(dag, dato, kilde)

    override fun visitDag(
        dag: ArbeidsgiverHelgedag,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: Hendelseskilde
    ) =
        set(dag, dato, økonomi, kilde)

    override fun visitDag(
        dag: Sykedag,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: Hendelseskilde
    ) =
        set(dag, dato, økonomi, kilde)

    override fun visitDag(
        dag: ForeldetSykedag,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: Hendelseskilde
    ) =
        set(dag, dato, økonomi, kilde)

    override fun visitDag(
        dag: SykHelgedag,
        dato: LocalDate,
        økonomi: Økonomi,
        kilde: Hendelseskilde
    ) =
        set(dag, dato, økonomi, kilde)

    override fun visitDag(dag: Permisjonsdag, dato: LocalDate, kilde: Hendelseskilde) =
        set(dag, dato, kilde)

    override fun visitDag(
        dag: ProblemDag,
        dato: LocalDate,
        kilde: Hendelseskilde,
        other: Hendelseskilde?,
        melding: String
    ) =
        set(dag, dato, kilde, melding)
}
