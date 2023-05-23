package no.nav.helse.etterlevelse

import java.io.Serializable
import java.time.LocalDate
import java.time.Year
import java.time.format.DateTimeFormatter
import java.util.UUID
import no.nav.helse.Personidentifikator
import no.nav.helse.etterlevelse.Bokstav.BOKSTAV_A
import no.nav.helse.etterlevelse.Bokstav.BOKSTAV_B
import no.nav.helse.etterlevelse.Bokstav.BOKSTAV_C
import no.nav.helse.etterlevelse.Ledd.Companion.ledd
import no.nav.helse.etterlevelse.Ledd.LEDD_1
import no.nav.helse.etterlevelse.Ledd.LEDD_2
import no.nav.helse.etterlevelse.Ledd.LEDD_3
import no.nav.helse.etterlevelse.Ledd.LEDD_5
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_10
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_11
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_12
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_13
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_15
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_16
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_17
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_19
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_2
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_28
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_29
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_3
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_30
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_51
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_9
import no.nav.helse.etterlevelse.Punktum.Companion.punktum
import no.nav.helse.etterlevelse.Subsumsjon.Utfall
import no.nav.helse.etterlevelse.Subsumsjon.Utfall.VILKAR_BEREGNET
import no.nav.helse.etterlevelse.Subsumsjon.Utfall.VILKAR_IKKE_OPPFYLT
import no.nav.helse.etterlevelse.Subsumsjon.Utfall.VILKAR_OPPFYLT
import no.nav.helse.etterlevelse.Tidslinjedag.Companion.dager
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.januar
import no.nav.helse.juni
import no.nav.helse.person.DokumentType
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Prosent

class MaskinellJurist private constructor(
    private val parent: MaskinellJurist?,
    private val kontekster: Map<String, KontekstType>,
    vedtaksperiode: Periode? = null
) : SubsumsjonObserver {

    private val periode: () -> Periode

    init {
        // Når periode blir kalt av en subsumsjon skal vi være i kontekst av en vedtaksperiode.
        periode =  { checkNotNull(vedtaksperiode){"MaksinellJurist må være i kontekst av en vedtaksperiode for å registrere subsumsjonen"} }
    }

    private var subsumsjoner = listOf<Subsumsjon>()

    constructor() : this(null, emptyMap())

    private fun leggTil(subsumsjon: Subsumsjon) {
        subsumsjoner = subsumsjon.sammenstill(subsumsjoner)
        parent?.leggTil(subsumsjon)
    }

    private fun kontekster(): Map<String, KontekstType> = this.kontekster.toMap()

    fun medFødselsnummer(personidentifikator: Personidentifikator) =
        kopierMedKontekst(mapOf(personidentifikator.toString() to KontekstType.Fødselsnummer) + kontekster.filterNot { it.value == KontekstType.Fødselsnummer })

    fun medOrganisasjonsnummer(organisasjonsnummer: String) =
        kopierMedKontekst(mapOf(organisasjonsnummer to KontekstType.Organisasjonsnummer) + kontekster.filterNot { it.value == KontekstType.Organisasjonsnummer })

    fun medVedtaksperiode(vedtaksperiodeId: UUID, hendelseIder: Map<UUID, DokumentType>, periode: Periode) =
        kopierMedKontekst(
            mapOf(vedtaksperiodeId.toString() to KontekstType.Vedtaksperiode) + hendelseIder
                .map { it.key.toString() to it.value.tilKontekst() } + kontekster,
            periode
        )

    private fun kopierMedKontekst(kontekster: Map<String, KontekstType>, periode: Periode? = null) = MaskinellJurist(this, kontekster, periode)

    override fun `§ 8-2 ledd 1`(
        oppfylt: Boolean,
        skjæringstidspunkt: LocalDate,
        tilstrekkeligAntallOpptjeningsdager: Int,
        arbeidsforhold: List<Map<String, Any?>>,
        antallOpptjeningsdager: Int
    ) {
        leggTil(
            EnkelSubsumsjon(
                lovverk = "folketrygdloven",
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                versjon = LocalDate.of(2020, 6, 12),
                paragraf = PARAGRAF_8_2,
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
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2011, 12, 16),
                paragraf = PARAGRAF_8_3,
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

    override fun `§ 8-3 ledd 2 punktum 1`(oppfylt: Boolean, skjæringstidspunkt: LocalDate, beregningsgrunnlag: Inntekt, minimumInntekt: Inntekt) {
        leggTil(
            EnkelSubsumsjon(
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2011, 12, 16),
                paragraf = PARAGRAF_8_3,
                ledd = 2.ledd,
                punktum = 1.punktum,
                input = mapOf(
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "grunnlagForSykepengegrunnlag" to beregningsgrunnlag.reflection { årlig, _, _, _ -> årlig },
                    "minimumInntekt" to minimumInntekt.reflection { årlig, _, _, _ -> årlig }
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-9 ledd 1`(oppfylt: Boolean, utlandsperiode: Periode, søknadsperioder: List<Map<String, Serializable>>) {
        utlandsperiode.forEach {
            leggTil(
                GrupperbarSubsumsjon(
                    dato = it,
                    lovverk = "folketrygdloven",
                    utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                    versjon = 1.juni(2021),
                    paragraf = PARAGRAF_8_9,
                    ledd = LEDD_1,
                    input = mapOf( "soknadsPerioder" to søknadsperioder),
                    output = emptyMap(),
                    kontekster = kontekster()
                )
            )
        }
    }

    override fun `§ 8-10 ledd 2 punktum 1`(
        erBegrenset: Boolean,
        maksimaltSykepengegrunnlag: Inntekt,
        skjæringstidspunkt: LocalDate,
        beregningsgrunnlag: Inntekt
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2020, 1, 1),
                paragraf = PARAGRAF_8_10,
                ledd = 2.ledd,
                punktum = 1.punktum,
                input = mapOf(
                    "maksimaltSykepengegrunnlag" to maksimaltSykepengegrunnlag.reflection { årlig, _, _, _ -> årlig },
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "grunnlagForSykepengegrunnlag" to beregningsgrunnlag.reflection { årlig, _, _, _ -> årlig }
                ),
                output = mapOf(
                    "erBegrenset" to erBegrenset
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-10 ledd 3`(årsinntekt: Double, inntektOmregnetTilDaglig: Double) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = 1.januar(2020),
                paragraf = PARAGRAF_8_10,
                ledd = 3.ledd,
                input = mapOf("årligInntekt" to årsinntekt),
                output = mapOf("dagligInntekt" to inntektOmregnetTilDaglig),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-11 ledd 1`(dato: LocalDate) {
        leggTil(
            GrupperbarSubsumsjon(
                dato = dato,
                lovverk = "folketrygdloven",
                paragraf = PARAGRAF_8_11,
                ledd = 1.ledd,
                utfall = VILKAR_IKKE_OPPFYLT,
                versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
                input = mapOf("periode" to mapOf( "fom" to periode().start, "tom" to periode().endInclusive)),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-12 ledd 1 punktum 1`(
        periode: Periode,
        tidslinjegrunnlag: List<List<Tidslinjedag>>,
        beregnetTidslinje: List<Tidslinjedag>,
        gjenståendeSykedager: Int,
        forbrukteSykedager: Int,
        maksdato: LocalDate,
        startdatoSykepengerettighet: LocalDate
    ) {
        val (dagerOppfylt, dagerIkkeOppfylt) =
            periode
                .filter { it >= startdatoSykepengerettighet }
                .sorted()
                .partition { it <= maksdato }

        fun logg(utfall: Utfall, utfallFom: LocalDate, utfallTom: LocalDate) {
            leggTil(
                EnkelSubsumsjon(
                    utfall = utfall,
                    lovverk = "folketrygdloven",
                    versjon = LocalDate.of(2021, 5, 21),
                    paragraf = PARAGRAF_8_12,
                    ledd = 1.ledd,
                    punktum = 1.punktum,
                    input = mapOf(
                        "fom" to periode.start,
                        "tom" to periode.endInclusive,
                        "utfallFom" to utfallFom,
                        "utfallTom" to utfallTom,
                        "tidslinjegrunnlag" to tidslinjegrunnlag.map { it.dager(periode) },
                        "beregnetTidslinje" to beregnetTidslinje.dager(periode)
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
        tidslinjegrunnlag: List<List<Tidslinjedag>>,
        beregnetTidslinje: List<Tidslinjedag>
    ) {
        leggTil(
            BetingetSubsumsjon(
                funnetRelevant = oppfylt || gjenståendeSykepengedager == 0, // Bare relevant om det er ny rett på sykepenger eller om vilkåret ikke er oppfylt
                lovverk = "folketrygdloven",
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                versjon = LocalDate.of(2021, 5, 21),
                paragraf = PARAGRAF_8_12,
                ledd = 2.ledd,
                punktum = null,
                bokstav = null,
                input = mapOf(
                    "dato" to dato,
                    "tilstrekkeligOppholdISykedager" to tilstrekkeligOppholdISykedager,
                    "tidslinjegrunnlag" to tidslinjegrunnlag.map { it.dager() },
                    "beregnetTidslinje" to beregnetTidslinje.dager()
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-13 ledd 1`(periode: Periode, avvisteDager: List<LocalDate>, tidslinjer: List<List<Tidslinjedag>>) {
        fun periode() = periode.toList() - avvisteDager

        fun logg(utfall: Utfall, dager: List<LocalDate>) {
            dager.forEach { dagen ->
                leggTil(
                    GrupperbarSubsumsjon(
                        dato = dagen,
                        lovverk = "folketrygdloven",
                        utfall = utfall,
                        paragraf = PARAGRAF_8_13,
                        ledd = LEDD_1,
                        versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
                        input = mapOf(
                            "tidslinjegrunnlag" to tidslinjer.map { it.dager(periode) }
                        ),
                        output = emptyMap(),
                        kontekster = kontekster()
                    )
                )
            }
        }

        val oppfylteDager = periode()

        if (oppfylteDager.isNotEmpty()) logg(VILKAR_OPPFYLT, oppfylteDager)
        if (avvisteDager.isNotEmpty()) logg(VILKAR_IKKE_OPPFYLT, avvisteDager)
    }

    override fun `§ 8-13 ledd 2`(
        periode: Periode,
        tidslinjer: List<List<Tidslinjedag>>,
        grense: Double,
        dagerUnderGrensen: Set<LocalDate>
    ) {
        periode.forEach { dagen ->
            leggTil(
                GrupperbarSubsumsjon(
                    dato = dagen,
                    lovverk = "folketrygdloven",
                    utfall = VILKAR_BEREGNET,
                    paragraf = PARAGRAF_8_13,
                    ledd = LEDD_2,
                    versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
                    input = mapOf(
                        "tidslinjegrunnlag" to tidslinjer.map { it.dager(periode) },
                        "grense" to grense
                    ),
                    output = mapOf(
                        "dagerUnderGrensen" to dagerUnderGrensen.grupperSammenhengendePerioder().map {
                            mapOf(
                                "fom" to it.start,
                                "tom" to it.endInclusive
                            )
                        }
                    ),
                    kontekster = kontekster()
                )
            )
        }
    }

    override fun `§ 8-15`(
        skjæringstidspunkt: LocalDate,
        organisasjonsnummer: String,
        inntekterSisteTreMåneder: List<Map<String, Any>>,
        forklaring: String,
        oppfylt: Boolean
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(1998, 12, 18),
                paragraf = PARAGRAF_8_15,
                ledd = null,
                punktum = null,
                bokstav = null,
                input = mapOf(
                    "organisasjonsnummer" to organisasjonsnummer,
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "inntekterSisteTreMåneder" to inntekterSisteTreMåneder,
                    "forklaring" to forklaring
                ),
                kontekster = kontekster(),
                output = if (oppfylt) {
                    mapOf("arbeidsforholdAvbrutt" to organisasjonsnummer)
                } else {
                    mapOf("aktivtArbeidsforhold" to organisasjonsnummer)
                }
            )
        )
    }

    override fun `§ 8-16 ledd 1`(dato: LocalDate, dekningsgrad: Double, inntekt: Double, dekningsgrunnlag: Double) {
        leggTil(
            GrupperbarSubsumsjon(
                dato = dato,
                lovverk = "folketrygdloven",
                input = mapOf("dekningsgrad" to dekningsgrad, "inntekt" to inntekt),
                output = mapOf("dekningsgrunnlag" to dekningsgrunnlag),
                utfall = VILKAR_BEREGNET,
                paragraf = PARAGRAF_8_16,
                ledd = 1.ledd,
                versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-17 ledd 1 bokstav a`(
        oppfylt: Boolean,
        dagen: LocalDate,
        sykdomstidslinje: List<Tidslinjedag>
    ) {
        leggTil(
            GrupperbarSubsumsjon(
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2018, 1, 1),
                dato = dagen,
                paragraf = PARAGRAF_8_17,
                ledd = 1.ledd,
                bokstav = BOKSTAV_A,
                input = mapOf("sykdomstidslinje" to sykdomstidslinje.dager(periode())),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-17 ledd 1`(dato: LocalDate) {
        leggTil(
            GrupperbarSubsumsjon(
                dato = dato,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2018, 1, 1),
                utfall = VILKAR_OPPFYLT,
                paragraf = PARAGRAF_8_17,
                ledd = LEDD_1,
                input = emptyMap(),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-17 ledd 1 bokstav a - arbeidsgiversøknad`(
        periode: Iterable<LocalDate>,
        sykdomstidslinje: List<Tidslinjedag>
    ) {
        periode.forEach {
            `§ 8-17 ledd 1 bokstav a`(false, it, sykdomstidslinje)
        }
    }

    override fun `§ 8-17 ledd 2`(dato: LocalDate, sykdomstidslinje: List<Tidslinjedag>) {
        leggTil(
            GrupperbarSubsumsjon(
                dato = dato,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2018, 1, 1),
                utfall = VILKAR_IKKE_OPPFYLT,
                paragraf = PARAGRAF_8_17,
                ledd = LEDD_2,
                input = mapOf(
                    "beregnetTidslinje" to sykdomstidslinje.dager(periode())
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-19 første ledd`(dato: LocalDate, beregnetTidslinje: List<Tidslinjedag>) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = 1.januar(2001),
                paragraf = PARAGRAF_8_19,
                ledd = 1.ledd,
                input = mapOf(
                    "beregnetTidslinje" to beregnetTidslinje.dager()
                ),
                output = mapOf(
                    "sisteDagIArbeidsgiverperioden" to dato
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-19 andre ledd`(dato: LocalDate, beregnetTidslinje: List<Tidslinjedag>) {
        leggTil(
            GrupperbarSubsumsjon(
                dato = dato,
                lovverk = "folketrygdloven",
                utfall = VILKAR_BEREGNET,
                versjon = 1.januar(2001),
                paragraf = PARAGRAF_8_19,
                ledd = 2.ledd,
                input = mapOf(
                    "beregnetTidslinje" to beregnetTidslinje.dager()
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-19 tredje ledd`(dato: LocalDate, beregnetTidslinje: List<Tidslinjedag>) {
        leggTil(
            GrupperbarSubsumsjon(
                dato = dato,
                lovverk = "folketrygdloven",
                utfall = VILKAR_BEREGNET,
                versjon = 1.januar(2001),
                paragraf = PARAGRAF_8_19,
                ledd = 3.ledd,
                input = mapOf(
                    "beregnetTidslinje" to beregnetTidslinje.dager()
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-19 fjerde ledd`(dato: LocalDate, beregnetTidslinje: List<Tidslinjedag>) {
        leggTil(
            GrupperbarSubsumsjon(
                dato = dato,
                lovverk = "folketrygdloven",
                utfall = VILKAR_BEREGNET,
                versjon = 1.januar(2001),
                paragraf = PARAGRAF_8_19,
                ledd = 4.ledd,
                input = mapOf(
                    "beregnetTidslinje" to beregnetTidslinje.dager()
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-28 ledd 3 bokstav a`(
        organisasjonsnummer: String,
        inntekterSisteTreMåneder: List<Map<String, Any>>,
        grunnlagForSykepengegrunnlag: Inntekt,
        skjæringstidspunkt: LocalDate
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2019, 1, 1),
                paragraf = PARAGRAF_8_28,
                ledd = LEDD_3,
                bokstav = BOKSTAV_A,
                input = mapOf(
                    "organisasjonsnummer" to organisasjonsnummer,
                    "inntekterSisteTreMåneder" to inntekterSisteTreMåneder,
                    "skjæringstidspunkt" to skjæringstidspunkt
                ),
                output = mapOf(
                    "beregnetGrunnlagForSykepengegrunnlagPrÅr" to grunnlagForSykepengegrunnlag.reflection { årlig, _, _, _ -> årlig },
                    "beregnetGrunnlagForSykepengegrunnlagPrMåned" to grunnlagForSykepengegrunnlag.reflection { _, månedlig, _, _ -> månedlig }
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-28 ledd 3 bokstav b`(
        organisasjonsnummer: String,
        startdatoArbeidsforhold: LocalDate,
        overstyrtInntektFraSaksbehandler: Map<String, Any>,
        skjæringstidspunkt: LocalDate,
        forklaring: String,
        grunnlagForSykepengegrunnlag: Inntekt
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2019, 1, 1),
                paragraf = PARAGRAF_8_28,
                ledd = LEDD_3,
                bokstav = BOKSTAV_B,
                input = mapOf(
                    "organisasjonsnummer" to organisasjonsnummer,
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "startdatoArbeidsforhold" to startdatoArbeidsforhold,
                    "overstyrtInntektFraSaksbehandler" to overstyrtInntektFraSaksbehandler,
                    "forklaring" to forklaring
                ),
                output = mapOf(
                    "beregnetGrunnlagForSykepengegrunnlagPrÅr" to grunnlagForSykepengegrunnlag.reflection { årlig, _, _, _ -> årlig },
                    "beregnetGrunnlagForSykepengegrunnlagPrMåned" to grunnlagForSykepengegrunnlag.reflection { _, månedlig, _, _ -> månedlig }
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-28 ledd 3 bokstav c`(
        organisasjonsnummer: String,
        overstyrtInntektFraSaksbehandler: Map<String, Any>,
        skjæringstidspunkt: LocalDate,
        forklaring: String,
        grunnlagForSykepengegrunnlag: Inntekt
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2019, 1, 1),
                paragraf = PARAGRAF_8_28,
                ledd = LEDD_3,
                bokstav = BOKSTAV_C,
                input = mapOf(
                    "organisasjonsnummer" to organisasjonsnummer,
                    "overstyrtInntektFraSaksbehandler" to overstyrtInntektFraSaksbehandler,
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "forklaring" to forklaring
                ),
                output = mapOf(
                    "beregnetGrunnlagForSykepengegrunnlagPrÅr" to grunnlagForSykepengegrunnlag.reflection { årlig, _, _, _ -> årlig },
                    "beregnetGrunnlagForSykepengegrunnlagPrMåned" to grunnlagForSykepengegrunnlag.reflection { _, månedlig, _, _ -> månedlig }
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-28 ledd 5`(
        organisasjonsnummer: String,
        overstyrtInntektFraSaksbehandler: Map<String, Any>,
        skjæringstidspunkt: LocalDate,
        forklaring: String,
        grunnlagForSykepengegrunnlag: Inntekt
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2019, 1, 1),
                paragraf = PARAGRAF_8_28,
                ledd = LEDD_5,
                input = mapOf(
                    "organisasjonsnummer" to organisasjonsnummer,
                    "overstyrtInntektFraSaksbehandler" to overstyrtInntektFraSaksbehandler,
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "forklaring" to forklaring
                ),
                output = mapOf(
                    "beregnetGrunnlagForSykepengegrunnlagPrÅr" to grunnlagForSykepengegrunnlag.reflection { årlig, _, _, _ -> årlig },
                    "beregnetGrunnlagForSykepengegrunnlagPrMåned" to grunnlagForSykepengegrunnlag.reflection { _, månedlig, _, _ -> månedlig }
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-29`(
        skjæringstidspunkt: LocalDate,
        grunnlagForSykepengegrunnlag: Inntekt,
        inntektsopplysninger: List<Map<String, Any>>,
        organisasjonsnummer: String
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2019, 1, 1),
                paragraf = PARAGRAF_8_29,
                ledd = null,
                input = mapOf(
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "organisasjonsnummer" to organisasjonsnummer,
                    "inntektsopplysninger" to inntektsopplysninger
                ),
                output = mapOf(
                    "grunnlagForSykepengegrunnlag" to grunnlagForSykepengegrunnlag.reflection { årlig, _, _, _ -> årlig }
                ),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-30 ledd 1`(grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Inntekt>, grunnlagForSykepengegrunnlag: Inntekt) {
        val beregnetMånedsinntektPerArbeidsgiver = grunnlagForSykepengegrunnlagPerArbeidsgiver
            .mapValues { it.value.reflection { _, månedlig, _, _ -> månedlig } }
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2019, 1, 1),
                paragraf = PARAGRAF_8_30,
                ledd = LEDD_1,
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
        maksimaltTillattAvvikPåÅrsinntekt: Prosent,
        grunnlagForSykepengegrunnlag: Inntekt,
        sammenligningsgrunnlag: Inntekt,
        avvik: Prosent
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2017, 4, 5),
                paragraf = PARAGRAF_8_30,
                ledd = 2.ledd,
                punktum = 1.punktum,
                input = mapOf(
                    "maksimaltTillattAvvikPåÅrsinntekt" to maksimaltTillattAvvikPåÅrsinntekt.prosent(),
                    "grunnlagForSykepengegrunnlag" to grunnlagForSykepengegrunnlag.reflection { årlig, _, _, _ -> årlig },
                    "sammenligningsgrunnlag" to sammenligningsgrunnlag.reflection { årlig, _, _, _ -> årlig }
                ),
                output = mapOf("avviksprosent" to avvik.prosent()),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-30 ledd 2`(skjæringstidspunkt: LocalDate, sammenligningsgrunnlag: SubsumsjonObserver.SammenligningsgrunnlagDTO) {
        leggTil(
            EnkelSubsumsjon(
                utfall = VILKAR_BEREGNET,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2019, 1, 1),
                paragraf = PARAGRAF_8_30,
                ledd = LEDD_2,
                input = mapOf(
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "inntekterFraAOrdningen" to sammenligningsgrunnlag.inntekterFraAOrdningen
                ),
                output = mapOf(
                    "sammenligningsgrunnlag" to sammenligningsgrunnlag.sammenligningsgrunnlag
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
        beregningsgrunnlag: Inntekt,
        minimumInntekt: Inntekt
    ) {
        leggTil(
            EnkelSubsumsjon(
                utfall = if (oppfylt) VILKAR_OPPFYLT else VILKAR_IKKE_OPPFYLT,
                lovverk = "folketrygdloven",
                versjon = LocalDate.of(2011, 12, 16),
                paragraf = PARAGRAF_8_51,
                ledd = LEDD_2,
                input = mapOf(
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "alderPåSkjæringstidspunkt" to alderPåSkjæringstidspunkt,
                    "grunnlagForSykepengegrunnlag" to beregningsgrunnlag.reflection { årlig, _, _, _ -> årlig },
                    "minimumInntekt" to minimumInntekt.reflection { årlig, _, _, _ -> årlig }
                ),
                output = emptyMap(),
                kontekster = kontekster()
            )
        )
    }

    override fun `§ 8-51 ledd 3`(
        periode: Periode,
        tidslinjegrunnlag: List<List<Tidslinjedag>>,
        beregnetTidslinje: List<Tidslinjedag>,
        gjenståendeSykedager: Int,
        forbrukteSykedager: Int,
        maksdato: LocalDate,
        startdatoSykepengerettighet: LocalDate
    ) {
        val (dagerOppfylt, dagerIkkeOppfylt) =
            periode
                .filter { it >= startdatoSykepengerettighet }
                .sorted()
                .partition { it <= maksdato }

        fun logg(utfall: Utfall, utfallFom: LocalDate, utfallTom: LocalDate) {
            leggTil(
                EnkelSubsumsjon(
                    utfall = utfall,
                    versjon = LocalDate.of(2011, 12, 16),
                    lovverk = "folketrygdloven",
                    paragraf = PARAGRAF_8_51,
                    ledd = LEDD_3,
                    input = mapOf(
                        "fom" to periode.start,
                        "tom" to periode.endInclusive,
                        "utfallFom" to utfallFom,
                        "utfallTom" to utfallTom,
                        "tidslinjegrunnlag" to tidslinjegrunnlag.map { it.dager() },
                        "beregnetTidslinje" to beregnetTidslinje.dager()
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

    override fun `§ 22-13 ledd 3`(avskjæringsdato: LocalDate, perioder: List<Periode>) {
        leggTil(EnkelSubsumsjon(
            utfall = VILKAR_IKKE_OPPFYLT,
            lovverk = "folketrygdloven",
            versjon = LocalDate.of(2011, 12, 16),
            paragraf = Paragraf.PARAGRAF_22_13,
            ledd = LEDD_3,
            input = mapOf(
                "avskjæringsdato" to avskjæringsdato
            ),
            output = mapOf(
                "perioder" to perioder.map {
                    mapOf(
                        "fom" to it.start,
                        "tom" to it.endInclusive
                    )
                }
            ),
            kontekster = kontekster()
        ))
    }

    override fun `fvl § 35 ledd 1`() {
        leggTil(EnkelSubsumsjon(
            utfall = VILKAR_OPPFYLT,
            lovverk = "forvaltningsloven",
            versjon = LocalDate.of(2021, 6, 1),
            paragraf = Paragraf.PARAGRAF_35,
            ledd = LEDD_1,
            input = emptyMap(),
            output = emptyMap(),
            kontekster = kontekster()
        ))
    }

    fun subsumsjoner() = subsumsjoner.toList()

    fun events() = subsumsjoner.map(SubsumsjonEvent.Companion::fraSubsumsjon)

    data class SubsumsjonEvent(
        val id: UUID = UUID.randomUUID(),
        val sporing: Map<KontekstType, List<String>>,
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

            internal fun fraSubsumsjon(subsumsjon: Subsumsjon): SubsumsjonEvent {
                return object : SubsumsjonVisitor {
                    lateinit var event: SubsumsjonEvent

                    init {
                        subsumsjon.accept(this)
                    }

                    override fun preVisitSubsumsjon(
                        utfall: Utfall,
                        lovverk: String,
                        versjon: LocalDate,
                        paragraf: Paragraf,
                        ledd: Ledd?,
                        punktum: Punktum?,
                        bokstav: Bokstav?,
                        input: Map<String, Any>,
                        output: Map<String, Any>,
                        kontekster: Map<String, KontekstType>
                    ) {
                        event = SubsumsjonEvent(
                            sporing = kontekster.toMutableMap()
                                .filterNot { it.value == KontekstType.Fødselsnummer }
                                .toList()
                                .fold(mutableMapOf()) { acc, kontekst ->
                                    acc.compute(kontekst.second) { _, value ->
                                        value?.plus(
                                            kontekst.first
                                        ) ?: mutableListOf(kontekst.first)
                                    }
                                    acc
                                },
                            lovverk = lovverk,
                            ikrafttredelse = paragrafVersjonFormaterer.format(versjon),
                            paragraf = paragraf.ref,
                            ledd = ledd?.nummer,
                            punktum = punktum?.toJson(),
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

    private fun DokumentType.tilKontekst() = when (this) {
        DokumentType.Sykmelding -> KontekstType.Sykmelding
        DokumentType.Søknad -> KontekstType.Søknad
        DokumentType.InntektsmeldingDager -> KontekstType.Inntektsmelding
        DokumentType.InntektsmeldingInntekt -> KontekstType.Inntektsmelding
        DokumentType.OverstyrTidslinje -> KontekstType.OverstyrTidslinje
        DokumentType.OverstyrInntekt -> KontekstType.OverstyrInntekt
        DokumentType.OverstyrRefusjon -> KontekstType.OverstyrRefusjon
        DokumentType.OverstyrArbeidsgiveropplysninger -> KontekstType.OverstyrArbeidsgiveropplysninger
        DokumentType.OverstyrArbeidsforhold -> KontekstType.OverstyrArbeidsforhold
        DokumentType.SkjønnsmessigFastsettelse -> KontekstType.SkjønnsmessigFastsettelse
    }

}

