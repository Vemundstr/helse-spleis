package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import no.nav.helse.Alder
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.Alder.Companion.alder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class UtbetalingTellerTest {
    private val UNG_PERSON_FNR_2018 = 15.januar(2000).alder
    private val PERSON_67_ÅR_FNR_2018 = 15.januar(1951).alder
    private val PERSON_70_ÅR_FNR_2018 = 15.januar(1948).alder
    private lateinit var grense: UtbetalingTeller
    private lateinit var maksdatosituasjon: Maksdatosituasjon

    @Test
    fun `Person under 67 år får utbetalt 248 dager`() {
        grense(UNG_PERSON_FNR_2018, 247)
        assertEquals(1, maksdatosituasjon.gjenståendeDager)
        grense.inkrementer(31.desember)
        assertEquals(0, grense.maksdatosituasjon(31.desember).gjenståendeDager)
    }

   @Test
    fun `Person som blir 67 år får utbetalt 60 dager etter 67 årsdagen`() {
        grense(PERSON_67_ÅR_FNR_2018, 15 + 59)
        assertEquals(1, maksdatosituasjon.gjenståendeDager)
        grense.inkrementer(31.desember)
        assertEquals(0, grense.maksdatosituasjon(31.desember).gjenståendeDager)
    }

    @Test
    fun `Person som blir 70 år har ikke utbetaling på 70 årsdagen`() {
        grense(PERSON_70_ÅR_FNR_2018, 11)
        assertEquals(1, maksdatosituasjon.gjenståendeDager)
        grense.inkrementer(12.januar)
        assertEquals(0, grense.maksdatosituasjon(12.januar).gjenståendeDager)
    }

    @Test
    fun `Person under 67 år får utbetalt 248 `() {
        grense(UNG_PERSON_FNR_2018, 248)
        assertEquals(0, maksdatosituasjon.gjenståendeDager)
        grense.resett()
        assertEquals(248, grense.maksdatosituasjon(1.januar).gjenståendeDager)
    }

    @Test
    fun `Person under 67`() {
        grense(UNG_PERSON_FNR_2018, 247)
        assertEquals(1, maksdatosituasjon.gjenståendeDager)
        grense.dekrementer(2.januar(2021))
        grense.inkrementer(30.desember)
        assertEquals(1, grense.maksdatosituasjon(30.januar).gjenståendeDager)
        grense.inkrementer(31.desember)
        assertEquals(0, grense.maksdatosituasjon(31.desember).gjenståendeDager)
    }

    @Test
    fun `Reset decrement impact`() {
        grense(UNG_PERSON_FNR_2018, 247)
        assertEquals(1, maksdatosituasjon.gjenståendeDager)
        grense.dekrementer(1.januar.minusDays(1))
        grense.inkrementer(31.desember)
        assertEquals(0, grense.maksdatosituasjon(31.januar).gjenståendeDager)
    }

    @Test
    fun maksdato() {
        undersøke(15.mai, UNG_PERSON_FNR_2018, 248, 15.mai)
        undersøke(18.mai, UNG_PERSON_FNR_2018, 244, 14.mai)
        undersøke(21.mai, UNG_PERSON_FNR_2018, 243, 14.mai)
        undersøke(22.mai, UNG_PERSON_FNR_2018, 242, 14.mai)
        undersøke(28.desember, UNG_PERSON_FNR_2018, 1, 17.januar)
        undersøke(9.februar, 12.februar(1948).alder, 1, 17.januar)
        undersøke(22.januar, 12.februar(1948).alder, 57, 17.januar)
        undersøke(7.mai, 12.februar(1951).alder, 65, 17.januar)
        undersøke(12.februar, 12.februar(1951).alder, 247, 9.februar)
        undersøke(13.februar, 12.februar(1951).alder, 246, 9.februar)
    }

    private fun undersøke(expected: LocalDate, alder: Alder, dager: Int, sisteUtbetalingsdag: LocalDate) {
        grense(alder, dager, sisteUtbetalingsdag.minusDays(dager.toLong() - 1))
        assertEquals(expected, maksdatosituasjon.maksdato)
    }

    private fun grense(alder: Alder, dager: Int, dato: LocalDate = 1.januar) {
        grense = UtbetalingTeller(alder, ArbeidsgiverRegler.Companion.NormalArbeidstaker).apply {
            this.resett()
            (0 until dager).forEach { this.inkrementer(dato.plusDays(it.toLong())) }
        }
        maksdatosituasjon = grense.maksdatosituasjon(dato.plusDays(dager.toLong() - 1))
    }

}
