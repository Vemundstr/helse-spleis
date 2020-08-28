package no.nav.helse.hendelser

import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.InntekthistorikkVol2
import no.nav.helse.testhelpers.februar
import no.nav.helse.testhelpers.januar
import no.nav.helse.testhelpers.mars
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

internal class InntektsopplysningTest {

    private companion object {
        private val HENDELSE = UUID.randomUUID()
        private const val ORGNR = "123456789"
        private val DATO = 1.januar
        private val PERIODE = Periode(1.februar, 28.februar)
    }

    private lateinit var aktivitetslogg: Aktivitetslogg
    private lateinit var inntekthistorikk: InntekthistorikkVol2

    @BeforeEach
    fun setup() {
        aktivitetslogg = Aktivitetslogg()
        inntekthistorikk = InntekthistorikkVol2()
    }

    @Test
    fun `legger til inntekter for samme arbeidsgiver`() {
        inntektsopplysning(DATO, ORGNR).addInntekter(HENDELSE, ORGNR, inntekthistorikk)
        TODO()//assertNotNull(inntekthistorikk.inntekt(DATO.minusDays(1)))
    }

    @Test
    fun `legger ikke til inntekter for annen arbeidsgiver`() {
        inntektsopplysning(DATO, "987654321").addInntekter(HENDELSE, ORGNR, inntekthistorikk)
        TODO()//assertNull(inntekthistorikk.inntekt(DATO.minusDays(1)))
    }

    @Test
    fun `refusjon opphører før perioden`() {
        inntektsopplysning(DATO, ORGNR, 1.januar).valider(aktivitetslogg, PERIODE)
        assertTrue(aktivitetslogg.hasErrors())
    }

    @Test
    fun `refusjon opphører i perioden`() {
        inntektsopplysning(DATO, ORGNR, 15.februar).valider(aktivitetslogg, PERIODE)
        assertTrue(aktivitetslogg.hasErrors())
    }

    @Test
    fun `refusjon opphører etter perioden`() {
        inntektsopplysning(DATO, ORGNR, 1.mars).valider(aktivitetslogg, PERIODE)
        assertFalse(aktivitetslogg.hasErrors())
    }

    private fun inntektsopplysning(dato: LocalDate, orgnr: String, refusjonTom: LocalDate? = null) =
        Utbetalingshistorikk.Inntektsopplysning(dato, 1000.månedlig, orgnr, true, refusjonTom)
}
