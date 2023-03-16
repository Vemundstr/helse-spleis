package no.nav.helse.person.etterlevelse

import java.time.LocalDate
import no.nav.helse.etterlevelse.Bokstav
import no.nav.helse.etterlevelse.Bokstav.BOKSTAV_A
import no.nav.helse.etterlevelse.Bokstav.BOKSTAV_B
import no.nav.helse.etterlevelse.KontekstType
import no.nav.helse.etterlevelse.Ledd
import no.nav.helse.etterlevelse.Ledd.Companion.ledd
import no.nav.helse.etterlevelse.Paragraf
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_2
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_16
import no.nav.helse.etterlevelse.Punktum
import no.nav.helse.etterlevelse.Punktum.PUNKTUM_1
import no.nav.helse.etterlevelse.Punktum.PUNKTUM_2
import no.nav.helse.etterlevelse.Subsumsjon
import no.nav.helse.etterlevelse.Subsumsjon.Utfall
import no.nav.helse.etterlevelse.Subsumsjon.Utfall.VILKAR_IKKE_OPPFYLT
import no.nav.helse.etterlevelse.Subsumsjon.Utfall.VILKAR_OPPFYLT
import no.nav.helse.etterlevelse.SubsumsjonVisitor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class SubsumsjonTest {

    @Test
    fun testEquals() {
        val paragraf1 = testParagraf(
            VILKAR_OPPFYLT,
            "folketrygdloven",
            LocalDate.MIN,
            paragraf = PARAGRAF_2,
            ledd = 1.ledd,
            bokstav = BOKSTAV_A,
            punktum = PUNKTUM_1,
            input = mapOf("a" to "a"),
            output = mapOf("b" to "b")
        )
        val paragraf2 = testParagraf(
            VILKAR_OPPFYLT,
            "folketrygdloven",
            LocalDate.MIN,
            paragraf = PARAGRAF_2,
            ledd = 1.ledd,
            bokstav = BOKSTAV_A,
            punktum = PUNKTUM_1,
            input = mapOf("a" to "a"),
            output = mapOf("b" to "b")
        )
        assertEquals(paragraf1, paragraf2)
    }

    @Test
    fun testEqualsByRef() {
        val paragraf1 = testParagraf(VILKAR_OPPFYLT, "folketrygdloven", LocalDate.MIN, paragraf = PARAGRAF_2, ledd = 1.ledd)
        assertEquals(paragraf1, paragraf1)
    }

    @Test
    fun `ulik når input varierer`() {
        val paragraf1 = testParagraf(VILKAR_OPPFYLT, "folketrygdloven", LocalDate.MIN, paragraf = PARAGRAF_2, ledd = 1.ledd, input = mapOf("a" to "a"))
        val paragraf2 = testParagraf(VILKAR_OPPFYLT, "folketrygdloven", LocalDate.MIN, paragraf = PARAGRAF_2, ledd = 1.ledd, input = mapOf("b" to "b"))
        assertNotEquals(paragraf1, paragraf2)
    }

    @Test
    fun `ulik når output varierer`() {
        val paragraf1 = testParagraf(VILKAR_OPPFYLT, "folketrygdloven", LocalDate.MIN, paragraf = PARAGRAF_2, ledd = 1.ledd, output = mapOf("a" to "a"))
        val paragraf2 = testParagraf(VILKAR_OPPFYLT, "folketrygdloven", LocalDate.MIN, paragraf = PARAGRAF_2, ledd = 1.ledd, output = mapOf("b" to "b"))
        assertNotEquals(paragraf1, paragraf2)
    }

    @Test
    fun `ulik når oppfylt varierer`() {
        val paragraf1 = testParagraf(VILKAR_OPPFYLT, "folketrygdloven", LocalDate.MIN, paragraf = PARAGRAF_2, ledd = 1.ledd)
        val paragraf2 = testParagraf(VILKAR_IKKE_OPPFYLT, "folketrygdloven", LocalDate.MIN, paragraf = PARAGRAF_2, ledd = 1.ledd)
        assertNotEquals(paragraf1, paragraf2)
    }

    @Test
    fun `ulik når versjon varierer`() {
        val paragraf1 = testParagraf(VILKAR_OPPFYLT, "folketrygdloven", LocalDate.MIN, paragraf = PARAGRAF_2, ledd = 1.ledd)
        val paragraf2 = testParagraf(VILKAR_OPPFYLT, "folketrygdloven", LocalDate.MAX, paragraf = PARAGRAF_2, ledd = 1.ledd)
        assertNotEquals(paragraf1, paragraf2)
    }

    @Test
    fun `ulik når paragraf varierer`() {
        val paragraf1 = testParagraf(VILKAR_OPPFYLT, "folketrygdloven", LocalDate.MIN, paragraf = PARAGRAF_2, ledd = 1.ledd)
        val paragraf2 = testParagraf(VILKAR_OPPFYLT, "folketrygdloven", LocalDate.MIN, paragraf = PARAGRAF_8_16, ledd = 1.ledd)
        assertNotEquals(paragraf1, paragraf2)
    }

    @Test
    fun `ulik når ledd varierer`() {
        val paragraf1 = testParagraf(VILKAR_OPPFYLT, "folketrygdloven", LocalDate.MIN, paragraf = PARAGRAF_2, ledd = 1.ledd)
        val paragraf2 = testParagraf(VILKAR_OPPFYLT, "folketrygdloven", LocalDate.MIN, paragraf = PARAGRAF_2, ledd = 2.ledd)
        assertNotEquals(paragraf1, paragraf2)
    }

    @Test
    fun `ulik når bokstav varierer`() {
        val paragraf1 = testParagraf(VILKAR_OPPFYLT, "folketrygdloven", LocalDate.MIN, paragraf = PARAGRAF_2, ledd = 1.ledd, bokstav = BOKSTAV_A)
        val paragraf2 = testParagraf(VILKAR_OPPFYLT, "folketrygdloven", LocalDate.MIN, paragraf = PARAGRAF_2, ledd = 1.ledd, bokstav = BOKSTAV_B)
        assertNotEquals(paragraf1, paragraf2)
    }

    @Test
    fun `ulik når punktum varierer`() {
        val paragraf1 = testParagraf(VILKAR_OPPFYLT, "folketrygdloven", LocalDate.MIN, paragraf = PARAGRAF_2, ledd = 1.ledd, punktum = PUNKTUM_1)
        val paragraf2 = testParagraf(VILKAR_OPPFYLT, "folketrygdloven", LocalDate.MIN, paragraf = PARAGRAF_2, ledd = 1.ledd, punktum = PUNKTUM_2)
        assertNotEquals(paragraf1, paragraf2)
    }

    private fun testParagraf(
        utfall: Utfall,
        lovverk: String,
        versjon: LocalDate,
        paragraf: Paragraf,
        ledd: Ledd,
        bokstav: Bokstav? = null,
        punktum: Punktum? = null,
        input: Map<String, Any> = mapOf(),
        output: Map<String, Any> = mapOf(),
        kontekster: Map<String, KontekstType> = mapOf()
    ) = object : Subsumsjon() {
        override val lovverk = lovverk
        override val utfall = utfall
        override val versjon = versjon
        override val paragraf = paragraf
        override val ledd: Ledd = ledd
        override val bokstav: Bokstav? = bokstav
        override val punktum: Punktum? = punktum
        override val input = input
        override val output = output
        override val kontekster = kontekster

        override fun acceptSpesifikk(visitor: SubsumsjonVisitor) {}
    }
}
