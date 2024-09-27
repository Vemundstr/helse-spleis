package no.nav.helse.etterlevelse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import org.junit.jupiter.api.assertThrows

internal class MaskinellJuristTest {

    @Test
    fun `jurist lytter på endringer av kontekst`() {
        val vedtaksperiodeJurist = MaskinellJurist()
            .medFødselsnummer("10052088033")
            .medOrganisasjonsnummer("123456789")
            .medVedtaksperiode(UUID.fromString("6bce6c83-28ab-4a8c-b7f6-8402988bc8fc"), emptyList(), 1.januar..31.januar)

        vedtaksperiodeJurist.logg(`§ 8-2 ledd 1`(true, LocalDate.now(), 1, emptyList(), 1))

        assertKontekster(
            vedtaksperiodeJurist.subsumsjoner()[0],
             "10052088033" to KontekstType.Fødselsnummer,
            "123456789" to KontekstType.Organisasjonsnummer,
            "6bce6c83-28ab-4a8c-b7f6-8402988bc8fc" to KontekstType.Vedtaksperiode
        )
    }

    @Test
    fun `kan ikke ha duplikate orgnr`() {
        val arbeidsgiverJurist = MaskinellJurist()
            .medFødselsnummer("10052088033")
            .medOrganisasjonsnummer("123456789")
            .medOrganisasjonsnummer("987654321")

        assertThrows<IllegalStateException> {
            arbeidsgiverJurist.logg(`§ 8-2 ledd 1`(true, LocalDate.now(), 1, emptyList(), 1))
        }
    }

    @Test
    fun `avviste dager`(){
        val vedtaksperiodeJurist = MaskinellJurist()
            .medFødselsnummer("10052088033")
            .medOrganisasjonsnummer("123456789")
            .medVedtaksperiode(UUID.randomUUID(), emptyList(), 1.januar..31.januar)
        `§ 8-13 ledd 1`(1.januar..31.januar, listOf(15.januar..16.januar), emptyList()).forEach {
            vedtaksperiodeJurist.logg(it)
        }
    }

    private fun assertKontekster(subsumsjon: Subsumsjon, vararg kontekster: Pair<String, KontekstType>) {
        assertEquals(
            kontekster.map { Subsumsjonskontekst(type = it.second, verdi = it.first) },
            subsumsjon.kontekster
        )
    }
}
