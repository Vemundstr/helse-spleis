package no.nav.helse.serde.migration

import no.nav.helse.serde.serdeObjectMapper
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class V108UtvidetUtbetalingstidslinjeBeregningTest {

    @Test
    fun `Utbetalingstidslinjeberegning peker på nyeste innslag i inntektshistorikken og nyeste innslag i vilkårsgrunnlaghistorikken`() {
        assertEquals(toNode(expected), migrer(original))
    }

    @Test
    fun `Utbetalingstidslinjeberegning for flere arbeidsgivere peker på nyeste innslag i inntektshistorikken og nyeste innslag i vilkårsgrunnlaghistorikken`() {
        assertEquals(toNode(expectedMedFlereArbeidsgivereOgFlereBeregninger), migrer(originalMedFlereArbeidsgivereOgFlereBeregninger))
    }

    @Test
    fun `Har ikke vilkårsgrunnlaghistorikkInnslag`() {
        assertEquals(toNode(expectedUtenVilkårsgrunnlagHistorikk), migrer(originalUtenVilkårsgrunnlagHistorikk))
    }

    private fun toNode(json: String) = serdeObjectMapper.readTree(json)

    private fun migrer(json: String) = listOf(V108UtvidetUtbetalingstidslinjeBeregning()).migrate(toNode(json))

    @Language("JSON")
    private val original = """{
    "arbeidsgivere": [
        {
            "beregnetUtbetalingstidslinjer": [
                {
                    "sykdomshistorikkElementId": "11ef5393-5fab-40a5-82a1-ad234011e619"
                }
            ],
            "inntektshistorikk": [
                {
                    "id": "472a1c71-df9e-488b-8aa8-f98ac97c5702"
                },
                {
                    "id": "a9bfe54c-c19c-4283-a2c6-ff17dade734e"
                }
            ]
        }
    ],
    "vilkårsgrunnlagHistorikk": [
        {
            "id": "bb85447d-83c2-460a-a646-ffb85b58b7c4"
        },
        {
            "id": "d487600d-b752-4d7c-8184-759bf7425631"
        }
    ],
    "skjemaVersjon": 107
}"""

    @Language("JSON")
    private val expected = """{
    "arbeidsgivere": [
        {
            "beregnetUtbetalingstidslinjer": [
                {
                    "sykdomshistorikkElementId": "11ef5393-5fab-40a5-82a1-ad234011e619",
                    "vilkårsgrunnlagHistorikkInnslagId": "bb85447d-83c2-460a-a646-ffb85b58b7c4",
                    "inntektshistorikkInnslagId": "472a1c71-df9e-488b-8aa8-f98ac97c5702"
                }
            ],
            "inntektshistorikk": [
                {
                    "id": "472a1c71-df9e-488b-8aa8-f98ac97c5702"
                },
                {
                    "id": "a9bfe54c-c19c-4283-a2c6-ff17dade734e"
                }
            ]
        }
    ],
    "vilkårsgrunnlagHistorikk": [
        {
            "id": "bb85447d-83c2-460a-a646-ffb85b58b7c4"
        },
        {
            "id": "d487600d-b752-4d7c-8184-759bf7425631"
        }
    ],
    "skjemaVersjon": 108
}"""

    @Language("JSON")
    private val originalMedFlereArbeidsgivereOgFlereBeregninger = """{
    "arbeidsgivere": [
        {
            "beregnetUtbetalingstidslinjer": [
                {
                    "sykdomshistorikkElementId": "11ef5393-5fab-40a5-82a1-ad234011e619"
                },
                {
                    "sykdomshistorikkElementId": "6f8fc633-5c04-41e1-ae63-719bb0408123"
                }
            ],
            "inntektshistorikk": [
                {
                    "id": "472a1c71-df9e-488b-8aa8-f98ac97c5702"
                },
                {
                    "id": "9919d671-8fe4-4739-9064-0e54c46372ba"
                }
            ]
        },
        {
            "beregnetUtbetalingstidslinjer": [
                {
                    "sykdomshistorikkElementId": "9b644781-162e-4a9d-a9c3-1a95aa802a9f"
                }
            ],
            "inntektshistorikk": [
                {
                    "id": "54b4eb3c-dad8-40a0-8017-b95af43a293e"
                }
            ]
        }
    ],
    "vilkårsgrunnlagHistorikk": [
        {
            "id": "bb85447d-83c2-460a-a646-ffb85b58b7c4"
        },
        {
            "id": "6a0dd169-3c8b-4b77-9e16-acb1983364d0"
        }
    ],
    "skjemaVersjon": 107
}"""


    @Language("JSON")
    private val expectedMedFlereArbeidsgivereOgFlereBeregninger = """{
    "arbeidsgivere": [
        {
            "beregnetUtbetalingstidslinjer": [
                {
                    "sykdomshistorikkElementId": "11ef5393-5fab-40a5-82a1-ad234011e619",
                    "vilkårsgrunnlagHistorikkInnslagId": "bb85447d-83c2-460a-a646-ffb85b58b7c4",
                    "inntektshistorikkInnslagId": "472a1c71-df9e-488b-8aa8-f98ac97c5702"
                },
                {
                    "sykdomshistorikkElementId": "6f8fc633-5c04-41e1-ae63-719bb0408123",
                    "vilkårsgrunnlagHistorikkInnslagId": "bb85447d-83c2-460a-a646-ffb85b58b7c4",
                    "inntektshistorikkInnslagId": "472a1c71-df9e-488b-8aa8-f98ac97c5702"
                }
            ],
            "inntektshistorikk": [
                {
                    "id": "472a1c71-df9e-488b-8aa8-f98ac97c5702"
                },
                {
                    "id": "9919d671-8fe4-4739-9064-0e54c46372ba"
                }
            ]
        },
        {
            "beregnetUtbetalingstidslinjer": [
                {
                    "sykdomshistorikkElementId": "9b644781-162e-4a9d-a9c3-1a95aa802a9f",
                    "vilkårsgrunnlagHistorikkInnslagId": "bb85447d-83c2-460a-a646-ffb85b58b7c4",
                    "inntektshistorikkInnslagId": "54b4eb3c-dad8-40a0-8017-b95af43a293e"
                }
            ],
            "inntektshistorikk": [
                {
                    "id": "54b4eb3c-dad8-40a0-8017-b95af43a293e"
                }
            ]
        }
    ],
    "vilkårsgrunnlagHistorikk": [
        {
            "id": "bb85447d-83c2-460a-a646-ffb85b58b7c4"
        },
        {
            "id": "6a0dd169-3c8b-4b77-9e16-acb1983364d0"
        }
    ],
    "skjemaVersjon": 108
}"""

    @Language("JSON")
    private val originalUtenVilkårsgrunnlagHistorikk = """{
    "arbeidsgivere": [
      {
        "beregnetUtbetalingstidslinjer": [],
        "inntektshistorikk": [
          {
            "id": "472a1c71-df9e-488b-8aa8-f98ac97c5702"
          }
        ]
      }
    ],
    "vilkårsgrunnlagHistorikk": [],
    "skjemaVersjon": 107
}"""

    @Language("JSON")
    private val expectedUtenVilkårsgrunnlagHistorikk = """{
    "arbeidsgivere": [
      {
        "beregnetUtbetalingstidslinjer": [],
        "inntektshistorikk": [
          {
            "id": "472a1c71-df9e-488b-8aa8-f98ac97c5702"
          }
        ]
      }
    ],
    "vilkårsgrunnlagHistorikk": [],
    "skjemaVersjon": 108
}"""
}
