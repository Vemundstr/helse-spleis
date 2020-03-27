package no.nav.helse.serde

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.person.*
import no.nav.helse.person.Vedtaksperiode.*
import no.nav.helse.serde.PersonData.ArbeidsgiverData
import no.nav.helse.serde.mapping.JsonDagType
import no.nav.helse.serde.mapping.konverterTilAktivitetslogg
import no.nav.helse.serde.migration.JsonMigration
import no.nav.helse.serde.migration.migrate
import no.nav.helse.serde.reflection.*
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.dag.*
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.utbetalingstidslinje.Utbetalingslinje
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal val serdeObjectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

private typealias SykdomstidslinjeData = List<ArbeidsgiverData.VedtaksperiodeData.DagData>

class SerialisertPerson(val json: String) {
    internal companion object {
        private val migrations = emptyList<JsonMigration>()

        fun gjeldendeVersjon() = JsonMigration.gjeldendeVersjon(migrations)
        fun medSkjemaversjon(jsonNode: JsonNode) = JsonMigration.medSkjemaversjon(migrations, jsonNode)
    }

    val skjemaVersjon = gjeldendeVersjon()

    private fun migrate(jsonNode: JsonNode) {
        migrations.migrate(jsonNode)
    }

    fun deserialize(): Person {
        val jsonNode = serdeObjectMapper.readTree(json)

        migrate(jsonNode)

        val personData: PersonData = serdeObjectMapper.treeToValue(jsonNode)
        val arbeidsgivere = mutableListOf<Arbeidsgiver>()
        val aktivitetslogg = personData.aktivitetslogg?.let(::konverterTilAktivitetslogg) ?: Aktivitetslogg()

        val person = createPerson(
            aktørId = personData.aktørId,
            fødselsnummer = personData.fødselsnummer,
            arbeidsgivere = arbeidsgivere,
            aktivitetslogg = aktivitetslogg
        )

        arbeidsgivere.addAll(personData.arbeidsgivere.map { konverterTilArbeidsgiver(person, personData, it) })

        return person
    }

    private fun konverterTilArbeidsgiver(
        person: Person,
        personData: PersonData,
        data: ArbeidsgiverData
    ): Arbeidsgiver {
        val inntekthistorikk = Inntekthistorikk()
        val vedtaksperioder = mutableListOf<Vedtaksperiode>()

        data.inntekter.forEach { inntektData ->
            inntekthistorikk.add(
                fom = inntektData.fom,
                hendelseId = inntektData.hendelseId,
                beløp = inntektData.beløp.setScale(1, RoundingMode.HALF_UP)
            )
        }

        val arbeidsgiver = createArbeidsgiver(
            person = person,
            organisasjonsnummer = data.organisasjonsnummer,
            id = data.id,
            inntekthistorikk = inntekthistorikk,
            tidslinjer = data.utbetalingstidslinjer.map(::konverterTilUtbetalingstidslinje).toMutableList(),
            perioder = vedtaksperioder
        )

        vedtaksperioder.addAll(data.vedtaksperioder.map {
            parseVedtaksperiode(
                person,
                arbeidsgiver,
                personData,
                data,
                it
            )
        })
        Vedtaksperiode.sorter(vedtaksperioder)

        return arbeidsgiver
    }

    private fun konverterTilUtbetalingstidslinje(data: UtbetalingstidslinjeData): Utbetalingstidslinje {
        return createUtbetalingstidslinje(data.dager.map {
            when (it.type) {
                UtbetalingstidslinjeData.TypeData.ArbeidsgiverperiodeDag -> {
                    Utbetalingsdag.ArbeidsgiverperiodeDag(inntekt = it.inntekt, dato = it.dato)
                }
                UtbetalingstidslinjeData.TypeData.NavDag -> {
                    createNavUtbetalingdag(
                        inntekt = it.inntekt,
                        dato = it.dato,
                        utbetaling = it.utbetaling!!,
                        grad = it.grad!!
                    )
                }
                UtbetalingstidslinjeData.TypeData.NavHelgDag -> {
                    Utbetalingsdag.NavHelgDag(inntekt = it.inntekt, dato = it.dato, grad = it.grad!!)
                }
                UtbetalingstidslinjeData.TypeData.Arbeidsdag -> {
                    Utbetalingsdag.Arbeidsdag(inntekt = it.inntekt, dato = it.dato)
                }
                UtbetalingstidslinjeData.TypeData.Fridag -> {
                    Utbetalingsdag.Fridag(inntekt = it.inntekt, dato = it.dato)
                }
                UtbetalingstidslinjeData.TypeData.AvvistDag -> {
                    Utbetalingsdag.AvvistDag(
                        inntekt = it.inntekt, dato = it.dato, begrunnelse = when (it.begrunnelse) {
                            UtbetalingstidslinjeData.BegrunnelseData.SykepengedagerOppbrukt -> Begrunnelse.SykepengedagerOppbrukt
                            UtbetalingstidslinjeData.BegrunnelseData.MinimumInntekt -> Begrunnelse.MinimumInntekt
                            UtbetalingstidslinjeData.BegrunnelseData.EgenmeldingUtenforArbeidsgiverperiode -> Begrunnelse.EgenmeldingUtenforArbeidsgiverperiode
                            UtbetalingstidslinjeData.BegrunnelseData.MinimumSykdomsgrad -> Begrunnelse.MinimumSykdomsgrad
                            null -> error("Prøver å deserialisere avvist dag uten begrunnelse")
                        }, grad = Double.NaN
                    )
                }
                UtbetalingstidslinjeData.TypeData.UkjentDag -> {
                    Utbetalingsdag.UkjentDag(inntekt = it.inntekt, dato = it.dato)
                }
                UtbetalingstidslinjeData.TypeData.ForeldetDag -> {
                    Utbetalingsdag.ForeldetDag(inntekt = it.inntekt, dato = it.dato)
                }
            }
        }
            .toMutableList())
    }

    private fun parseVedtaksperiode(
        person: Person,
        arbeidsgiver: Arbeidsgiver,
        personData: PersonData,
        arbeidsgiverData: ArbeidsgiverData,
        data: ArbeidsgiverData.VedtaksperiodeData
    ): Vedtaksperiode {
        return createVedtaksperiode(
            person = person,
            arbeidsgiver = arbeidsgiver,
            id = data.id,
            aktørId = personData.aktørId,
            fødselsnummer = personData.fødselsnummer,
            organisasjonsnummer = arbeidsgiverData.organisasjonsnummer,
            tilstand = parseTilstand(data.tilstand),
            maksdato = data.maksdato,
            forbrukteSykedager = data.forbrukteSykedager,
            utbetalingslinjer = data.utbetalingslinjer.map(::parseUtbetalingslinje),
            godkjentAv = data.godkjentAv,
            godkjenttidspunkt = data.godkjenttidspunkt,
            utbetalingsreferanse = data.utbetalingsreferanse,
            førsteFraværsdag = data.førsteFraværsdag,
            dataForSimulering = data.dataForSimulering?.let(::parseDataForSimulering),
            dataForVilkårsvurdering = data.dataForVilkårsvurdering?.let(::parseDataForVilkårsvurdering),
            sykdomshistorikk = parseSykdomshistorikk(data.sykdomshistorikk),
            utbetalingstidslinje = konverterTilUtbetalingstidslinje(data.utbetalingstidslinje)
        )
    }

    private fun parseSykdomstidslinje(
        tidslinjeData: SykdomstidslinjeData
    ): Sykdomstidslinje = Sykdomstidslinje(tidslinjeData.map(::parseDag))

    private fun parseDag(
        data: ArbeidsgiverData.VedtaksperiodeData.DagData
    ): Dag {
        return when (data.type) {
            JsonDagType.ARBEIDSDAG_INNTEKTSMELDING -> Arbeidsdag.Inntektsmelding(data.dagen)
            JsonDagType.ARBEIDSDAG_SØKNAD -> Arbeidsdag.Søknad(data.dagen)
            JsonDagType.EGENMELDINGSDAG_INNTEKTSMELDING -> Egenmeldingsdag.Inntektsmelding(data.dagen)
            JsonDagType.EGENMELDINGSDAG_SØKNAD -> Egenmeldingsdag.Søknad(data.dagen)
            JsonDagType.FERIEDAG_INNTEKTSMELDING -> Feriedag.Inntektsmelding(data.dagen)
            JsonDagType.FERIEDAG_SØKNAD -> Feriedag.Søknad(data.dagen)
            JsonDagType.IMPLISITT_DAG -> ImplisittDag(data.dagen)
            JsonDagType.KUN_ARBEIDSGIVER_SYKEDAG -> KunArbeidsgiverSykedag(data.dagen, data.grad)
            JsonDagType.PERMISJONSDAG_SØKNAD -> Permisjonsdag.Søknad(data.dagen)
            JsonDagType.PERMISJONSDAG_AAREG -> Permisjonsdag.Aareg(data.dagen)
            JsonDagType.STUDIEDAG -> Studiedag(data.dagen)
            JsonDagType.SYKEDAG_SYKMELDING -> Sykedag.Sykmelding(data.dagen, data.grad)
            JsonDagType.SYKEDAG_SØKNAD -> Sykedag.Søknad(data.dagen, data.grad)
            JsonDagType.SYK_HELGEDAG_SYKMELDING -> SykHelgedag.Sykmelding(data.dagen, data.grad)
            JsonDagType.SYK_HELGEDAG_SØKNAD -> SykHelgedag.Søknad(data.dagen, data.grad)
            JsonDagType.UBESTEMTDAG -> Ubestemtdag(data.dagen)
            JsonDagType.UTENLANDSDAG -> Utenlandsdag(data.dagen)
        }
    }

    private fun parseTilstand(tilstand: TilstandType) = when (tilstand) {
        TilstandType.AVVENTER_HISTORIKK -> AvventerHistorikk
        TilstandType.AVVENTER_GODKJENNING -> AvventerGodkjenning
        TilstandType.AVVENTER_SIMULERING -> AvventerSimulering
        TilstandType.TIL_UTBETALING -> TilUtbetaling
        TilstandType.AVSLUTTET -> Avsluttet
        TilstandType.AVSLUTTET_UTEN_UTBETALING -> AvsluttetUtenUtbetaling
        TilstandType.UTBETALING_FEILET -> UtbetalingFeilet
        TilstandType.TIL_INFOTRYGD -> TilInfotrygd
        TilstandType.START -> Start
        TilstandType.MOTTATT_SYKMELDING_FERDIG_FORLENGELSE -> MottattSykmeldingFerdigForlengelse
        TilstandType.MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE -> MottattSykmeldingUferdigForlengelse
        TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP -> MottattSykmeldingFerdigGap
        TilstandType.MOTTATT_SYKMELDING_UFERDIG_GAP -> MottattSykmeldingUferdigGap
        TilstandType.AVVENTER_SØKNAD_FERDIG_GAP -> AvventerSøknadFerdigGap
        TilstandType.AVVENTER_VILKÅRSPRØVING_GAP -> AvventerVilkårsprøvingGap
        TilstandType.AVVENTER_GAP -> AvventerGap
        TilstandType.AVVENTER_SØKNAD_UFERDIG_GAP -> AvventerSøknadUferdigGap
        TilstandType.AVVENTER_INNTEKTSMELDING_FERDIG_GAP -> AvventerInntektsmeldingFerdigGap
        TilstandType.AVVENTER_INNTEKTSMELDING_UFERDIG_GAP -> AvventerInntektsmeldingUferdigGap
        TilstandType.AVVENTER_UFERDIG_GAP -> AvventerUferdigGap
        TilstandType.AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE -> AvventerInntektsmeldingUferdigForlengelse
        TilstandType.AVVENTER_SØKNAD_UFERDIG_FORLENGELSE -> AvventerSøknadUferdigForlengelse
        TilstandType.AVVENTER_UFERDIG_FORLENGELSE -> AvventerUferdigForlengelse
    }

    private fun parseUtbetalingslinje(
        data: ArbeidsgiverData.VedtaksperiodeData.UtbetalingslinjeData
    ): Utbetalingslinje = Utbetalingslinje(fom = data.fom, tom = data.tom, dagsats = data.dagsats, grad = data.grad)

    private fun parseDataForVilkårsvurdering(
        data: ArbeidsgiverData.VedtaksperiodeData.DataForVilkårsvurderingData
    ): Vilkårsgrunnlag.Grunnlagsdata =
        Vilkårsgrunnlag.Grunnlagsdata(
            erEgenAnsatt = data.erEgenAnsatt,
            beregnetÅrsinntektFraInntektskomponenten = data.beregnetÅrsinntektFraInntektskomponenten,
            avviksprosent = data.avviksprosent,
            harOpptjening = data.harOpptjening,
            antallOpptjeningsdagerErMinst = data.antallOpptjeningsdagerErMinst
        )

    private fun parseDataForSimulering(
        data: ArbeidsgiverData.VedtaksperiodeData.DataForSimuleringData
    ) = Simulering.SimuleringResultat(
        totalbeløp = data.totalbeløp,
        perioder = data.perioder.map { periode ->
            Simulering.SimulertPeriode(
                fom = periode.fom,
                tom = periode.tom,
                utbetalinger = periode.utbetalinger.map { utbetaling ->
                    Simulering.SimulertUtbetaling(
                        forfallsdato = utbetaling.forfallsdato,
                        utbetalesTil = Simulering.Mottaker(
                            id = utbetaling.utbetalesTil.id,
                            navn = utbetaling.utbetalesTil.navn
                        ),
                        feilkonto = utbetaling.feilkonto,
                        detaljer = utbetaling.detaljer.map { detalj ->
                            Simulering.Detaljer(
                                fom = detalj.fom,
                                tom = detalj.tom,
                                konto = detalj.konto,
                                beløp = detalj.beløp,
                                klassekode = Simulering.Klassekode(
                                    kode = detalj.klassekode.kode,
                                    beskrivelse = detalj.klassekode.beskrivelse
                                ),
                                uføregrad = detalj.uføregrad,
                                utbetalingstype = detalj.utbetalingstype,
                                tilbakeføring = detalj.tilbakeføring,
                                sats = Simulering.Sats(
                                    sats = detalj.sats.sats,
                                    antall = detalj.sats.antall,
                                    type = detalj.sats.type
                                ),
                                refunderesOrgnummer = detalj.refunderesOrgnummer
                            )
                        }
                    )
                }
            )
        }
    )

    private fun parseSykdomshistorikk(
        data: List<ArbeidsgiverData.VedtaksperiodeData.SykdomshistorikkData>
    ): Sykdomshistorikk {
        return createSykdomshistorikk(data.map { sykdomshistorikkData ->
            createSykdomshistorikkElement(
                timestamp = sykdomshistorikkData.tidsstempel,
                hendelseSykdomstidslinje = parseSykdomstidslinje(sykdomshistorikkData.hendelseSykdomstidslinje),
                beregnetSykdomstidslinje = parseSykdomstidslinje(sykdomshistorikkData.beregnetSykdomstidslinje),
                hendelseId = sykdomshistorikkData.hendelseId
            )
        }
        )
    }

}

internal data class PersonData(
    val aktørId: String,
    val fødselsnummer: String,
    val arbeidsgivere: List<ArbeidsgiverData>,
    val aktivitetslogg: AktivitetsloggData?
) {

    internal data class AktivitetsloggData(
        val aktiviteter: List<AktivitetData>
    ) {
        data class AktivitetData(
            val alvorlighetsgrad: Alvorlighetsgrad,
            val label: Char,
            val behovtype: String?,
            val melding: String,
            val tidsstempel: String,
            val kontekster: List<SpesifikkKontekstData>,
            val detaljer: Map<String, Any>
        )

        data class SpesifikkKontekstData(
            val kontekstType: String,
            val kontekstMap: Map<String, String>
        )

        enum class Alvorlighetsgrad {
            INFO,
            WARN,
            BEHOV,
            ERROR,
            SEVERE
        }
    }

    data class ArbeidsgiverData(
        val organisasjonsnummer: String,
        val id: UUID,
        val inntekter: List<InntektData>,
        val vedtaksperioder: List<VedtaksperiodeData>,
        val utbetalingstidslinjer: List<UtbetalingstidslinjeData>
    ) {
        data class InntektData(
            val fom: LocalDate,
            val hendelseId: UUID,
            val beløp: BigDecimal
        )

        data class VedtaksperiodeData(
            val id: UUID,
            val maksdato: LocalDate?,
            val forbrukteSykedager: Int?,
            val godkjentAv: String?,
            val godkjenttidspunkt: LocalDateTime?,
            val utbetalingsreferanse: String,
            val førsteFraværsdag: LocalDate?,
            val dataForVilkårsvurdering: DataForVilkårsvurderingData?,
            val dataForSimulering: DataForSimuleringData?,
            val sykdomshistorikk: List<SykdomshistorikkData>,
            val tilstand: TilstandType,
            val utbetalingslinjer: List<UtbetalingslinjeData>,
            val utbetalingstidslinje: UtbetalingstidslinjeData
        ) {
            data class DagData(
                val dagen: LocalDate,
                val type: JsonDagType,
                val grad: Double
            )

            data class SykdomshistorikkData(
                val tidsstempel: LocalDateTime,
                val hendelseId: UUID,
                val hendelseSykdomstidslinje: SykdomstidslinjeData,
                val beregnetSykdomstidslinje: SykdomstidslinjeData
            )

            data class DataForVilkårsvurderingData(
                val erEgenAnsatt: Boolean,
                val beregnetÅrsinntektFraInntektskomponenten: Double,
                val avviksprosent: Double,
                val harOpptjening: Boolean,
                val antallOpptjeningsdagerErMinst: Int
            )

            data class DataForSimuleringData(
                val totalbeløp: BigDecimal,
                val perioder: List<SimulertPeriode>
            ) {
                data class SimulertPeriode(
                    val fom: LocalDate,
                    val tom: LocalDate,
                    val utbetalinger: List<SimulertUtbetaling>
                )

                data class SimulertUtbetaling(
                    val forfallsdato: LocalDate,
                    val utbetalesTil: Mottaker,
                    val feilkonto: Boolean,
                    val detaljer: List<Detaljer>
                )

                data class Detaljer(
                    val fom: LocalDate,
                    val tom: LocalDate,
                    val konto: String,
                    val beløp: BigDecimal,
                    val klassekode: Klassekode,
                    val uføregrad: Int,
                    val utbetalingstype: String,
                    val tilbakeføring: Boolean,
                    val sats: Sats,
                    val refunderesOrgnummer: String
                )

                data class Sats(
                    val sats: BigDecimal,
                    val antall: Int,
                    val type: String
                )

                data class Klassekode(
                    val kode: String,
                    val beskrivelse: String
                )

                data class Mottaker(
                    val id: String,
                    val navn: String
                )
            }

            data class UtbetalingslinjeData(
                val fom: LocalDate,
                val tom: LocalDate,
                val dagsats: Int,
                val grad: Double
            )
        }
    }
}

data class UtbetalingstidslinjeData(
    val dager: List<UtbetalingsdagData>
) {
    enum class BegrunnelseData {
        SykepengedagerOppbrukt,
        MinimumInntekt,
        EgenmeldingUtenforArbeidsgiverperiode,
        MinimumSykdomsgrad
    }

    enum class TypeData {
        ArbeidsgiverperiodeDag,
        NavDag,
        NavHelgDag,
        Arbeidsdag,
        Fridag,
        AvvistDag,
        UkjentDag,
        ForeldetDag
    }

    data class UtbetalingsdagData(
        val type: TypeData,
        val dato: LocalDate,
        val inntekt: Double,
        val utbetaling: Int?,
        val begrunnelse: BegrunnelseData?,
        val grad: Double?
    )
}
