package no.nav.helse.person.etterlevelse

import no.nav.helse.Fødselsnummer
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.person.*
import no.nav.helse.person.Bokstav.BOKSTAV_A
import no.nav.helse.person.Ledd.Companion.ledd
import no.nav.helse.person.Paragraf.PARAGRAF_8_17
import no.nav.helse.person.Paragraf.PARAGRAF_8_51
import no.nav.helse.person.Punktum.Companion.punktum
import no.nav.helse.person.etterlevelse.Subsumsjon.Utfall
import no.nav.helse.person.etterlevelse.Subsumsjon.Utfall.*
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Prosent
import java.time.LocalDate
import java.time.Year
import java.time.format.DateTimeFormatter
import java.util.*

class MaskinellJurist private constructor(
    private val parent: MaskinellJurist?,
    private val kontekster: Map<String, String>
) : SubsumsjonObserver {

    private var subsumsjoner = listOf<Subsumsjon>()

    constructor() : this(null, emptyMap())

    private fun leggTil(subsumsjon: Subsumsjon) {
        subsumsjoner = subsumsjon.sammenstill(subsumsjoner)
        parent?.leggTil(subsumsjon)
    }

    private fun kontekster(): Map<String, String> = this.kontekster.toMap()

    fun medFødselsnummer(fødselsnummer: Fødselsnummer) = kopierMedKontekst(mapOf("fødselsnummer" to fødselsnummer.toString()))
    fun medOrganisasjonsnummer(organisasjonsnummer: String) = kopierMedKontekst(mapOf("organisasjonsnummer" to organisasjonsnummer))
    fun medVedtaksperiode(vedtaksperiodeId: UUID, hendelseIder: List<UUID>) = kopierMedKontekst(mapOf("vedtaksperiode" to vedtaksperiodeId.toString()))
    private fun kopierMedKontekst(kontekster: Map<String, String>) = MaskinellJurist(this, this.kontekster + kontekster)

    override fun `§ 8-2 ledd 1`(
        oppfylt: Boolean,
        skjæringstidspunkt: LocalDate,
        tilstrekkeligAntallOpptjeningsdager: Int,
        arbeidsforhold: List<Map<String, Any?>>,
        antallOpptjeningsdager: Int
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                versjon = LocalDate.of(2020, 6, 12),
                paragraf = Paragraf.PARAGRAF_8_2,
                ledd = 1.ledd,
                input = mapOf(
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "tilstrekkeligAntallOpptjeningsdager" to tilstrekkeligAntallOpptjeningsdager,
                    "arbeidsforhold" to arbeidsforhold
                ),
                output = mapOf("antallOpptjeningsdager" to antallOpptjeningsdager),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-3 ledd 1 punktum 2`(
        oppfylt: Boolean,
        syttiårsdagen: LocalDate,
        utfallFom: LocalDate,
        utfallTom: LocalDate,
        tidslinjeFom: LocalDate,
        tidslinjeTom: LocalDate,
        avvisteDager: List<LocalDate>
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                versjon = LocalDate.of(2011, 12, 16),
                paragraf = Paragraf.PARAGRAF_8_3,
                ledd = 1.ledd,
                punktum = 2.punktum,
                input = mapOf(
                    "syttiårsdagen" to syttiårsdagen,
                    "utfallFom" to utfallFom,
                    "utfallTom" to utfallTom,
                    "tidslinjeFom" to tidslinjeFom,
                    "tidslinjeTom" to tidslinjeTom
                ),
                output = mapOf("avvisteDager" to avvisteDager.grupperSammenhengendePerioder()),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-3 ledd 2 punktum 1`(oppfylt: Boolean, skjæringstidspunkt: LocalDate, grunnlagForSykepengegrunnlag: Inntekt, minimumInntekt: Inntekt) {
        leggTil(
            EnkelSubsumsjon(
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                versjon = LocalDate.of(2011, 12, 16),
                paragraf = Paragraf.PARAGRAF_8_3,
                ledd = 2.ledd,
                punktum = 1.punktum,
                input = mapOf(
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "grunnlagForSykepengegrunnlag" to grunnlagForSykepengegrunnlag.reflection { årlig, _, _, _ -> årlig },
                    "minimumInntekt" to minimumInntekt.reflection { årlig, _, _, _ -> årlig }
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-10 ledd 2 punktum 1`(
        erBegrenset: Boolean,
        maksimaltSykepengegrunnlag: Inntekt,
        skjæringstidspunkt: LocalDate,
        grunnlagForSykepengegrunnlag: Inntekt
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                LocalDate.of(2020, 1, 1),
                Paragraf.PARAGRAF_8_10,
                2.ledd,
                1.punktum,
                input = mapOf(
                    "maksimaltSykepengegrunnlag" to maksimaltSykepengegrunnlag.reflection { årlig, _, _, _ -> årlig },
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "grunnlagForSykepengegrunnlag" to grunnlagForSykepengegrunnlag.reflection { årlig, _, _, _ -> årlig }
                ),
                output = mapOf(
                    "erBegrenset" to erBegrenset
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-10 ledd 3`(oppfylt: Boolean) {
        super.`§ 8-10 ledd 3`(oppfylt)
    }

    override fun `§ 8-11 første ledd`() {
        super.`§ 8-11 første ledd`()
    }

    override fun `§ 8-12 ledd 1 punktum 1`(
        periode: Periode,
        tidslinjegrunnlag: List<List<Map<String, Any>>>,
        beregnetTidslinje: List<Map<String, Any>>,
        gjenståendeSykedager: Int,
        forbrukteSykedager: Int,
        maksdato: LocalDate,
        startdatoSykepengerettighet: LocalDate
    ) {
        val (dagerOppfylt, dagerIkkeOppfylt) =
            periode
                .filter { it >= startdatoSykepengerettighet }
                .partition { it <= maksdato }

        fun logg(utfall: Utfall, utfallFom: LocalDate, utfallTom: LocalDate) {
            leggTil(
                EnkelSubsumsjon(
                    utfall = utfall,
                    versjon = LocalDate.of(2021, 5, 21),
                    paragraf = Paragraf.PARAGRAF_8_12,
                    ledd = 1.ledd,
                    punktum = 1.punktum,
                    input = mapOf(
                        "fom" to periode.start,
                        "tom" to periode.endInclusive,
                        "utfallFom" to utfallFom,
                        "utfallTom" to utfallTom,
                        "tidslinjegrunnlag" to tidslinjegrunnlag,
                        "beregnetTidslinje" to beregnetTidslinje
                    ),
                    output = mapOf(
                        "gjenståendeSykedager" to gjenståendeSykedager,
                        "forbrukteSykedager" to forbrukteSykedager,
                        "maksdato" to maksdato,
                    ),
                    kontekster = kontekster()
                )
            )
        }
        if (dagerOppfylt.isNotEmpty()) logg(VILKAR_OPPFYLT, dagerOppfylt.first(), dagerOppfylt.last())
        if (dagerIkkeOppfylt.isNotEmpty()) logg(VILKAR_IKKE_OPPFYLT, dagerIkkeOppfylt.first(), dagerIkkeOppfylt.last())
    }

    override fun `§ 8-12 ledd 2`(
        oppfylt: Boolean,
        dato: LocalDate,
        gjenståendeSykepengedager: Int,
        beregnetAntallOppholdsdager: Int,
        tilstrekkeligOppholdISykedager: Int,
        tidslinjegrunnlag: List<List<Map<String, Any>>>,
        beregnetTidslinje: List<Map<String, Any>>
    ) {
        leggTil(
            BetingetSubsumsjon(
                funnetRelevant = oppfylt || gjenståendeSykepengedager == 0, // Bare relevant om det er ny rett på sykepenger eller om vilkåret ikke er oppfylt
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                versjon = LocalDate.of(2021, 5, 21),
                paragraf = Paragraf.PARAGRAF_8_12,
                ledd = 2.ledd,
                punktum = null,
                bokstav = null,
                input = mapOf(
                    "dato" to dato,
                    "tilstrekkeligOppholdISykedager" to tilstrekkeligOppholdISykedager,
                    "tidslinjegrunnlag" to tidslinjegrunnlag,
                    "beregnetTidslinje" to beregnetTidslinje
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-13 ledd 1`(oppfylt: Boolean, avvisteDager: List<LocalDate>) {
        leggTil(
            EnkelSubsumsjon(
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                paragraf = Paragraf.PARAGRAF_8_13,
                ledd = Ledd.LEDD_1,
                versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
                input = mapOf("avvisteDager" to avvisteDager),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-16 ledd 1`(dato: LocalDate, dekningsgrad: Double, inntekt: Double, dekningsgrunnlag: Double) {
        leggTil(
            GrupperbarSubsumsjon(
                dato = dato,
                input = mapOf("dekningsgrad" to dekningsgrad, "inntekt" to inntekt),
                output = mapOf("dekningsgrunnlag" to dekningsgrunnlag),
                utfall = VILKAR_BEREGNET,
                paragraf = Paragraf.PARAGRAF_8_16,
                ledd = 1.ledd,
                versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-17 ledd 1 bokstav a`(arbeidsgiverperiode: List<LocalDate>, førsteNavdag: LocalDate) {
        leggTil(
            EnkelSubsumsjon(
                VILKAR_BEREGNET,
                LocalDate.of(2018, 1, 1),
                paragraf = PARAGRAF_8_17,
                ledd = 1.ledd,
                bokstav = BOKSTAV_A,
                input = mapOf(
                    "arbeidsgiverperioder" to arbeidsgiverperiode.grupperSammenhengendePerioder().map {
                        mapOf("fom" to it.start, "tom" to it.endInclusive)
                    }
                ),
                output = mapOf(
                    "førsteUtbetalingsdag" to førsteNavdag
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-17 ledd 2`(oppfylt: Boolean) {
        super.`§ 8-17 ledd 2`(oppfylt)
    }

    override fun `§ 8-28 ledd 3 bokstav a`(oppfylt: Boolean, grunnlagForSykepengegrunnlag: Inntekt) {
        super.`§ 8-28 ledd 3 bokstav a`(oppfylt, grunnlagForSykepengegrunnlag)
    }

    override fun `§ 8-30 ledd 1`(grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Inntekt>, grunnlagForSykepengegrunnlag: Inntekt) {
        val beregnetMånedsinntektPerArbeidsgiver = grunnlagForSykepengegrunnlagPerArbeidsgiver
            .mapValues { it.value.reflection { _, månedlig, _, _ -> månedlig } }
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                versjon = LocalDate.of(2019, 1, 1),
                paragraf = Paragraf.PARAGRAF_8_30,
                ledd = Ledd.LEDD_1,
                input = mapOf(
                    "beregnetMånedsinntektPerArbeidsgiver" to beregnetMånedsinntektPerArbeidsgiver
                ),
                output = mapOf(
                    "grunnlagForSykepengegrunnlag" to grunnlagForSykepengegrunnlag.reflection { årlig, _, _, _ -> årlig }
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-30 ledd 2 punktum 1`(
        oppfylt: Boolean,
        maksimaltTillattAvvikPåÅrsinntekt: Prosent,
        grunnlagForSykepengegrunnlag: Inntekt,
        sammenligningsgrunnlag: Inntekt,
        avvik: Prosent
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                versjon = LocalDate.of(2017, 4, 5),
                paragraf = Paragraf.PARAGRAF_8_30,
                ledd = 2.ledd,
                punktum = 1.punktum,
                input = mapOf(
                    "maksimaltTillattAvvikPåÅrsinntekt" to maksimaltTillattAvvikPåÅrsinntekt.prosent(),
                    "grunnlagForSykepengegrunnlag" to grunnlagForSykepengegrunnlag.reflection { årlig, _, _, _ -> årlig },
                    "sammenligningsgrunnlag" to sammenligningsgrunnlag.reflection { årlig, _, _, _ -> årlig }
                ),
                output = mapOf(
                    "avvik" to avvik.prosent()
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-33 ledd 1`() {
        super.`§ 8-33 ledd 1`()
    }

    override fun `§ 8-33 ledd 3`(grunnlagForFeriepenger: Int, opptjeningsår: Year, prosentsats: Double, alder: Int, feriepenger: Double) {
        super.`§ 8-33 ledd 3`(grunnlagForFeriepenger, opptjeningsår, prosentsats, alder, feriepenger)
    }

    override fun `§ 8-51 ledd 2`(
        oppfylt: Boolean,
        skjæringstidspunkt: LocalDate,
        alderPåSkjæringstidspunkt: Int,
        grunnlagForSykepengegrunnlag: Inntekt,
        minimumInntekt: Inntekt
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                versjon = LocalDate.of(2011, 12, 16),
                paragraf = PARAGRAF_8_51,
                ledd = Ledd.LEDD_2,
                input = mapOf(
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "alderPåSkjæringstidspunkt" to alderPåSkjæringstidspunkt,
                    "grunnlagForSykepengegrunnlag" to grunnlagForSykepengegrunnlag.reflection { årlig, _, _, _ -> årlig },
                    "minimumInntekt" to minimumInntekt.reflection { årlig, _, _, _ -> årlig }
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-51 ledd 3`(
        periode: Periode,
        tidslinjegrunnlag: List<List<Map<String, Any>>>,
        beregnetTidslinje: List<Map<String, Any>>,
        gjenståendeSykedager: Int,
        forbrukteSykedager: Int,
        maksdato: LocalDate,
        startdatoSykepengerettighet: LocalDate
    ) {
        val (dagerOppfylt, dagerIkkeOppfylt) =
            periode
                .filter { it >= startdatoSykepengerettighet }
                .partition { it <= maksdato }

        fun logg(utfall: Utfall, utfallFom: LocalDate, utfallTom: LocalDate) {
            leggTil(
                EnkelSubsumsjon(
                    utfall = utfall,
                    versjon = LocalDate.of(2011, 12, 16),
                    paragraf = PARAGRAF_8_51,
                    ledd = Ledd.LEDD_3,
                    input = mapOf(
                        "fom" to periode.start,
                        "tom" to periode.endInclusive,
                        "utfallFom" to utfallFom,
                        "utfallTom" to utfallTom,
                        "tidslinjegrunnlag" to tidslinjegrunnlag,
                        "beregnetTidslinje" to beregnetTidslinje
                    ),
                    output = mapOf(
                        "gjenståendeSykedager" to gjenståendeSykedager,
                        "forbrukteSykedager" to forbrukteSykedager,
                        "maksdato" to maksdato,
                    ),
                    kontekster = kontekster()
                )
            )
        }
        if (dagerOppfylt.isNotEmpty()) logg(VILKAR_OPPFYLT, dagerOppfylt.first(), dagerOppfylt.last())
        if (dagerIkkeOppfylt.isNotEmpty()) logg(VILKAR_IKKE_OPPFYLT, dagerIkkeOppfylt.first(), dagerIkkeOppfylt.last())
    }

    internal fun subsumsjoner() = subsumsjoner.toList()

    fun events() = subsumsjoner.map(SubsumsjonEvent.Companion::fraSubsumsjon)

    data class SubsumsjonEvent(
        val id: UUID = UUID.randomUUID(),
        val sporing: Map<String, String>,
        val lovverk: String,
        val ikrafttredelse: String,
        val paragraf: String,
        val ledd: Int?,
        val punktum: Int?,
        val bokstav: Char?,
        val input: Map<String, Any>,
        val output: Map<String, Any>,
        val utfall: String,
    ) {

        companion object {

            private val paragrafVersjonFormaterer = DateTimeFormatter.ISO_DATE

            internal fun fraSubsumsjon(juridiskVurdering: Subsumsjon): SubsumsjonEvent {
                return object: SubsumsjonVisitor {
                    lateinit var event: SubsumsjonEvent

                    init {
                        juridiskVurdering.accept(this)
                    }

                    override fun preVisitSubsumsjon(
                        utfall: Utfall,
                        versjon: LocalDate,
                        paragraf: Paragraf,
                        ledd: Ledd,
                        punktum: Punktum?,
                        bokstav: Bokstav?,
                        input: Map<String, Any>,
                        output: Map<String, Any>,
                        kontekster: Map<String, String>
                    ) {
                        event = SubsumsjonEvent(
                            sporing = kontekster,
                            lovverk = "folketrygdloven",
                            ikrafttredelse = paragrafVersjonFormaterer.format(versjon),
                            paragraf = paragraf.ref,
                            ledd = ledd.nummer,
                            punktum = punktum?.toJson(), // Bryter encap?
                            bokstav = bokstav?.toJson(),
                            input = input,
                            output = output,
                            utfall = utfall.name
                        )
                    }
                }.event
            }
        }
    }
}
