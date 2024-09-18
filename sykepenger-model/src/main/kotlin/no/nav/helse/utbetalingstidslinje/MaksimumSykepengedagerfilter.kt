package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import no.nav.helse.Alder
import no.nav.helse.etterlevelse.Subsumsjonslogg
import no.nav.helse.etterlevelse.Subsumsjonslogg.Companion.NullObserver
import no.nav.helse.etterlevelse.UtbetalingstidslinjeBuilder.Companion.subsumsjonsformat
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_9
import no.nav.helse.utbetalingstidslinje.MaksimumSykepengedagerfilter.State.Karantene
import no.nav.helse.utbetalingstidslinje.MaksimumSykepengedagerfilter.State.Syk
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.NavDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.UkjentDag
import no.nav.helse.økonomi.Økonomi

internal class MaksimumSykepengedagerfilter(
    private val alder: Alder,
    private val arbeidsgiverRegler: ArbeidsgiverRegler,
    private val infotrygdtidslinje: Utbetalingstidslinje
): UtbetalingstidslinjerFilter, UtbetalingstidslinjeVisitor {

    private companion object {
        const val TILSTREKKELIG_OPPHOLD_I_SYKEDAGER = 26 * 7
        private const val HISTORISK_PERIODE_I_ÅR: Long = 3
    }

    private val tidligereVurderinger = mutableMapOf<LocalDate, Maksdatokontekst>()
    private var sisteVurdering = Maksdatokontekst.TomKontekst
        set(value) {
            /* lagrer gammel verdi for å kunne plukke den opp senere, ifm. maksdatovurderinger på tvers av arbeidsgivere med ulike tom */
            tidligereVurderinger.putIfAbsent(field.vurdertTilOgMed, field)
            field = value
        }

    private var state: State = State.Initiell
    private val begrunnelserForAvvisteDager = mutableMapOf<Begrunnelse, MutableSet<LocalDate>>()
    private val avvisteDager get() = begrunnelserForAvvisteDager.values.flatten().toSet()
    internal lateinit var beregnetTidslinje: Utbetalingstidslinje
    private lateinit var tidslinjegrunnlag: List<Utbetalingstidslinje>
    private var subsumsjonslogg: Subsumsjonslogg = NullObserver

    private val tidslinjegrunnlagsubsumsjon by lazy { tidslinjegrunnlag.subsumsjonsformat() }
    private val beregnetTidslinjesubsumsjon by lazy { beregnetTidslinje.subsumsjonsformat() }

    internal fun maksimumSykepenger(periode: Periode, subsumsjonslogg: Subsumsjonslogg): Maksdatosituasjon {
        val sisteVurderingForut = tidligereVurderinger.values
            .sortedBy { it.vurdertTilOgMed }
            .lastOrNull { it.vurdertTilOgMed < periode.endInclusive }

        val riktigKontekst = when {
            // vi har ikke gjort en konkret vurdering, så da strekker vi siste vurdering frem
            sisteVurdering.vurdertTilOgMed < periode.endInclusive -> sisteVurdering.copy(vurdertTilOgMed = periode.endInclusive)
            // konkret vurdering for dagen er gjort
            sisteVurdering.vurdertTilOgMed == periode.endInclusive -> sisteVurdering
            // konkret vurdering for dagen er gjort
            periode.endInclusive in tidligereVurderinger -> tidligereVurderinger.getValue(periode.endInclusive)
            // tar utgangspunkt i siste vurdering før dato, og strekker den frem
            sisteVurderingForut != null -> sisteVurderingForut.copy(vurdertTilOgMed = periode.endInclusive)
            else -> error("Finner ikke vurdering for ${periode.endInclusive}")
        }
        // todo: fjerne unødvendig mellomledd <Maksdatosituasjon>
        val maksimumSykepenger = riktigKontekst.somMaksdatosituasjon()
        maksimumSykepenger.vurderMaksdatobestemmelse(subsumsjonslogg, periode, tidslinjegrunnlagsubsumsjon, beregnetTidslinjesubsumsjon, avvisteDager)
        return maksimumSykepenger
    }

    private fun Maksdatokontekst.somMaksdatosituasjon() = Maksdatosituasjon(
        regler = arbeidsgiverRegler,
        dato = vurdertTilOgMed,
        alder = alder,
        startdatoSykepengerettighet = startdatoSykepengerettighet,
        startdatoTreårsvindu = startdatoTreårsvindu,
        betalteDager = betalteDager
    )

    private fun avvisDag(dag: LocalDate, begrunnelse: Begrunnelse) {
        begrunnelserForAvvisteDager.getOrPut(begrunnelse) {
            mutableSetOf()
        }.add(dag)
    }

    override fun filter(
        tidslinjer: List<Utbetalingstidslinje>,
        periode: Periode,
        aktivitetslogg: IAktivitetslogg,
        subsumsjonslogg: Subsumsjonslogg
    ): List<Utbetalingstidslinje> {
        this.subsumsjonslogg = subsumsjonslogg
        tidslinjegrunnlag = tidslinjer + listOf(infotrygdtidslinje)
        beregnetTidslinje = tidslinjegrunnlag.reduce(Utbetalingstidslinje::plus)
        beregnetTidslinje.accept(this)

        val avvisteTidslinjer = begrunnelserForAvvisteDager.entries.fold(tidslinjer) { result, (begrunnelse, dager) ->
            Utbetalingstidslinje.avvis(result, dager.grupperSammenhengendePerioder(), listOf(begrunnelse))
        }

        if (begrunnelserForAvvisteDager[Begrunnelse.NyVilkårsprøvingNødvendig]?.any { it in periode } == true) {
            aktivitetslogg.funksjonellFeil(RV_VV_9)
        }
        if (avvisteDager in periode)
            aktivitetslogg.info("Maks antall sykepengedager er nådd i perioden")
        else
            aktivitetslogg.info("Maksimalt antall sykedager overskrides ikke i perioden")

        return avvisteTidslinjer
    }

    private fun state(nyState: State) {
        if (this.state == nyState) return
        state.leaving(this)
        state = nyState
        state.entering(this)
    }

    override fun visit(dag: Utbetalingsdag.ForeldetDag, dato: LocalDate, økonomi: Økonomi) {}

    override fun visit(
        dag: NavDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        if (alder.mistetSykepengerett(dato)) state(State.ForGammel)
        state.betalbarDag(this, dato)
    }

    override fun visit(
        dag: Utbetalingsdag.NavHelgDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        if (alder.mistetSykepengerett(dato)) state(State.ForGammel)
        state.sykdomshelg(this, dato)
    }

    override fun visit(
        dag: Utbetalingsdag.Arbeidsdag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        state.oppholdsdag(this, dato)
    }

    override fun visit(
        dag: Utbetalingsdag.ArbeidsgiverperiodeDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        state.oppholdsdag(this, dato)
    }

    override fun visit(dag: Utbetalingsdag.ArbeidsgiverperiodedagNav, dato: LocalDate, økonomi: Økonomi) {
        state.oppholdsdag(this, dato)
    }

    override fun visit(
        dag: Utbetalingsdag.Fridag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        state.fridag(this, dato)
    }

    override fun visit(
        dag: Utbetalingsdag.AvvistDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        state.avvistDag(this, dato)
    }

    override fun visit(
        dag: UkjentDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        state.oppholdsdag(this, dato)
    }

    private fun økOppholdstelling(dato: LocalDate) {
        sisteVurdering = sisteVurdering.økOppholdstelling(dato)
    }

    private fun subsummerTilstrekkeligOppholdNådd(dagen: LocalDate, oppholdFørDagen: Int = sisteVurdering.oppholdsteller): Boolean {
        // Nok opphold? 🤔
        val harTilstrekkeligOpphold = oppholdFørDagen >= TILSTREKKELIG_OPPHOLD_I_SYKEDAGER
        subsumsjonslogg.`§ 8-12 ledd 2`(
            oppfylt = harTilstrekkeligOpphold,
            dato = dagen,
            gjenståendeSykepengedager = sisteVurdering.gjenståendeDagerUnder67År(alder, arbeidsgiverRegler),
            beregnetAntallOppholdsdager = oppholdFørDagen,
            tilstrekkeligOppholdISykedager = TILSTREKKELIG_OPPHOLD_I_SYKEDAGER,
            tidslinjegrunnlag = tidslinjegrunnlagsubsumsjon,
            beregnetTidslinje = beregnetTidslinjesubsumsjon,
        )
        return harTilstrekkeligOpphold
    }

    private fun håndterBetalbarDag(dagen: LocalDate) {
        sisteVurdering = sisteVurdering.inkrementer(dagen)
        when {
            sisteVurdering.erDagerUnder67ÅrForbrukte(alder, arbeidsgiverRegler) -> state(Karantene(Begrunnelse.SykepengedagerOppbrukt))
            sisteVurdering.erDagerOver67ÅrForbrukte(alder, arbeidsgiverRegler) -> state(Karantene(Begrunnelse.SykepengedagerOppbruktOver67))
            else -> state(Syk)
        }
    }
    private fun håndterBetalbarDagEtterFerie(dagen: LocalDate) {
        håndterBetalbarDag(dagen)
    }
    private fun håndterBetalbarDagEtterOpphold(dagen: LocalDate) {
        val oppholdFørDagen = sisteVurdering.oppholdsteller
        sisteVurdering = sisteVurdering.dekrementer(dagen, dagen.minusYears(HISTORISK_PERIODE_I_ÅR))
        subsummerTilstrekkeligOppholdNådd(dagen, oppholdFørDagen = oppholdFørDagen)
        håndterBetalbarDag(dagen)
    }

    private interface State {
        fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {}
        fun avvistDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {}
        fun oppholdsdag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {}
        fun sykdomshelg(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {}
        fun fridag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) = oppholdsdag(avgrenser, dagen)
        fun entering(avgrenser: MaksimumSykepengedagerfilter) {}
        fun leaving(avgrenser: MaksimumSykepengedagerfilter) {}

        object Initiell : State {
            override fun entering(avgrenser: MaksimumSykepengedagerfilter) {
                avgrenser.sisteVurdering = Maksdatokontekst.TomKontekst
            }

            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                /* starter en helt ny maksdatosak 😊 */
                avgrenser.sisteVurdering = Maksdatokontekst(
                    vurdertTilOgMed = dagen,
                    startdatoSykepengerettighet = dagen,
                    startdatoTreårsvindu = dagen.minusYears(HISTORISK_PERIODE_I_ÅR),
                    betalteDager = setOf(dagen),
                    oppholdsteller = 0
                )
                avgrenser.state(Syk)
            }
        }

        object Syk : State {
            override fun entering(avgrenser: MaksimumSykepengedagerfilter) {
                check(avgrenser.sisteVurdering.oppholdsteller == 0)
            }

            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.håndterBetalbarDag(dagen)
            }

            override fun oppholdsdag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.økOppholdstelling(dagen)
                avgrenser.state(Opphold)
            }

            override fun fridag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.økOppholdstelling(dagen)
                avgrenser.state(OppholdFri)
            }
        }

        object Opphold : State {

            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.håndterBetalbarDagEtterOpphold(dagen)
            }

            override fun sykdomshelg(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.økOppholdstelling(dagen)
                oppholdsdag(avgrenser, dagen)
            }

            override fun oppholdsdag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.økOppholdstelling(dagen)
                if (!avgrenser.subsummerTilstrekkeligOppholdNådd(dagen)) return
                avgrenser.state(Initiell)
            }
        }

        object OppholdFri : State {
            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.håndterBetalbarDagEtterFerie(dagen)
            }

            override fun fridag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.økOppholdstelling(dagen)
                if (!avgrenser.subsummerTilstrekkeligOppholdNådd(dagen)) return
                avgrenser.state(Initiell)
            }

            override fun oppholdsdag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.økOppholdstelling(dagen)
                if (!avgrenser.subsummerTilstrekkeligOppholdNådd(dagen)) return avgrenser.state(Opphold)
                avgrenser.state(Initiell)
            }
        }

        class Karantene(private val begrunnelse: Begrunnelse) : State {
            override fun entering(avgrenser: MaksimumSykepengedagerfilter) {
                check(avgrenser.sisteVurdering.oppholdsteller == 0)
            }

            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.avvisDag(dagen, begrunnelse)
                avgrenser.økOppholdstelling(dagen)
                vurderTilstrekkeligOppholdNådd(avgrenser)
            }

            override fun avvistDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.avvisDag(dagen, begrunnelse)
                avgrenser.økOppholdstelling(dagen)
                vurderTilstrekkeligOppholdNådd(avgrenser)
            }

            override fun sykdomshelg(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.økOppholdstelling(dagen)
                /* helg skal ikke medføre ny rettighet */
                vurderTilstrekkeligOppholdNådd(avgrenser)
            }

            override fun oppholdsdag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.økOppholdstelling(dagen)
                if (!avgrenser.subsummerTilstrekkeligOppholdNådd(dagen)) return
                avgrenser.state(Initiell)
            }

            override fun fridag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.økOppholdstelling(dagen)
                /* helg skal ikke medføre ny rettighet */
                vurderTilstrekkeligOppholdNådd(avgrenser)
            }

            private fun vurderTilstrekkeligOppholdNådd(avgrenser: MaksimumSykepengedagerfilter) {
                if (avgrenser.sisteVurdering.oppholdsteller < TILSTREKKELIG_OPPHOLD_I_SYKEDAGER) return
                avgrenser.state(KaranteneTilstrekkeligOppholdNådd)
            }
        }

        object KaranteneTilstrekkeligOppholdNådd : State {
            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avvistDag(avgrenser, dagen)
            }

            override fun avvistDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.avvisDag(dagen, Begrunnelse.NyVilkårsprøvingNødvendig)
            }

            override fun sykdomshelg(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {}

            override fun oppholdsdag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.state(Initiell)
            }

            override fun fridag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {}
        }

        object ForGammel : State {
            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                over70(avgrenser, dagen)
            }

            override fun sykdomshelg(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                over70(avgrenser, dagen)
            }

            override fun leaving(avgrenser: MaksimumSykepengedagerfilter) = throw IllegalStateException("Kan ikke gå ut fra state ForGammel")

            private fun over70(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.avvisDag(dagen, Begrunnelse.Over70)
            }
        }
    }
}
