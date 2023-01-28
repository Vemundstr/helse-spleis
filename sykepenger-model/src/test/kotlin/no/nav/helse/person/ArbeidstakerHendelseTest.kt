package no.nav.helse.person

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import no.nav.helse.hendelser.ArbeidstakerHendelse

internal class ArbeidstakerHendelseTest {

    private companion object {
        private const val FØDSELSNUMMER = "fnr"
        private const val AKTØR = "aktørId"
        private const val ORGNR = "orgnr"
        private val MELDINGSREFERANSE = UUID.randomUUID()
    }

    @Test
    fun kontekst() {
        assertEquals(mapOf(
            "meldingsreferanseId" to MELDINGSREFERANSE.toString(),
            "aktørId" to AKTØR,
            "fødselsnummer" to FØDSELSNUMMER,
            "organisasjonsnummer" to ORGNR
        ), Testhendelse().toSpesifikkKontekst().kontekstMap)
    }

    private class Testhendelse : ArbeidstakerHendelse(MELDINGSREFERANSE, FØDSELSNUMMER, AKTØR, ORGNR)
}
