package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import no.nav.helse.Alder
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.SubsumsjonInspektør
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.november
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.etterlevelse.Ledd.LEDD_1
import no.nav.helse.etterlevelse.Paragraf.PARAGRAF_8_3
import no.nav.helse.etterlevelse.Punktum.Companion.punktum
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.plus
import no.nav.helse.ukedager
import no.nav.helse.Alder.Companion.alder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AlderTest {

    private companion object {
        val FYLLER_67_ÅR_1_JANUAR_2018 = 1.januar(1951).alder
        val FYLLER_18_ÅR_2_NOVEMBER_2018 = 2.november(2000).alder
        val FYLLER_70_ÅR_10_JANUAR_2018 = 10.januar(1948).alder
        val FYLLER_70_ÅR_13_JANUAR_2018 = 13.januar(1948).alder
        val FYLLER_70_ÅR_14_JANUAR_2018 = 14.januar(1948).alder
        val FYLLER_70_ÅR_15_JANUAR_2018 = 15.januar(1948).alder
    }

    @Test
    fun `alder på gitt dato`() {
        val alder = 12.februar(1992).alder
        assertEquals(25, alder.alderPåDato(11.februar))
        assertEquals(26, alder.alderPåDato(12.februar))
    }

    @Test
    fun `mindre enn 70`() {
        assertTrue(FYLLER_70_ÅR_10_JANUAR_2018.innenfor70årsgrense(9.januar))
        assertFalse(FYLLER_70_ÅR_10_JANUAR_2018.innenfor70årsgrense(10.januar))
        assertFalse(FYLLER_70_ÅR_10_JANUAR_2018.innenfor70årsgrense(11.januar))
        assertTrue(FYLLER_70_ÅR_13_JANUAR_2018.innenfor70årsgrense(12.januar))
        assertTrue(FYLLER_70_ÅR_14_JANUAR_2018.innenfor70årsgrense(12.januar))
        assertTrue(FYLLER_70_ÅR_15_JANUAR_2018.innenfor70årsgrense(12.januar))
    }

    @Test
    fun `utbetaling skal stoppes selv om man reelt sett er 69 år - dersom 70årsdagen er i en helg`() {
        val dagen = 12.januar
        assertTrue(FYLLER_70_ÅR_13_JANUAR_2018.innenfor70årsgrense(dagen))
        assertTrue(FYLLER_70_ÅR_13_JANUAR_2018.harNådd70årsgrense(dagen))
    }

    @Test
    fun `har fylt 70 år`() {
        assertFalse(FYLLER_70_ÅR_10_JANUAR_2018.harNådd70årsgrense(8.januar))
        assertTrue(FYLLER_70_ÅR_10_JANUAR_2018.harNådd70årsgrense(9.januar))
        assertTrue(FYLLER_70_ÅR_10_JANUAR_2018.harNådd70årsgrense(10.januar))
    }

    @Test
    fun `har fylt 70 år hensyntar helg`() {
        assertFalse(FYLLER_70_ÅR_13_JANUAR_2018.harNådd70årsgrense(11.januar))
        assertFalse(FYLLER_70_ÅR_14_JANUAR_2018.harNådd70årsgrense(11.januar))
        assertFalse(FYLLER_70_ÅR_15_JANUAR_2018.harNådd70årsgrense(11.januar))
        assertTrue(FYLLER_70_ÅR_13_JANUAR_2018.harNådd70årsgrense(12.januar))
        assertTrue(FYLLER_70_ÅR_14_JANUAR_2018.harNådd70årsgrense(12.januar))
        assertTrue(FYLLER_70_ÅR_15_JANUAR_2018.harNådd70årsgrense(12.januar))
    }

    @Test
    fun `67årsgrense`() {
        assertTrue(FYLLER_67_ÅR_1_JANUAR_2018.innenfor67årsgrense(31.desember(2017)))
        assertTrue(FYLLER_67_ÅR_1_JANUAR_2018.innenfor67årsgrense(1.januar))
        assertFalse(FYLLER_67_ÅR_1_JANUAR_2018.innenfor67årsgrense(2.januar))
    }

    @Test
    fun `begrunnelse for alder`() {
        assertEquals(Begrunnelse.SykepengedagerOppbrukt, FYLLER_67_ÅR_1_JANUAR_2018.begrunnelseForAlder(1.januar))
        assertEquals(Begrunnelse.SykepengedagerOppbruktOver67, FYLLER_67_ÅR_1_JANUAR_2018.begrunnelseForAlder(2.januar))
        assertEquals(Begrunnelse.SykepengedagerOppbruktOver67, FYLLER_67_ÅR_1_JANUAR_2018.begrunnelseForAlder(2.januar))
        assertEquals(Begrunnelse.SykepengedagerOppbruktOver67, FYLLER_70_ÅR_10_JANUAR_2018.begrunnelseForAlder(9.januar))
        assertEquals(Begrunnelse.Over70, FYLLER_70_ÅR_10_JANUAR_2018.begrunnelseForAlder(10.januar))
    }

    @Test
    fun `får ikke lov å søke sykepenger dersom personen er mindre enn 18 år på søknadstidspunktet`() {
        assertTrue(FYLLER_18_ÅR_2_NOVEMBER_2018.forUngForÅSøke(1.november))
    }

    @Test
    fun `får lov å søke dersom personen er minst 18 år`() {
        assertFalse(FYLLER_18_ÅR_2_NOVEMBER_2018.forUngForÅSøke(2.november))
    }

    @Test
    fun `forhøyet inntektskrav`() {
        assertFalse(FYLLER_67_ÅR_1_JANUAR_2018.forhøyetInntektskrav(1.januar))
        assertTrue(FYLLER_67_ÅR_1_JANUAR_2018.forhøyetInntektskrav(2.januar))
    }

    @Test
    fun `riktig begrunnelse for minimum inntekt ved alder over 67`() {
        assertEquals(Begrunnelse.MinimumInntektOver67, FYLLER_67_ÅR_1_JANUAR_2018.begrunnelseForMinimumInntekt(2.januar))
    }

    @Test
    fun `riktig begrunnelse for minimum inntekt ved alder 67 eller under`() {
        assertEquals(Begrunnelse.MinimumInntekt, FYLLER_67_ÅR_1_JANUAR_2018.begrunnelseForMinimumInntekt(1.januar))
    }

    @Test
    fun `etterlevelse for periode før fylte 70`() {
        val jurist = MaskinellJurist()
        Alder(1.januar(2000)).etterlevelse70år(
            aktivitetslogg = Aktivitetslogg(),
            periode = 17.mai(2069) til 31.desember(2069),
            avvisteDager = emptySet(),
            jurist = jurist
        )
        SubsumsjonInspektør(jurist).assertVurdert(paragraf = PARAGRAF_8_3, ledd = LEDD_1, punktum = 2.punktum, versjon = 16.desember(2011))
    }

    @Test
    fun `siste dag med sykepenger 70 år`() {
        assertSisteDagMedSykepenger(9.januar, Hjemmelbegrunnelse.OVER_70, 10.januar, FYLLER_70_ÅR_10_JANUAR_2018, 248, 60)
        assertSisteDagMedSykepenger(9.januar, Hjemmelbegrunnelse.OVER_70, 1.januar, FYLLER_70_ÅR_10_JANUAR_2018, 248, 60)
    }

    @Test
    fun `siste dag med sykepenger 67 år`() {
        assertSisteDagMedSykepenger(1.januar + 60.ukedager, Hjemmelbegrunnelse.OVER_67, 1.januar, FYLLER_67_ÅR_1_JANUAR_2018, 248, 60)
        assertSisteDagMedSykepenger(1.januar + 20.ukedager, Hjemmelbegrunnelse.UNDER_67, 1.januar, FYLLER_67_ÅR_1_JANUAR_2018, 20, 60)
        assertSisteDagMedSykepenger(1.januar + 60.ukedager, Hjemmelbegrunnelse.OVER_67, 1.desember(2017), FYLLER_67_ÅR_1_JANUAR_2018, 200, 60)
        assertSisteDagMedSykepenger(1.mars + 40.ukedager, Hjemmelbegrunnelse.OVER_67, 1.mars, FYLLER_67_ÅR_1_JANUAR_2018, 248, 40)
    }

    @Test
    fun `siste dag med sykepenger under 67 år`() {
        assertSisteDagMedSykepenger(1.januar + 248.ukedager, Hjemmelbegrunnelse.UNDER_67, 1.januar, FYLLER_18_ÅR_2_NOVEMBER_2018, 248, 60)
        assertSisteDagMedSykepenger(1.januar, Hjemmelbegrunnelse.UNDER_67, 1.januar, FYLLER_18_ÅR_2_NOVEMBER_2018, 0, 60)
    }

    @Test
    fun `siste dag med sykepenger under 67 år faller på samme dag som over 67`() {
        assertSisteDagMedSykepenger(1.januar + 60.ukedager, Hjemmelbegrunnelse.UNDER_67, 1.januar, FYLLER_18_ÅR_2_NOVEMBER_2018, 60, 60)
    }

    @Test
    fun `siste dag med sykepenger over 67 år faller på samme dag som over 70`() {
        assertSisteDagMedSykepenger(9.januar, Hjemmelbegrunnelse.OVER_67, 1.januar, FYLLER_70_ÅR_10_JANUAR_2018, 248, 6)
    }

    private enum class Hjemmelbegrunnelse { UNDER_67, OVER_67, OVER_70 }

    private fun assertSisteDagMedSykepenger(forventet: LocalDate, begrunnelse: Hjemmelbegrunnelse, sisteBetalteDag: LocalDate, alder: Alder, gjenståendeSykepengedager: Int, gjenståendeSykepengedagerOver67: Int) {
        lateinit var hjemmel: Hjemmelbegrunnelse
        assertEquals(forventet, alder.maksimumSykepenger(sisteBetalteDag, 0, gjenståendeSykepengedager, gjenståendeSykepengedagerOver67).sisteDag(object : Alder.MaksimumSykepenger.Begrunnelse {
            override fun `§ 8-12 ledd 1 punktum 1`(sisteDag: LocalDate, forbrukteDager: Int, gjenståendeDager: Int) {
                hjemmel = Hjemmelbegrunnelse.UNDER_67
            }

            override fun `§ 8-51 ledd 3`(sisteDag: LocalDate, forbrukteDager: Int, gjenståendeDager: Int) {
                hjemmel = Hjemmelbegrunnelse.OVER_67
            }

            override fun `§ 8-3 ledd 1 punktum 2`(sisteDag: LocalDate, forbrukteDager: Int, gjenståendeDager: Int) {
                hjemmel = Hjemmelbegrunnelse.OVER_70
            }
        }))
        assertEquals(begrunnelse, hjemmel)
    }

    @Test
    fun `etterlevelse for periode man fyller 70`() {
        val jurist = MaskinellJurist()
        Alder(1.januar(2000)).etterlevelse70år(
            aktivitetslogg = Aktivitetslogg(),
            periode = 17.mai(2069) til 1.januar(2070),
            avvisteDager = setOf(1.januar(2070)),
            jurist = jurist
        )
        SubsumsjonInspektør(jurist).assertOppfylt(paragraf = PARAGRAF_8_3, ledd = LEDD_1, versjon = 16.desember(2011), punktum = 2.punktum)
        SubsumsjonInspektør(jurist).assertIkkeOppfylt(paragraf = PARAGRAF_8_3, ledd = LEDD_1, versjon = 16.desember(2011), punktum = 2.punktum)
    }

    @Test
    fun `etterlevelse for periode etter fylte 70`() {
        val jurist = MaskinellJurist()
        Alder(1.januar(2000)).etterlevelse70år(
            aktivitetslogg = Aktivitetslogg(),
            periode = 1.januar(2070) til 5.januar(2070),
            avvisteDager = setOf(1.januar(2070), 2.januar(2070), 3.januar(2070), 4.januar(2070), 5.januar(2070)),
            jurist = jurist
        )
        SubsumsjonInspektør(jurist).assertIkkeOppfylt(paragraf = PARAGRAF_8_3, ledd = LEDD_1, versjon = 16.desember(2011), punktum = 2.punktum)
    }
}
