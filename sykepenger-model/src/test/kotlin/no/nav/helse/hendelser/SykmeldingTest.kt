package no.nav.helse.hendelser

import no.nav.helse.dsl.ArbeidsgiverHendelsefabrikk
import no.nav.helse.januar
import no.nav.helse.somPersonidentifikator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class SykmeldingTest {

    private companion object {
        const val UNG_PERSON_FNR_2018 = "12029240045"
        val hendelsefabrikk = ArbeidsgiverHendelsefabrikk(
            aktørId = "12345",
            personidentifikator = UNG_PERSON_FNR_2018.somPersonidentifikator(),
            organisasjonsnummer = "987654321"
        )
    }

    private lateinit var sykmelding: Sykmelding

    @Test
    fun `oppdaterer perioder`() {
        sykmelding(Sykmeldingsperiode(10.januar, 15.januar))

        sykmelding.oppdaterSykmeldingsperioder(emptyList()).also { result ->
            assertEquals(listOf(10.januar til 15.januar), result)
        }

        sykmelding.oppdaterSykmeldingsperioder(
            listOf(1.januar til 2.januar)
        ).also { result ->
            assertEquals(listOf(
                1.januar til 2.januar,
                10.januar til 15.januar
            ), result)
        }

        sykmelding.oppdaterSykmeldingsperioder(
            listOf(17.januar til 20.januar)
        ).also { result ->
            assertEquals(listOf(
                10.januar til 15.januar,
                17.januar til 20.januar
            ), result)
        }

        sykmelding.oppdaterSykmeldingsperioder(listOf(
            1.januar til 2.januar,
            6.januar til 10.januar,
            15.januar til 20.januar,
            23.januar til 25.januar
        )).also { result ->
            assertEquals(listOf(
                1.januar til 2.januar,
                6.januar til 20.januar,
                23.januar til 25.januar
            ), result)
        }
    }
    @Test
    fun `oppdaterer perioder - trimmet dager - en dag igjen`() {
        sykmelding(Sykmeldingsperiode(10.januar, 15.januar))
        sykmelding.trimLeft(14.januar)
        sykmelding.oppdaterSykmeldingsperioder(emptyList()).also { result ->
            assertEquals(listOf(15.januar til 15.januar), result)
        }
    }
    @Test
    fun `oppdaterer perioder - trimmet forbi`() {
        sykmelding(Sykmeldingsperiode(10.januar, 15.januar))
        sykmelding.trimLeft(15.januar)
        val perioder = listOf(1.januar til 2.januar)
        sykmelding.oppdaterSykmeldingsperioder(perioder).also { result ->
            assertEquals(perioder, result)
        }
    }

    @Test
    fun `sykeperioder mangler`() {
        assertThrows<IllegalStateException> { sykmelding() }
    }

    private fun sykmelding(vararg sykeperioder: Sykmeldingsperiode) {
        sykmelding = hendelsefabrikk.lagSykmelding(
            sykeperioder = sykeperioder
        )
    }

}
