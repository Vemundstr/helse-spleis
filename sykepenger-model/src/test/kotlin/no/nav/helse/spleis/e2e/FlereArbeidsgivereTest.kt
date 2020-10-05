package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.*
import no.nav.helse.hendelser.UtbetalingHendelse.Oppdragstatus.AKSEPTERT
import no.nav.helse.hendelser.Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver
import no.nav.helse.person.TilstandType
import no.nav.helse.person.TilstandType.*
import no.nav.helse.testhelpers.*
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

@Disabled("Virker ikke før støtte for flere arbeidsgivere blir skrudd på i Person")
internal class FlereArbeidsgivereTest : AbstractEndToEndTest() {

    internal companion object {
        private const val a1 = "arbeidsgiver 1"
        private const val a2 = "arbeidsgiver 2"
        private const val a3 = "arbeidsgiver 3"
        private const val a4 = "arbeidsgiver 4"
    }

    private val a1Inspektør get() = TestArbeidsgiverInspektør(person, a1)
    private val a2Inspektør get() = TestArbeidsgiverInspektør(person, a2)
    private val a3Inspektør get() = TestArbeidsgiverInspektør(person, a3)
    private val a4Inspektør get() = TestArbeidsgiverInspektør(person, a4)

    @Test
    fun `Sammenligningsgrunnlag for flere arbeidsgivere`() {
        val periodeA1 = 1.januar to 31.januar
        nyPeriode(1.januar to 31.januar, a1)
        person.håndter(
            inntektsmelding(
                UUID.randomUUID(),
                listOf(Periode(periodeA1.start, periodeA1.start.plusDays(15))),
                beregnetInntekt = 30000.månedlig,
                refusjon = Triple(null, 30000.månedlig, emptyList()),
                førsteFraværsdag = periodeA1.start,
                orgnummer = a1
            )
        )
        person.håndter(vilkårsgrunnlag(a1.id(0), orgnummer = a1, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioder {
                inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SAMMENLIGNINGSGRUNNLAG
                1.januar(2017) til 1.juni(2017) inntekter {
                    a1 inntekt INNTEKT
                    a2 inntekt 5000.månedlig
                }
                1.august(2017) til 1.desember(2017) inntekter {
                    a1 inntekt 17000.månedlig
                    a2 inntekt 3500.månedlig
                }
            }
        )))

        val periodeA2 = 15.januar to 15.februar
        nyPeriode(periodeA2, a2)
        person.håndter(
            inntektsmelding(
                UUID.randomUUID(),
                listOf(Periode(periodeA2.start, periodeA2.start.plusDays(15))),
                beregnetInntekt = 10000.månedlig,
                refusjon = Triple(null, 10000.månedlig, emptyList()),
                førsteFraværsdag = periodeA2.start,
                orgnummer = a2
            )
        )

        assertEquals(318500.årlig, person.sammenligningsgrunnlag(15.januar to 15.februar))
    }

    @Test
    fun `Sammenligningsgrunnlag for flere arbeidsgivere med flere sykeperioder`() {
        nyPeriode(15.januar to 5.februar, a1)
        person.håndter(
            inntektsmelding(
                UUID.randomUUID(),
                listOf(15.januar to 28.januar, 2.februar to 3.februar),
                beregnetInntekt = 30000.månedlig,
                refusjon = Triple(null, 30000.månedlig, emptyList()),
                førsteFraværsdag = 2.februar,
                orgnummer = a1
            )
        )
        person.håndter(vilkårsgrunnlag(a1.id(0), orgnummer = a1, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioder {
                inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SAMMENLIGNINGSGRUNNLAG
                1.januar(2017) til 1.juni(2017) inntekter {
                    a1 inntekt INNTEKT
                    a2 inntekt 5000.månedlig
                }
                1.august(2017) til 1.desember(2017) inntekter {
                    a1 inntekt 17000.månedlig
                    a2 inntekt 3500.månedlig
                }
            }
        )))

        val periodeA2 = 2.februar to 20.februar
        nyPeriode(periodeA2, a2)
        person.håndter(
            inntektsmelding(
                UUID.randomUUID(),
                listOf(Periode(periodeA2.start, periodeA2.start.plusDays(15))),
                beregnetInntekt = 10000.månedlig,
                refusjon = Triple(null, 10000.månedlig, emptyList()),
                førsteFraværsdag = periodeA2.start,
                orgnummer = a2
            )
        )

        assertEquals(282500.årlig, person.sammenligningsgrunnlag(15.januar to 5.februar))
    }

    @Test
    fun `Sammenligningsgrunnlag for flere arbeidsgivere som overlapper hverandres sykeperioder`() {
        nyPeriode(15.januar to 5.februar, a1)
        person.håndter(
            inntektsmelding(
                UUID.randomUUID(),
                listOf(15.januar to 28.januar, 2.februar to 3.februar),
                beregnetInntekt = 30000.månedlig,
                refusjon = Triple(null, 30000.månedlig, emptyList()),
                førsteFraværsdag = 2.februar,
                orgnummer = a1
            )
        )
        val periodeA2 = 15.januar to 15.februar
        nyPeriode(periodeA2, a2)

        person.håndter(vilkårsgrunnlag(a1.id(0), orgnummer = a1, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioder {
                inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SAMMENLIGNINGSGRUNNLAG
                1.januar(2017) til 1.juni(2017) inntekter {
                    a1 inntekt INNTEKT
                    a2 inntekt 5000.månedlig
                }
                1.august(2017) til 1.desember(2017) inntekter {
                    a1 inntekt 17000.månedlig
                    a2 inntekt 3500.månedlig
                }
            }
        )))

        person.håndter(
            inntektsmelding(
                UUID.randomUUID(),
                listOf(Periode(periodeA2.start, periodeA2.start.plusDays(15))),
                beregnetInntekt = 10000.månedlig,
                refusjon = Triple(null, 10000.månedlig, emptyList()),
                førsteFraværsdag = periodeA2.start,
                orgnummer = a2
            )
        )

        assertEquals(318500.årlig, person.sammenligningsgrunnlag(15.januar to 15.februar))
    }

    @Test
    fun `overlappende arbeidsgivere ikke sendt til infotrygd`() {
        gapPeriode(1.januar to 31.januar, a1)
        gapPeriode(15.januar to 15.februar, a2)
        assertNoErrors(a1Inspektør)
        assertNoErrors(a2Inspektør)

        historikk(a1)
        assertNoErrors(a1Inspektør)
        assertNoErrors(a2Inspektør)
        assertTilstand(a1, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a2, AVVENTER_HISTORIKK)
    }

    @Test
    fun `vedtaksperioder atskilt med betydelig tid`() {
        prosessperiode(1.januar to 31.januar, a1)
        assertNoErrors(a1Inspektør)
        assertTilstand(a1, AVSLUTTET)

        prosessperiode(1.mars to 31.mars, a2)
        assertNoErrors(a2Inspektør)
        assertTilstand(a1, AVSLUTTET)
    }

    @Test
    fun `Tre overlappende perioder med en ikke-overlappende periode`() {
        gapPeriode(1.januar to 31.januar, a1)
        gapPeriode(15.januar to 15.mars, a2)
        gapPeriode(1.februar to 28.februar, a3)
        gapPeriode(15.april to 15.mai, a4)

        historikk(a1)
        assertTilstand(a1, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a2, AVVENTER_HISTORIKK)
        assertTilstand(a3, AVVENTER_HISTORIKK)
        assertTilstand(a4, AVVENTER_HISTORIKK)

        historikk(a3)
        assertTilstand(a1, AVVENTER_HISTORIKK)
        assertTilstand(a2, AVVENTER_HISTORIKK)
        assertTilstand(a3, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a4, AVVENTER_HISTORIKK)

        historikk(a1)
        assertTilstand(a1, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a2, AVVENTER_HISTORIKK)
        assertTilstand(a3, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a4, AVVENTER_HISTORIKK)

        historikk(a2)
        assertTilstand(a1, AVVENTER_HISTORIKK)
        assertTilstand(a2, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a3, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a4, AVVENTER_HISTORIKK)

        historikk(a1)
        assertTilstand(a1, AVVENTER_SIMULERING)
        assertTilstand(a2, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a3, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a4, AVVENTER_HISTORIKK)

        betale(a1)
        assertTilstand(a1, AVSLUTTET)
        assertTilstand(a2, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a3, AVVENTER_HISTORIKK)
        assertTilstand(a4, AVVENTER_HISTORIKK)

        historikk(a3)
        assertTilstand(a1, AVSLUTTET)
        assertTilstand(a2, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a3, AVVENTER_SIMULERING)
        assertTilstand(a4, AVVENTER_HISTORIKK)

        betale(a3)
        assertTilstand(a1, AVSLUTTET)
        assertTilstand(a2, AVVENTER_HISTORIKK)
        assertTilstand(a3, AVSLUTTET)
        assertTilstand(a4, AVVENTER_HISTORIKK)

        historikk(a2)
        assertTilstand(a1, AVSLUTTET)
        assertTilstand(a2, AVVENTER_SIMULERING)
        assertTilstand(a3, AVSLUTTET)
        assertTilstand(a4, AVVENTER_HISTORIKK)

        betale(a2)
        assertTilstand(a1, AVSLUTTET)
        assertTilstand(a2, AVSLUTTET)
        assertTilstand(a3, AVSLUTTET)
        assertTilstand(a4, AVVENTER_HISTORIKK)

        historikk(a4)
        assertTilstand(a1, AVSLUTTET)
        assertTilstand(a2, AVSLUTTET)
        assertTilstand(a3, AVSLUTTET)
        assertTilstand(a4, AVVENTER_SIMULERING)

        betale(a4)
        assertTilstand(a1, AVSLUTTET)
        assertTilstand(a2, AVSLUTTET)
        assertTilstand(a3, AVSLUTTET)
        assertTilstand(a4, AVSLUTTET)
    }

    @Test
    fun `Tre paralelle perioder`() {
        gapPeriode(3.januar to 31.januar, a1)
        gapPeriode(1.januar to 31.januar, a2)
        gapPeriode(2.januar to 31.januar, a3)

        historikk(a1)
        assertTilstand(a1, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a2, AVVENTER_HISTORIKK)
        assertTilstand(a3, AVVENTER_HISTORIKK)

        historikk(a2)
        assertTilstand(a1, AVVENTER_HISTORIKK)
        assertTilstand(a2, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a3, AVVENTER_HISTORIKK)

        historikk(a1)
        assertTilstand(a1, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a2, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a3, AVVENTER_HISTORIKK)

        historikk(a3)
        assertTilstand(a1, AVVENTER_HISTORIKK)
        assertTilstand(a2, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a3, AVVENTER_ARBEIDSGIVERE)

        historikk(a1)
        assertTilstand(a1, AVVENTER_SIMULERING)
        assertTilstand(a2, AVVENTER_ARBEIDSGIVERE)
        assertTilstand(a3, AVVENTER_ARBEIDSGIVERE)

        betale(a1)
        assertTilstand(a1, AVSLUTTET)
        assertTilstand(a2, AVVENTER_HISTORIKK)
        assertTilstand(a3, AVVENTER_ARBEIDSGIVERE)

        historikk(a2)
        assertTilstand(a1, AVSLUTTET)
        assertTilstand(a2, AVVENTER_SIMULERING)
        assertTilstand(a3, AVVENTER_ARBEIDSGIVERE)

        betale(a2)
        assertTilstand(a1, AVSLUTTET)
        assertTilstand(a2, AVSLUTTET)
        assertTilstand(a3, AVVENTER_HISTORIKK)

        historikk(a3)
        assertTilstand(a1, AVSLUTTET)
        assertTilstand(a2, AVSLUTTET)
        assertTilstand(a3, AVVENTER_SIMULERING)

        betale(a3)
        assertTilstand(a1, AVSLUTTET)
        assertTilstand(a2, AVSLUTTET)
        assertTilstand(a3, AVSLUTTET)
    }

    private fun assertTilstand(
        orgnummer: String,
        tilstand: TilstandType,
        vedtaksperiodeIndeks: Int = 0
    ) {
        assertEquals(tilstand, TestArbeidsgiverInspektør(person, orgnummer).sisteTilstand(vedtaksperiodeIndeks))
    }

    private fun prosessperiode(periode: Periode, orgnummer: String, sykedagstelling: Int = 0) {
        gapPeriode(periode, orgnummer)
        historikk(orgnummer, sykedagstelling)
        betale(orgnummer)
    }

    private fun forlengelsePeriode(periode: Periode, orgnummer: String) {
        nyPeriode(periode, orgnummer)
    }

    private fun gapPeriode(periode: Periode, orgnummer: String) {
        nyPeriode(periode, orgnummer)
        person.håndter(
            inntektsmelding(
                UUID.randomUUID(),
                listOf(Periode(periode.start, periode.start.plusDays(15))),
                førsteFraværsdag = periode.start,
                orgnummer = orgnummer
            )
        )
        person.håndter(vilkårsgrunnlag(orgnummer.id(0), orgnummer = orgnummer, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioder {
                1.januar(2017) til 1.desember(2017) inntekter {
                    orgnummer inntekt INNTEKT
                }
            }
        )))
    }

    private fun nyPeriode(periode: Periode, orgnummer: String) {
        person.håndter(
            sykmelding(
                UUID.randomUUID(),
                Sykmeldingsperiode(periode.start, periode.endInclusive, 100),
                orgnummer = orgnummer
            )
        )
        person.håndter(
            søknad(
                UUID.randomUUID(),
                Søknad.Søknadsperiode.Sykdom(periode.start, periode.endInclusive, 100),
                orgnummer = orgnummer
            )
        )
    }

    private fun historikk(orgnummer: String, sykedagstelling: Int = 0) {
        person.håndter(
            ytelser(
                orgnummer.id(0),
                utbetalinger = utbetalinger(sykedagstelling, orgnummer),
                orgnummer = orgnummer
            )
        )
    }

    private fun betale(orgnummer: String) {
        person.håndter(simulering(orgnummer.id(0), orgnummer = orgnummer))
        person.håndter(
            utbetalingsgodkjenning(
                orgnummer.id(0),
                true,
                orgnummer = orgnummer,
                automatiskBehandling = false
            )
        )
        person.håndter(utbetaling(orgnummer.id(0), AKSEPTERT, orgnummer = orgnummer))
    }

    private fun utbetalinger(dagTeller: Int, orgnummer: String): List<RefusjonTilArbeidsgiver> {
        if (dagTeller == 0) return emptyList()
        val førsteDato = 2.desember(2017).minusDays(
            (
                (dagTeller / 5 * 7) + dagTeller % 5
                ).toLong()
        )
        return listOf(
            RefusjonTilArbeidsgiver(
                førsteDato,
                1.desember(2017),
                100,
                100,
                orgnummer
            )
        )
    }

    private infix fun LocalDate.to(other: LocalDate) = Periode(this, other)
}
