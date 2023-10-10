package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.hendelser.Periode
import no.nav.helse.april
import no.nav.helse.februar
import no.nav.helse.januar
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class V25ManglendeForlengelseFraInfotrygdTest {

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Test
    fun `ferdigbygd json blir migrert riktig`() {
        val original = objectMapper.readTree(testperson(
            Pair(Periode(1.januar, 31.januar), "JA"),
            Pair(Periode(1.februar, 10.februar), "IKKE_ETTERSPURT"),
            Pair(Periode(11.februar, 25.februar), "IKKE_ETTERSPURT"),
            skjemaversjon = 24
        ))

        val expected = objectMapper.readTree(testperson(
            Pair(Periode(1.januar, 31.januar), "JA"),
            Pair(Periode(1.februar, 10.februar), "JA"),
            Pair(Periode(11.februar, 25.februar), "JA"),
            skjemaversjon = 25
        ))

        Assertions.assertEquals(expected, listOf(V25ManglendeForlengelseFraInfotrygd()).migrate(original))
    }

    @Test
    fun `forlengelseFraInfotrygd overføres kun til forlengelser`() {
        val original = objectMapper.readTree(testperson(
            Pair(Periode(1.januar, 31.januar), "JA"),
            Pair(Periode(1.februar, 10.februar), "IKKE_ETTERSPURT"),
            Pair(Periode(11.februar, 25.februar), "IKKE_ETTERSPURT"),
            Pair(Periode(1.april, 20.april), "NEI"),
            skjemaversjon = 24
        ))

        val expected = objectMapper.readTree(testperson(
            Pair(Periode(1.januar, 31.januar), "JA"),
            Pair(Periode(1.februar, 10.februar), "JA"),
            Pair(Periode(11.februar, 25.februar), "JA"),
            Pair(Periode(1.april, 20.april), "NEI"),
            skjemaversjon = 25
        ))

        Assertions.assertEquals(expected, listOf(V25ManglendeForlengelseFraInfotrygd()).migrate(original))
    }

}

@Language("JSON")
private fun testperson(vararg perioder: Pair<Periode, String>, skjemaversjon: Int) =
    """
    {
      "arbeidsgivere": [
        {
          "vedtaksperioder": [
            ${perioder.joinToString { (periode, forlengelseFraInfotrygd) ->
                """
                {
                    "fom": "${periode.start}",
                    "tom": "${periode.endInclusive}",
                    "forlengelseFraInfotrygd": "$forlengelseFraInfotrygd"
                }
                """.trimIndent()
            }}
          ]
        }
      ],
      "skjemaVersjon": $skjemaversjon
    }
    """.trimIndent()
