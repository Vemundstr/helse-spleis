package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.Arbeidsforhold
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.person.*
import no.nav.helse.person.Ledd.LEDD_1
import no.nav.helse.person.Paragraf.PARAGRAF_8_12
import no.nav.helse.person.Paragraf.PARAGRAF_8_2
import no.nav.helse.person.Punktum.Companion.punktum
import no.nav.helse.testhelpers.desember
import no.nav.helse.testhelpers.januar
import no.nav.helse.testhelpers.juni
import no.nav.helse.testhelpers.mai
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class EtterlevelseTest : AbstractEndToEndTest() {

    @Test
    fun `§8-2 ledd 1 - opptjeningstid tilfredstilt`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser()
        val arbeidsforhold = listOf(Arbeidsforhold(ORGNUMMER, 4.desember(2017), 31.januar))
        håndterVilkårsgrunnlag(arbeidsforhold = arbeidsforhold)

        assertOppfylt(
            paragraf = PARAGRAF_8_2,
            ledd = LEDD_1,
            punktum = 1.punktum,
            versjon = 12.juni(2020),
            inputdata = mapOf(
                "skjæringstidspunkt" to 1.januar,
                "tilstrekkeligAntallOpptjeningsdager" to 28,
                "arbeidsforhold" to listOf(
                    mapOf(
                        "orgnummer" to ORGNUMMER,
                        "fom" to 4.desember(2017),
                        "tom" to 31.januar
                    )
                )
            ),
            outputdata = mapOf("antallOpptjeningsdager" to 28)
        )
    }

    @Test
    fun `§8-2 ledd 1 - opptjeningstid ikke tilfredstilt`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser()
        val arbeidsforhold = listOf(Arbeidsforhold(ORGNUMMER, 5.desember(2017), 31.januar))
        håndterVilkårsgrunnlag(arbeidsforhold = arbeidsforhold)

        assertIkkeOppfylt(
            paragraf = PARAGRAF_8_2,
            ledd = LEDD_1,
            punktum = 1.punktum,
            versjon = 12.juni(2020),
            inputdata = mapOf(
                "skjæringstidspunkt" to 1.januar,
                "tilstrekkeligAntallOpptjeningsdager" to 28,
                "arbeidsforhold" to listOf(
                    mapOf(
                        "orgnummer" to ORGNUMMER,
                        "fom" to 5.desember(2017),
                        "tom" to 31.januar
                    )
                )
            ),
            outputdata = mapOf("antallOpptjeningsdager" to 27)
        )
    }

    @Test
    fun `§8-12 ledd 1 - Brukt færre enn 248 dager`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 50.prosent))
        håndterSøknadMedValidering(1.vedtaksperiode, Sykdom(3.januar, 26.januar, 50.prosent, 50.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(3.januar, 18.januar)))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)

        assertOppfylt(
            paragraf = PARAGRAF_8_12,
            ledd = LEDD_1,
            punktum = 1.punktum,
            versjon = 21.mai(2021),
            inputdata = mapOf(
                "fom" to 19.januar,
                "tom" to 26.januar,
                "tidslinjegrunnlag" to listOf(listOf(mapOf("fom" to 19.januar, "tom" to 26.januar, "dagtype" to "NAVDAG")), emptyList()),
                "beregnetTidslinje" to listOf(mapOf("fom" to 19.januar, "tom" to 26.januar, "dagtype" to "NAVDAG"))
            ),
            outputdata = mapOf(
                "gjenståendeSykedager" to 242,
                "forbrukteSykedager" to 6,
                "maksdato" to 1.januar(2019),
                "avvisteDager" to emptyList<Periode>()
            )
        )
    }

    @Test
    fun `§8-12 ledd 1 - Brukt flere enn 248 dager`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar(2018), 11.januar(2019), 50.prosent))
        håndterSøknadMedValidering(
            1.vedtaksperiode,
            Sykdom(3.januar(2018), 11.januar(2019), 50.prosent, 50.prosent),
            sendtTilNav = 3.januar(2018)
        )
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(3.januar(2018), 18.januar(2018))))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)

        assertOppfylt(
            resultatvelger = 1.resultat,
            paragraf = PARAGRAF_8_12,
            ledd = LEDD_1,
            punktum = 1.punktum,
            versjon = 21.mai(2021),
            inputdata = mapOf(
                "fom" to 19.januar,
                "tom" to 1.januar(2019),
                "tidslinjegrunnlag" to listOf(listOf(mapOf("fom" to 19.januar, "tom" to 11.januar(2019), "dagtype" to "NAVDAG")), emptyList()),
                "beregnetTidslinje" to listOf(mapOf("fom" to 19.januar, "tom" to 11.januar(2019), "dagtype" to "NAVDAG"))
            ),
            outputdata = mapOf(
                "gjenståendeSykedager" to 0,
                "forbrukteSykedager" to 248,
                "maksdato" to 1.januar(2019),
                "avvisteDager" to emptyList<Periode>()
            )
        )

        assertIkkeOppfylt(
            resultatvelger = 2.resultat,
            paragraf = PARAGRAF_8_12,
            ledd = LEDD_1,
            punktum = 1.punktum,
            versjon = 21.mai(2021),
            inputdata = mapOf(
                "fom" to 2.januar(2019),
                "tom" to 11.januar(2019),
                "tidslinjegrunnlag" to listOf(listOf(mapOf("fom" to 19.januar, "tom" to 11.januar(2019), "dagtype" to "NAVDAG")), emptyList()),
                "beregnetTidslinje" to listOf(mapOf("fom" to 19.januar, "tom" to 11.januar(2019), "dagtype" to "NAVDAG"))
            ),
            outputdata = mapOf(
                "gjenståendeSykedager" to 0,
                "forbrukteSykedager" to 248,
                "maksdato" to 1.januar(2019),
                "avvisteDager" to listOf(2.januar(2019) til 4.januar(2019), 7.januar(2019) til 11.januar(2019))
            )
        )
    }

    private val Int.resultat: (List<Resultat>) -> Resultat get() = { it[this - 1] }

    private fun assertOppfylt(
        paragraf: Paragraf,
        ledd: Ledd,
        punktum: List<Punktum>,
        versjon: LocalDate,
        inputdata: Map<Any, Any?>,
        outputdata: Map<Any, Any?>,
        resultatvelger: (List<Resultat>) -> Resultat = { it.single() },
        inspektør: EtterlevelseInspektør = EtterlevelseInspektør(person.aktivitetslogg)
    ) {
        val resultat = inspektør.resultat(paragraf, ledd, punktum).let(resultatvelger)
        assertTrue(resultat.oppfylt)
        assertResultat(versjon, inputdata, outputdata, resultat)
    }

    private fun assertIkkeOppfylt(
        paragraf: Paragraf,
        ledd: Ledd,
        punktum: List<Punktum>,
        versjon: LocalDate,
        inputdata: Map<Any, Any?>,
        outputdata: Map<Any, Any?>,
        resultatvelger: (List<Resultat>) -> Resultat = { it.single() },
        inspektør: EtterlevelseInspektør = EtterlevelseInspektør(person.aktivitetslogg)
    ) {
        val resultat = inspektør.resultat(paragraf, ledd, punktum).let(resultatvelger)
        assertFalse(resultat.oppfylt)
        assertResultat(versjon, inputdata, outputdata, resultat)
    }

    private fun assertResultat(
        versjon: LocalDate,
        inputdata: Map<Any, Any?>,
        outputdata: Map<Any, Any?>,
        resultat: Resultat
    ) {
        assertEquals(versjon, resultat.versjon)
        assertEquals(inputdata, resultat.inputdata)
        assertEquals(outputdata, resultat.outputdata)
    }

    private class EtterlevelseInspektør(aktivitetslogg: Aktivitetslogg) : AktivitetsloggVisitor {
        private val resultater = mutableListOf<Resultat>()

        fun resultat(paragraf: Paragraf, ledd: Ledd, punktum: List<Punktum>) =
            resultater.filter { it.paragraf == paragraf && it.ledd == ledd && it.punktum == punktum }

        init {
            aktivitetslogg.accept(this)
        }

        private lateinit var melding: String

        override fun preVisitEtterlevelse(
            kontekster: List<SpesifikkKontekst>,
            aktivitet: Aktivitetslogg.Aktivitet.Etterlevelse,
            melding: String,
            vurderingsresultat: Aktivitetslogg.Aktivitet.Etterlevelse.Vurderingsresultat,
            tidsstempel: String
        ) {
            this.melding = melding
        }

        override fun visitVurderingsresultat(
            oppfylt: Boolean,
            versjon: LocalDate,
            paragraf: Paragraf,
            ledd: Ledd,
            punktum: List<Punktum>,
            inputdata: Map<Any, Any?>,
            outputdata: Map<Any, Any?>
        ) {
            resultater.add(Resultat(melding, oppfylt, versjon, paragraf, ledd, punktum, inputdata, outputdata))
        }
    }

    private class Resultat(
        val melding: String,
        val oppfylt: Boolean,
        val versjon: LocalDate,
        val paragraf: Paragraf,
        val ledd: Ledd,
        val punktum: List<Punktum>,
        val inputdata: Map<Any, Any?>,
        val outputdata: Map<Any, Any?>
    )
}
