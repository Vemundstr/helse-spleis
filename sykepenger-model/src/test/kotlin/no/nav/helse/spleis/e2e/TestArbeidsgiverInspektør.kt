package no.nav.helse.spleis.e2e

import no.nav.helse.etterspurteBehovFinnes
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.person.*
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.*
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse.Hendelseskilde
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Økonomi
import org.junit.jupiter.api.fail
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.reflect.KClass

internal class TestArbeidsgiverInspektør(
    person: Person,
    orgnummer: String? = null
) : ArbeidsgiverVisitor {
    internal var vedtaksperiodeTeller: Int = 0
        private set
    private var vedtaksperiodeindeks = 0
    private val tilstander = mutableMapOf<Int, TilstandType>()
    private val skjæringstidspunkter = mutableMapOf<Int, LocalDate>()
    private val maksdatoer = mutableMapOf<Int, LocalDate>()
    private val gjenståendeSykedagerer = mutableMapOf<Int, Int>()
    private val vedtaksperiodeindekser = mutableMapOf<UUID, Int>()
    private val fagsystemIder = mutableMapOf<Int, String>()
    private val vedtaksperiodeForkastet = mutableMapOf<Int, Boolean>()
    private val vilkårsgrunnlag = mutableMapOf<Int, Vilkårsgrunnlag.Grunnlagsdata?>()
    internal val personLogg: Aktivitetslogg
    internal lateinit var arbeidsgiver: Arbeidsgiver
    internal lateinit var inntektshistorikk: Inntektshistorikk
    internal lateinit var sykdomshistorikk: Sykdomshistorikk
    internal lateinit var sykdomstidslinje: Sykdomstidslinje
    internal var låstePerioder = emptyList<Periode>()
    internal val dagtelling = mutableMapOf<KClass<out Dag>, Int>()
    internal val inntekter = mutableListOf<Inntektshistorikk.Inntektsendring>()
    internal lateinit var utbetalinger: List<Utbetaling>
    internal val arbeidsgiverOppdrag = mutableListOf<Oppdrag>()
    internal val totalBeløp = mutableListOf<Int>()
    internal val nettoBeløp = mutableListOf<Int>()
    private val utbetalingstidslinjer = mutableMapOf<Int, Utbetalingstidslinje>()
    private val vedtaksperioder = mutableMapOf<Int, Vedtaksperiode>()
    private var forkastetPeriode = false
    private var inVedtaksperiode = false
    private val forlengelserFraInfotrygd = mutableMapOf<Int, ForlengelseFraInfotrygd>()
    private val hendelseIder = mutableMapOf<Int, List<UUID>>()

    init {
        HentAktivitetslogg(person, orgnummer).also { results ->
            personLogg = results.aktivitetslogg
            results.arbeidsgiver.accept(this)
        }
    }

    private class HentAktivitetslogg(person: Person, private val valgfriOrgnummer: String?) : PersonVisitor {
        lateinit var aktivitetslogg: Aktivitetslogg
        lateinit var arbeidsgiver: Arbeidsgiver

        init {
            person.accept(this)
        }

        override fun visitPersonAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
            this.aktivitetslogg = aktivitetslogg
        }

        override fun preVisitArbeidsgiver(arbeidsgiver: Arbeidsgiver, id: UUID, organisasjonsnummer: String) {
            if (organisasjonsnummer == valgfriOrgnummer) this.arbeidsgiver = arbeidsgiver
            if (this::arbeidsgiver.isInitialized) return
            this.arbeidsgiver = arbeidsgiver
        }
    }

    override fun preVisitArbeidsgiver(
        arbeidsgiver: Arbeidsgiver,
        id: UUID,
        organisasjonsnummer: String
    ) {
        this.arbeidsgiver = arbeidsgiver
    }

    override fun preVisitForkastedePerioder(vedtaksperioder: Map<Vedtaksperiode, ForkastetÅrsak>) {
        forkastetPeriode = true
    }

    override fun postVisitForkastedePerioder(vedtaksperioder: Map<Vedtaksperiode, ForkastetÅrsak>) {
        forkastetPeriode = false
    }

    override fun preVisitVedtaksperiode(
        vedtaksperiode: Vedtaksperiode,
        id: UUID,
        arbeidsgiverNettoBeløp: Int,
        personNettoBeløp: Int,
        periode: Periode,
        opprinneligPeriode: Periode,
        hendelseIder: List<UUID>
    ) {
        inVedtaksperiode = true
        vedtaksperiodeTeller += 1
        vedtaksperiodeindekser[id] = vedtaksperiodeindeks
        vedtaksperiodeForkastet[vedtaksperiodeindeks] = forkastetPeriode
        vedtaksperioder[vedtaksperiodeindeks] = vedtaksperiode
        this.hendelseIder[vedtaksperiodeindeks] = hendelseIder
    }

    override fun visitArbeidsgiverFagsystemId(fagsystemId: String?) {
        if (fagsystemId == null) return
        fagsystemIder[vedtaksperiodeindeks] = fagsystemId
    }

    override fun preVisit(tidslinje: Utbetalingstidslinje) {
        if (inVedtaksperiode) utbetalingstidslinjer[vedtaksperiodeindeks] = tidslinje
    }

    override fun visitForlengelseFraInfotrygd(forlengelseFraInfotrygd: ForlengelseFraInfotrygd) {
        forlengelserFraInfotrygd[vedtaksperiodeindeks] = forlengelseFraInfotrygd
    }

    override fun postVisitVedtaksperiode(
        vedtaksperiode: Vedtaksperiode,
        id: UUID,
        arbeidsgiverNettoBeløp: Int,
        personNettoBeløp: Int,
        periode: Periode,
        opprinneligPeriode: Periode
    ) {
        vedtaksperiodeindeks += 1
        inVedtaksperiode = false
    }

    override fun preVisitUtbetalinger(utbetalinger: List<Utbetaling>) {
        this.utbetalinger = utbetalinger
    }

    override fun preVisitArbeidsgiverOppdrag(oppdrag: Oppdrag) {
        arbeidsgiverOppdrag.add(oppdrag)
    }

    override fun preVisitOppdrag(
        oppdrag: Oppdrag,
        totalBeløp: Int,
        nettoBeløp: Int,
        tidsstempel: LocalDateTime
    ) {
        if (oppdrag != arbeidsgiverOppdrag.last()) return
        this.totalBeløp.add(totalBeløp)
        this.nettoBeløp.add(nettoBeløp)
    }

    override fun visitSkjæringstidspunkt(skjæringstidspunkt: LocalDate) {
        skjæringstidspunkter[vedtaksperiodeindeks] = skjæringstidspunkt
    }

    override fun visitMaksdato(maksdato: LocalDate) {
        maksdatoer[vedtaksperiodeindeks] = maksdato
    }

    override fun visitGjenståendeSykedager(gjenståendeSykedager: Int?) {
        if (gjenståendeSykedager == null) return
        gjenståendeSykedagerer[vedtaksperiodeindeks] = gjenståendeSykedager
    }

    override fun preVisitInntekthistorikk(inntektshistorikk: Inntektshistorikk) {
        this.inntektshistorikk = inntektshistorikk
    }

    override fun visitInntekt(inntektsendring: Inntektshistorikk.Inntektsendring, id: UUID) {
        inntekter.add(inntektsendring)
    }

    override fun preVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) {
        if (inVedtaksperiode) return
        this.sykdomshistorikk = sykdomshistorikk
        if (!sykdomshistorikk.isEmpty()) {
            sykdomstidslinje = sykdomshistorikk.sykdomstidslinje()
            this.sykdomshistorikk.sykdomstidslinje().accept(Dagteller())
        }
        lagreLås(sykdomshistorikk)
    }

    private inner class LåsInspektør : SykdomstidslinjeVisitor {
        override fun preVisitSykdomstidslinje(tidslinje: Sykdomstidslinje, låstePerioder: List<Periode>) {
            this@TestArbeidsgiverInspektør.låstePerioder = låstePerioder
        }
    }

    private fun lagreLås(sykdomshistorikk: Sykdomshistorikk) {
        if (!sykdomshistorikk.isEmpty()) sykdomshistorikk.sykdomstidslinje().accept(LåsInspektør())
    }

    override fun visitTilstand(tilstand: Vedtaksperiode.Vedtaksperiodetilstand) {
        tilstander[vedtaksperiodeindeks] = tilstand.type
    }

    override fun visitDataForVilkårsvurdering(dataForVilkårsvurdering: Vilkårsgrunnlag.Grunnlagsdata?) {
        vilkårsgrunnlag[vedtaksperiodeindeks] = dataForVilkårsvurdering
    }

    private inner class Dagteller : SykdomstidslinjeVisitor {
        override fun visitDag(dag: UkjentDag, dato: LocalDate, kilde: Hendelseskilde) =
            inkrementer(dag)
        override fun visitDag(dag: Arbeidsdag, dato: LocalDate, kilde: Hendelseskilde) = inkrementer(dag)
        override fun visitDag(
            dag: Arbeidsgiverdag,
            dato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) = inkrementer(dag)
        override fun visitDag(dag: Feriedag, dato: LocalDate, kilde: Hendelseskilde) = inkrementer(dag)

        override fun visitDag(dag: FriskHelgedag, dato: LocalDate, kilde: Hendelseskilde) = inkrementer(dag)
        override fun visitDag(
            dag: ArbeidsgiverHelgedag,
            dato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) = inkrementer(dag)
        override fun visitDag(
            dag: Sykedag,
            dato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) = inkrementer(dag)

        override fun visitDag(
            dag: ForeldetSykedag,
            dato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) = inkrementer(dag)

        override fun visitDag(
            dag: SykHelgedag,
            dato: LocalDate,
            økonomi: Økonomi,
            kilde: Hendelseskilde
        ) = inkrementer(dag)

        override fun visitDag(dag: Permisjonsdag, dato: LocalDate, kilde: Hendelseskilde) = inkrementer(dag)

        override fun visitDag(dag: Studiedag, dato: LocalDate, kilde: Hendelseskilde) = inkrementer(dag)
        override fun visitDag(dag: Utenlandsdag, dato: LocalDate, kilde: Hendelseskilde) = inkrementer(dag)
        override fun visitDag(dag: ProblemDag, dato: LocalDate, kilde: Hendelseskilde, melding: String) =
            inkrementer(dag)
        private fun inkrementer(klasse: Dag) {
            dagtelling.compute(klasse::class) { _, value -> 1 + (value ?: 0) }
        }
    }

    private fun <V> UUID.finn(hva: Map<Int, V>) = hva.getValue(this.indeks)
    private val UUID.indeks get() = vedtaksperiodeindekser[this] ?: fail { "Vedtaksperiode $this finnes ikke" }

    internal fun periodeErForkastet(id: UUID) = id.finn(vedtaksperiodeForkastet)

    internal fun periodeErIkkeForkastet(id: UUID) = !periodeErForkastet(id)

    internal fun etterspurteBehov(vedtaksperiodeId: UUID, behovtype: Aktivitetslogg.Aktivitet.Behov.Behovtype) =
        personLogg.etterspurteBehovFinnes(vedtaksperiodeId, behovtype)

    internal fun sisteBehov(id: UUID) =
        personLogg.behov().last { it.kontekst()["vedtaksperiodeId"] == id.toString() }

    internal fun sisteBehov(type: Aktivitetslogg.Aktivitet.Behov.Behovtype) =
        personLogg.behov().last { it.type == type }

    internal fun maksdato(id: UUID) = id.finn(maksdatoer)

    internal fun gjenståendeSykedager(id: UUID) = id.finn(gjenståendeSykedagerer)

    internal fun forlengelseFraInfotrygd(id: UUID) = id.finn(forlengelserFraInfotrygd)

    internal fun vilkårsgrunnlag(id: UUID) = id.finn(vilkårsgrunnlag)

    internal fun utbetalingslinjer(indeks: Int) = arbeidsgiverOppdrag[indeks]

    internal fun sisteTilstand(id: UUID) = id.finn(tilstander)

    internal fun skjæringstidspunkt(id: UUID) = id.finn(skjæringstidspunkter)

    internal fun utbetalingstidslinjer(id: UUID) = id.finn(utbetalingstidslinjer)

    internal fun vedtaksperioder(id: UUID) = id.finn(vedtaksperioder)

    internal fun hendelseIder(id: UUID) = id.finn(hendelseIder)

    internal fun fagsystemId(id: UUID) = id.finn(fagsystemIder)
}

