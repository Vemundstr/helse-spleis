package no.nav.helse.serde.api

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.person.*
import no.nav.helse.serde.mapping.JsonDagType
import no.nav.helse.serde.reflection.ArbeidsgiverReflect
import no.nav.helse.serde.reflection.PersonReflect
import no.nav.helse.serde.reflection.ReflectInstance.Companion.get
import no.nav.helse.serde.reflection.VedtaksperiodeReflect
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.dag.*
import no.nav.helse.utbetalingstidslinje.Utbetalingslinje
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import java.time.LocalDateTime
import java.util.*


fun serializePersonForSpeil(person: Person): Pair<ObjectNode, Set<UUID>> {
    val hendelseReferanser = mutableSetOf<UUID>()
    val jsonBuilder = SpeilBuilder(hendelseReferanser)
    person.accept(jsonBuilder)
    return jsonBuilder.toJson() to hendelseReferanser
}

internal class SpeilBuilder(private val hendelser: MutableSet<UUID> = mutableSetOf()) : PersonVisitor {

    private val stack: Stack<JsonState> = Stack()
    private val rootState = Root()
    private val currentState: JsonState get() = stack.peek()

    init {
        stack.push(rootState)
    }

    internal fun toJson() = rootState.toJson()

    override fun toString() = rootState.toJson().toPrettyString()

    private fun pushState(state: JsonState) {
        currentState.leaving()
        stack.push(state)
        currentState.entering()
    }

    private fun popState() {
        currentState.leaving()
        stack.pop()
        currentState.entering()
    }

    override fun preVisitPerson(
        person: Person,
        aktørId: String,
        fødselsnummer: String
    ) = currentState.preVisitPerson(person, aktørId, fødselsnummer)

    override fun postVisitPerson(
        person: Person,
        aktørId: String,
        fødselsnummer: String
    ) = currentState.postVisitPerson(person, aktørId, fødselsnummer)

    override fun preVisitArbeidsgiver(
        arbeidsgiver: Arbeidsgiver,
        id: UUID,
        organisasjonsnummer: String
    ) =
        currentState.preVisitArbeidsgiver(arbeidsgiver, id, organisasjonsnummer)

    override fun postVisitArbeidsgiver(
        arbeidsgiver: Arbeidsgiver,
        id: UUID,
        organisasjonsnummer: String
    ) = currentState.postVisitArbeidsgiver(arbeidsgiver, id, organisasjonsnummer)

    override fun preVisitArbeidsgivere() = currentState.preVisitArbeidsgivere()
    override fun postVisitArbeidsgivere() = currentState.postVisitArbeidsgivere()
    override fun preVisitInntekthistorikk(inntekthistorikk: Inntekthistorikk) =
        currentState.preVisitInntekthistorikk(inntekthistorikk)

    override fun postVisitInntekthistorikk(inntekthistorikk: Inntekthistorikk) {
        currentState.postVisitInntekthistorikk(inntekthistorikk)
    }

    override fun visitInntekt(inntekt: Inntekthistorikk.Inntekt) {
        hendelser.add(inntekt.hendelseId)
        currentState.visitInntekt(inntekt)
    }

    override fun preVisitTidslinjer(tidslinjer: MutableList<Utbetalingstidslinje>) =
        currentState.preVisitTidslinjer(tidslinjer)

    override fun preVisitUtbetalingstidslinje() =
        currentState.preVisitUtbetalingstidslinje()

    override fun visitArbeidsdag(dag: Utbetalingstidslinje.Utbetalingsdag.Arbeidsdag) =
        currentState.visitArbeidsdag(dag)

    override fun visitArbeidsgiverperiodeDag(dag: Utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag) =
        currentState.visitArbeidsgiverperiodeDag(dag)

    override fun visitNavDag(dag: Utbetalingstidslinje.Utbetalingsdag.NavDag) =
        currentState.visitNavDag(dag)

    override fun visitNavHelgDag(dag: Utbetalingstidslinje.Utbetalingsdag.NavHelgDag) =
        currentState.visitNavHelgDag(dag)

    override fun visitFridag(dag: Utbetalingstidslinje.Utbetalingsdag.Fridag) =
        currentState.visitFridag(dag)

    override fun visitAvvistDag(dag: Utbetalingstidslinje.Utbetalingsdag.AvvistDag) =
        currentState.visitAvvistDag(dag)

    override fun visitUkjentDag(dag: Utbetalingstidslinje.Utbetalingsdag.UkjentDag) =
        currentState.visitUkjentDag(dag)

    override fun visitForeldetDag(dag: Utbetalingstidslinje.Utbetalingsdag.ForeldetDag) =
        currentState.visitForeldetDag(dag)

    override fun postVisitUtbetalingstidslinje() =
        currentState.postVisitUtbetalingstidslinje()

    override fun preVisitPerioder() = currentState.preVisitPerioder()
    override fun preVisitVedtaksperiode(vedtaksperiode: Vedtaksperiode, id: UUID) {
        currentState.preVisitVedtaksperiode(vedtaksperiode, id)
    }

    override fun preVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) =
        currentState.preVisitSykdomshistorikk(sykdomshistorikk)

    override fun postVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) {
        currentState.postVisitSykdomshistorikk(sykdomshistorikk)
    }

    override fun preVisitSykdomshistorikkElement(
        element: Sykdomshistorikk.Element,
        id: UUID,
        tidsstempel: LocalDateTime
    ) {
        hendelser.add(id)
        currentState.preVisitSykdomshistorikkElement(element, id, tidsstempel)
    }

    override fun preVisitHendelseSykdomstidslinje(tidslinje: Sykdomstidslinje) =
        currentState.preVisitHendelseSykdomstidslinje(tidslinje)

    override fun postVisitHendelseSykdomstidslinje(tidslinje: Sykdomstidslinje) =
        currentState.postVisitHendelseSykdomstidslinje(tidslinje)

    override fun preVisitBeregnetSykdomstidslinje(tidslinje: Sykdomstidslinje) =
        currentState.preVisitBeregnetSykdomstidslinje(tidslinje)

    override fun postVisitBeregnetSykdomstidslinje(tidslinje: Sykdomstidslinje) =
        currentState.postVisitBeregnetSykdomstidslinje(tidslinje)

    override fun preVisitSykdomstidslinje(tidslinje: Sykdomstidslinje) =
        currentState.preVisitSykdomstidslinje(tidslinje)

    override fun postVisitSykdomstidslinje(tidslinje: Sykdomstidslinje) =
        currentState.postVisitSykdomstidslinje(tidslinje)

    override fun postVisitSykdomshistorikkElement(
        element: Sykdomshistorikk.Element,
        id: UUID,
        tidsstempel: LocalDateTime
    ) =
        currentState.postVisitSykdomshistorikkElement(element, id, tidsstempel)

    override fun postVisitVedtaksperiode(vedtaksperiode: Vedtaksperiode, id: UUID) =
        currentState.postVisitVedtaksperiode(vedtaksperiode, id)

    override fun visitArbeidsdag(arbeidsdag: Arbeidsdag.Inntektsmelding) = currentState.visitArbeidsdag(arbeidsdag)
    override fun visitArbeidsdag(arbeidsdag: Arbeidsdag.Søknad) = currentState.visitArbeidsdag(arbeidsdag)
    override fun visitEgenmeldingsdag(egenmeldingsdag: Egenmeldingsdag.Inntektsmelding) =
        currentState.visitEgenmeldingsdag(egenmeldingsdag)

    override fun visitEgenmeldingsdag(egenmeldingsdag: Egenmeldingsdag.Søknad) =
        currentState.visitEgenmeldingsdag(egenmeldingsdag)

    override fun visitFeriedag(feriedag: Feriedag.Inntektsmelding) = currentState.visitFeriedag(feriedag)
    override fun visitFeriedag(feriedag: Feriedag.Søknad) = currentState.visitFeriedag(feriedag)
    override fun visitImplisittDag(implisittDag: ImplisittDag) = currentState.visitImplisittDag(implisittDag)
    override fun visitPermisjonsdag(permisjonsdag: Permisjonsdag.Søknad) =
        currentState.visitPermisjonsdag(permisjonsdag)

    override fun visitPermisjonsdag(permisjonsdag: Permisjonsdag.Aareg) = currentState.visitPermisjonsdag(permisjonsdag)
    override fun visitStudiedag(studiedag: Studiedag) = currentState.visitStudiedag(studiedag)
    override fun visitSykHelgedag(sykHelgedag: SykHelgedag.Søknad) = currentState.visitSykHelgedag(sykHelgedag)
    override fun visitSykHelgedag(sykHelgedag: SykHelgedag.Sykmelding) = currentState.visitSykHelgedag(sykHelgedag)
    override fun visitSykedag(sykedag: Sykedag.Sykmelding) = currentState.visitSykedag(sykedag)
    override fun visitSykedag(sykedag: Sykedag.Søknad) = currentState.visitSykedag(sykedag)
    override fun visitUbestemt(ubestemtdag: Ubestemtdag) = currentState.visitUbestemt(ubestemtdag)
    override fun visitUtenlandsdag(utenlandsdag: Utenlandsdag) = currentState.visitUtenlandsdag(utenlandsdag)
    override fun visitTilstand(tilstand: Vedtaksperiode.Vedtaksperiodetilstand) =
        currentState.visitTilstand(tilstand)

    override fun preVisitUtbetalingslinjer(linjer: List<Utbetalingslinje>) =
        currentState.preVisitUtbetalingslinjer(linjer)

    override fun visitUtbetalingslinje(utbetalingslinje: Utbetalingslinje) =
        currentState.visitUtbetalingslinje(utbetalingslinje)

    override fun postVisitUtbetalingslinjer(linjer: List<Utbetalingslinje>) =
        currentState.postVisitUtbetalingslinjer(linjer)

    private interface JsonState : PersonVisitor {
        fun entering() {}
        fun leaving() {}
    }

    private inner class Root : JsonState {
        private val personMap = mutableMapOf<String, Any?>()

        override fun preVisitPerson(
            person: Person,
            aktørId: String,
            fødselsnummer: String
        ) {
            pushState(PersonState(person, personMap))
        }

        override fun toString() = personMap.toString()

        fun toJson() = jacksonObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(JavaTimeModule())
            .valueToTree<ObjectNode>(personMap)
    }

    private inner class PersonState(person: Person, private val personMap: MutableMap<String, Any?>) : JsonState {
        init {
            personMap.putAll(PersonReflect(person).toSpeilMap())
        }

        private val arbeidsgivere = mutableListOf<MutableMap<String, Any?>>()

        override fun preVisitArbeidsgivere() {
            personMap["arbeidsgivere"] = arbeidsgivere
        }

        override fun preVisitArbeidsgiver(
            arbeidsgiver: Arbeidsgiver,
            id: UUID,
            organisasjonsnummer: String
        ) {
            val arbeidsgiverMap = mutableMapOf<String, Any?>()
            arbeidsgivere.add(arbeidsgiverMap)
            pushState(ArbeidsgiverState(arbeidsgiver, arbeidsgiverMap))
        }

        override fun postVisitPerson(
            person: Person,
            aktørId: String,
            fødselsnummer: String
        ) {
            popState()
        }
    }

    private inner class ArbeidsgiverState(
        private val arbeidsgiver: Arbeidsgiver,
        private val arbeidsgiverMap: MutableMap<String, Any?>
    ) :
        JsonState {
        init {
            arbeidsgiverMap.putAll(ArbeidsgiverReflect(arbeidsgiver).toSpeilMap())
        }

        private val vedtaksperioder = mutableListOf<VedtaksperiodeDTO>()
        var vedtaksperiodeMap = mutableMapOf<String, Any?>()

        override fun preVisitPerioder() {
            arbeidsgiverMap["vedtaksperioder"] = vedtaksperioder
        }

        override fun preVisitVedtaksperiode(vedtaksperiode: Vedtaksperiode, id: UUID) {
            pushState(VedtaksperiodeState(vedtaksperiode, arbeidsgiver, vedtaksperiodeMap, vedtaksperioder))
        }

        override fun postVisitArbeidsgiver(
            arbeidsgiver: Arbeidsgiver,
            id: UUID,
            organisasjonsnummer: String
        ) {
            popState()
        }
    }


    private inner class VedtaksperiodeState(
        vedtaksperiode: Vedtaksperiode,
        arbeidsgiver: Arbeidsgiver,
        private val vedtaksperiodeMap: MutableMap<String, Any?>,
        private val vedtaksperioder: MutableList<VedtaksperiodeDTO>
    ) : JsonState {
        private val hendelser = mutableSetOf<UUID>()
        private val beregnetSykdomstidslinje = mutableListOf<SykdomstidslinjedagDTO>()
        private var totalbeløpArbeidstaker = 0.0
        private val dataForVilkårsvurdering = vedtaksperiode
            .get<Vedtaksperiode, Vilkårsgrunnlag.Grunnlagsdata?>("dataForVilkårsvurdering")
            ?.let { mapDataForVilkårsvurdering(it) }

        init {
            vedtaksperiodeMap.putAll(VedtaksperiodeReflect(vedtaksperiode).toSpeilMap(arbeidsgiver))
            vedtaksperiodeMap["sykdomstidslinje"] = beregnetSykdomstidslinje
            vedtaksperiodeMap["hendelser"] = hendelser
            vedtaksperiodeMap["dataForVilkårsvurdering"] = dataForVilkårsvurdering
        }

        override fun preVisitUtbetalingstidslinje() {
            val utbetalingstidslinje = mutableListOf<UtbetalingstidslinjedagDTO>()
            vedtaksperiodeMap["utbetalingstidslinje"] = utbetalingstidslinje
            pushState(UtbetalingstidslinjeState(utbetalingstidslinje, totalbeløpArbeidstaker))
        }

        override fun visitTilstand(tilstand: Vedtaksperiode.Vedtaksperiodetilstand) {
            vedtaksperiodeMap["tilstand"] =
                mapTilstander(tilstand = tilstand.type, utbetalt = (totalbeløpArbeidstaker > 0))
        }

        override fun preVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) {
            pushState(SykdomshistorikkState(hendelser, beregnetSykdomstidslinje))
        }

        override fun preVisitSykdomshistorikkElement(
            element: Sykdomshistorikk.Element,
            id: UUID,
            tidsstempel: LocalDateTime
        ) {
            hendelser.add(id)
        }

        override fun preVisitUtbetalingslinjer(linjer: List<Utbetalingslinje>) {
            val utbetalingstidslinjeListe = mutableListOf<UtbetalingslinjeDTO>()
            vedtaksperiodeMap["utbetalingslinjer"] = utbetalingstidslinjeListe
            pushState(UtbetalingslinjeState(utbetalingstidslinjeListe))
        }

        override fun postVisitVedtaksperiode(vedtaksperiode: Vedtaksperiode, id: UUID) {
            vedtaksperiodeMap["totalbeløpArbeidstaker"] = totalbeløpArbeidstaker
            vedtaksperioder.add(vedtaksperiodeMap.mapTilVedtaksperiodeDto())
            popState()
        }
    }

    private inner class UtbetalingslinjeState(private val utbetalingstidslinjeListe: MutableList<UtbetalingslinjeDTO>) :
        JsonState {
        private val linjerForSpeil = mutableListOf<Utbetalingslinje>()

        override fun visitUtbetalingslinje(utbetalingslinje: Utbetalingslinje) {
            linjerForSpeil.add(utbetalingslinje)
        }

        override fun postVisitUtbetalingslinjer(linjer: List<Utbetalingslinje>) {
            linjerForSpeil.forEach { utbetalingslinje ->
                val utbetalingstidslinjeMap = UtbetalingslinjeDTO(
                    utbetalingslinje.fom,
                    utbetalingslinje.tom,
                    utbetalingslinje.dagsats,
                    utbetalingslinje.grad
                )
                utbetalingstidslinjeListe.add(utbetalingstidslinjeMap)
            }
            popState()
        }
    }

    private inner class UtbetalingstidslinjeState(
        private val utbetalingstidslinjeMap: MutableList<UtbetalingstidslinjedagDTO>,
        private var totalbeløpArbeidstaker: Double
    ) : JsonState {

        override fun visitArbeidsdag(dag: Utbetalingstidslinje.Utbetalingsdag.Arbeidsdag) {
            utbetalingstidslinjeMap.add(
                UtbetalingsdagDTO(
                    type = TypeDataDTO.Arbeidsdag,
                    inntekt = dag.inntekt,
                    dato = dag.dato
                )
            )
        }

        override fun visitArbeidsgiverperiodeDag(dag: Utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag) {
            utbetalingstidslinjeMap.add(
                UtbetalingsdagDTO(
                    type = TypeDataDTO.ArbeidsgiverperiodeDag,
                    inntekt = dag.inntekt,
                    dato = dag.dato
                )
            )
        }

        override fun visitNavDag(dag: Utbetalingstidslinje.Utbetalingsdag.NavDag) {
            utbetalingstidslinjeMap.add(
                NavDagDTO(
                    type = TypeDataDTO.NavDag,
                    inntekt = dag.inntekt,
                    dato = dag.dato,
                    utbetaling = dag.utbetaling,
                    grad = dag.grad
                )
            )
            totalbeløpArbeidstaker += dag.utbetaling
        }

        override fun visitNavHelgDag(dag: Utbetalingstidslinje.Utbetalingsdag.NavHelgDag) {
            utbetalingstidslinjeMap.add(
                UtbetalingsdagMedGradDTO(
                    type = TypeDataDTO.NavHelgDag,
                    inntekt = dag.inntekt,
                    dato = dag.dato,
                    grad = dag.grad
                )
            )
        }

        override fun visitFridag(dag: Utbetalingstidslinje.Utbetalingsdag.Fridag) {
            utbetalingstidslinjeMap.add(
                UtbetalingsdagDTO(
                    type = TypeDataDTO.Fridag,
                    inntekt = dag.inntekt,
                    dato = dag.dato
                )
            )
        }

        override fun visitUkjentDag(dag: Utbetalingstidslinje.Utbetalingsdag.UkjentDag) {
            utbetalingstidslinjeMap.add(
                UtbetalingsdagDTO(
                    type = TypeDataDTO.UkjentDag,
                    inntekt = dag.inntekt,
                    dato = dag.dato
                )
            )
        }

        override fun visitAvvistDag(dag: Utbetalingstidslinje.Utbetalingsdag.AvvistDag) {
            utbetalingstidslinjeMap.add(
                AvvistDagDTO(
                    type = TypeDataDTO.AvvistDag,
                    inntekt = dag.inntekt,
                    dato = dag.dato,
                    begrunnelse = mapBegrunnelse(dag.begrunnelse),
                    grad = dag.grad
                )
            )
        }

        override fun visitForeldetDag(dag: Utbetalingstidslinje.Utbetalingsdag.ForeldetDag) {
            utbetalingstidslinjeMap.add(
                UtbetalingsdagDTO(
                    type = TypeDataDTO.ForeldetDag,
                    inntekt = dag.inntekt,
                    dato = dag.dato
                )
            )
        }

        override fun postVisitUtbetalingstidslinje() {
            popState()
        }
    }

    private inner class SykdomshistorikkState(
        private val hendelser: MutableSet<UUID>,
        private val sykdomstidslinjeListe: MutableList<SykdomstidslinjedagDTO>
    ) : JsonState {

        override fun preVisitSykdomshistorikkElement(
            element: Sykdomshistorikk.Element,
            id: UUID,
            tidsstempel: LocalDateTime
        ) {
            hendelser.add(id)
        }

        override fun preVisitBeregnetSykdomstidslinje(tidslinje: Sykdomstidslinje) {
            pushState(SykdomstidslinjeState(sykdomstidslinjeListe))
        }

        override fun postVisitSykdomshistorikkElement(
            element: Sykdomshistorikk.Element,
            id: UUID,
            tidsstempel: LocalDateTime
        ) {
            popState()
        }

        override fun postVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) {
            popState()
        }

        override fun preVisitHendelseSykdomstidslinje(tidslinje: Sykdomstidslinje) {}

        override fun postVisitHendelseSykdomstidslinje(tidslinje: Sykdomstidslinje) {}

        override fun postVisitBeregnetSykdomstidslinje(tidslinje: Sykdomstidslinje) {}
    }

    private inner class SykdomstidslinjeState(private val sykdomstidslinjeListe: MutableList<SykdomstidslinjedagDTO>) :
        JsonState {

        override fun visitArbeidsdag(arbeidsdag: Arbeidsdag.Inntektsmelding) =
            leggTilDag(JsonDagType.ARBEIDSDAG_INNTEKTSMELDING, arbeidsdag)

        override fun visitArbeidsdag(arbeidsdag: Arbeidsdag.Søknad) =
            leggTilDag(JsonDagType.ARBEIDSDAG_SØKNAD, arbeidsdag)

        override fun visitEgenmeldingsdag(egenmeldingsdag: Egenmeldingsdag.Inntektsmelding) =
            leggTilDag(JsonDagType.EGENMELDINGSDAG_INNTEKTSMELDING, egenmeldingsdag)

        override fun visitEgenmeldingsdag(egenmeldingsdag: Egenmeldingsdag.Søknad) =
            leggTilDag(JsonDagType.EGENMELDINGSDAG_SØKNAD, egenmeldingsdag)

        override fun visitFeriedag(feriedag: Feriedag.Inntektsmelding) =
            leggTilDag(JsonDagType.FERIEDAG_INNTEKTSMELDING, feriedag)

        override fun visitFeriedag(feriedag: Feriedag.Søknad) = leggTilDag(JsonDagType.FERIEDAG_SØKNAD, feriedag)
        override fun visitImplisittDag(implisittDag: ImplisittDag) = leggTilDag(JsonDagType.IMPLISITT_DAG, implisittDag)
        override fun visitPermisjonsdag(permisjonsdag: Permisjonsdag.Søknad) =
            leggTilDag(JsonDagType.PERMISJONSDAG_SØKNAD, permisjonsdag)

        override fun visitPermisjonsdag(permisjonsdag: Permisjonsdag.Aareg) =
            leggTilDag(JsonDagType.PERMISJONSDAG_AAREG, permisjonsdag)

        override fun visitStudiedag(studiedag: Studiedag) = leggTilDag(JsonDagType.STUDIEDAG, studiedag)
        override fun visitSykHelgedag(sykHelgedag: SykHelgedag.Sykmelding) =
            leggTilSykedag(JsonDagType.SYK_HELGEDAG_SYKMELDING, sykHelgedag)

        override fun visitSykHelgedag(sykHelgedag: SykHelgedag.Søknad) =
            leggTilSykedag(JsonDagType.SYK_HELGEDAG_SØKNAD, sykHelgedag)

        override fun visitSykedag(sykedag: Sykedag.Sykmelding) = leggTilSykedag(JsonDagType.SYKEDAG_SYKMELDING, sykedag)
        override fun visitSykedag(sykedag: Sykedag.Søknad) = leggTilSykedag(JsonDagType.SYKEDAG_SØKNAD, sykedag)
        override fun visitUbestemt(ubestemtdag: Ubestemtdag) = leggTilDag(JsonDagType.UBESTEMTDAG, ubestemtdag)
        override fun visitUtenlandsdag(utenlandsdag: Utenlandsdag) = leggTilDag(JsonDagType.UTENLANDSDAG, utenlandsdag)

        private fun leggTilDag(jsonDagType: JsonDagType, dag: Dag) {
            sykdomstidslinjeListe.add(
                SykdomstidslinjedagDTO(dag.dagen, jsonDagType)
            )
        }

        private fun leggTilSykedag(jsonDagType: JsonDagType, dag: GradertDag) {
            sykdomstidslinjeListe.add(
                SykdomstidslinjedagDTO(dag.dagen, jsonDagType, dag.grad)
            )
        }

        override fun postVisitBeregnetSykdomstidslinje(tidslinje: Sykdomstidslinje) {
            popState()
        }
    }
}
