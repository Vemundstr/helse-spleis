package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.*
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.person.TilstandType.*
import no.nav.helse.sykdomstidslinje.Dag.SykHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.testhelpers.*
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.opentest4j.AssertionFailedError

internal class KunEnArbeidsgiverTest : AbstractEndToEndTest() {

    @Test
    fun `ingen historie med inntektsmelding først`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        inspektør.låstePerioder.also {
            assertEquals(0, it.size)
        }
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertEquals(18, it.dagtelling[Sykedag::class])
            assertEquals(6, it.dagtelling[SykHelgedag::class])
        }
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTrue(observatør.utbetalteVedtaksperioder.contains(inspektør.vedtaksperiodeId(0)))
        inspektør.låstePerioder.also {
            assertEquals(1, it.size)
            assertEquals(Periode(1.januar, 26.januar), it.first())
        }
    }

    @Test
    fun `ingen historie med søknad til arbeidsgiver først`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 8.januar, 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(3.januar, 8.januar, 100))
        assertNoWarnings(inspektør)
        håndterInntektsmelding(arbeidsgiverperioder = listOf(Periode(3.januar, 18.januar)), førsteFraværsdag = 3.januar)
        håndterVilkårsgrunnlag(0, INNTEKT)
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertEquals(4, it.dagtelling[Sykedag::class])
            assertEquals(2, it.dagtelling[SykHelgedag::class])
        }
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
    }

    @Test
    fun `ingen historie med to søknader til arbeidsgiver før inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 5.januar, 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(3.januar, 5.januar, 100))
        håndterInntektsmelding(arbeidsgiverperioder = listOf(Periode(3.januar, 5.januar)), førsteFraværsdag = 3.januar)
        håndterVilkårsgrunnlag(0, INNTEKT)

        håndterSykmelding(Sykmeldingsperiode(8.januar, 10.januar, 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(8.januar, 10.januar, 100))

        håndterSykmelding(Sykmeldingsperiode(11.januar, 22.januar, 100))
        håndterSøknad(Sykdom(11.januar, 22.januar, 100))
        håndterInntektsmelding(arbeidsgiverperioder = listOf(Periode(3.januar, 18.januar)), førsteFraværsdag = 3.januar)
        håndterVilkårsgrunnlag(1, INNTEKT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(8, it.sykdomshistorikk.size)
            assertEquals(4, it.dagtelling[SykHelgedag::class])
            assertEquals(14, it.dagtelling[Sykedag::class])
        }
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
        assertTilstander(
            2,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_UFERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK
        )
    }

    @Test
    fun `ingen historie med to søknader til arbeidsgiver først`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 5.januar, 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(3.januar, 5.januar, 100))

        håndterSykmelding(Sykmeldingsperiode(8.januar, 10.januar, 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(8.januar, 10.januar, 100))

        håndterSykmelding(Sykmeldingsperiode(11.januar, 22.januar, 100))
        håndterSøknad(Sykdom(11.januar, 22.januar, 100))

        håndterInntektsmelding(arbeidsgiverperioder = listOf(Periode(3.januar, 18.januar)), førsteFraværsdag = 3.januar)
        håndterVilkårsgrunnlag(0, INNTEKT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(7, it.sykdomshistorikk.size)
            assertEquals(4, it.dagtelling[SykHelgedag::class])
            assertEquals(14, it.dagtelling[Sykedag::class])
        }
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVSLUTTET_UTEN_UTBETALING,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
        assertTilstander(
            2,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_UFERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK
        )
    }

    @Test
    fun `ingen historie med to søknader (med gap mellom) til arbeidsgiver først`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 4.januar, 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(3.januar, 4.januar, 100))

        håndterSykmelding(Sykmeldingsperiode(8.januar, 10.januar, 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(8.januar, 10.januar, 100))

        håndterSykmelding(Sykmeldingsperiode(11.januar, 22.januar, 100))
        håndterSøknad(Sykdom(11.januar, 22.januar, 100))

        håndterInntektsmelding(arbeidsgiverperioder = listOf(Periode(3.januar, 4.januar), Periode(8.januar, 21.januar)), førsteFraværsdag = 8.januar)
        håndterVilkårsgrunnlag(0, INNTEKT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(7, it.sykdomshistorikk.size)
            assertEquals(4, it.dagtelling[SykHelgedag::class])
            assertEquals(13, it.dagtelling[Sykedag::class])
        }
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD
        )
        assertTilstander(
            2,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_UFERDIG_FORLENGELSE
        )
    }

    @Test
    fun `ingen historie med inntektsmelding, så søknad til arbeidsgiver`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 8.januar, 100))
        håndterInntektsmelding(arbeidsgiverperioder = listOf(Periode(3.januar, 18.januar)), førsteFraværsdag = 3.januar)
        assertNoWarnings(inspektør)
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(3.januar, 8.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertEquals(4, it.dagtelling[Sykedag::class])
            assertEquals(2, it.dagtelling[SykHelgedag::class])
        }
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
    }

    @Test
    fun `ingen historie med Søknad først`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertEquals(18, it.dagtelling[Sykedag::class])
            assertEquals(6, it.dagtelling[SykHelgedag::class])
        }
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTrue(observatør.utbetalteVedtaksperioder.contains(inspektør.vedtaksperiodeId(0)))
    }

    @Test
    fun `søknad sendt etter 3 mnd`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSøknad(Sykdom(3.januar, 26.januar, 100), sendtTilNav = 1.mai)
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterUtbetalingsgodkjenning(0, true)
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertNull(it.dagtelling[Sykedag::class])
            assertEquals(6, it.dagtelling[SykHelgedag::class])
            assertDoesNotThrow { it.arbeidsgiver.nåværendeTidslinje() }
            assertTrue(it.utbetalingslinjer(0).isEmpty())
            TestTidslinjeInspektør(it.utbetalingstidslinjer(0)).also { tidslinjeInspektør ->
                assertEquals(7, tidslinjeInspektør.dagtelling[ForeldetDag::class])
                assertEquals(2, tidslinjeInspektør.dagtelling[NavHelgDag::class])
                assertEquals(16, tidslinjeInspektør.dagtelling[ArbeidsgiverperiodeDag::class])
                assertEquals(1, tidslinjeInspektør.dagtelling[Arbeidsdag::class])
            }
        }
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVVENTER_GODKJENNING, AVSLUTTET
        )
    }

    @Test
    fun `gap historie før inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterYtelser(0, Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(1.desember(2017), 15.desember(2017), 15000, 100, ORGNUMMER))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertEquals(18, it.dagtelling[Sykedag::class])
            assertEquals(6, it.dagtelling[SykHelgedag::class])
        }
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_INNTEKTSMELDING_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTrue(observatør.utbetalteVedtaksperioder.contains(inspektør.vedtaksperiodeId(0)))
    }

    @Test
    fun `no-gap historie før inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterYtelser(0, Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(
            1.desember(2017),
            16.desember(2017),
            15000,
            100,
            ORGNUMMER
        ))
        inspektør.also {
            assertNoErrors(it)
            assertTrue(it.personLogg.hasOnlyInfoAndNeeds())
            assertMessages(it)
            assertFalse(it.inntekter.isEmpty())
            assertNotNull(it.inntektshistorikk.inntekt(2.januar))
            assertEquals(2, it.sykdomshistorikk.size)
            assertEquals(18, it.dagtelling[Sykedag::class])
            assertEquals(6, it.dagtelling[SykHelgedag::class])
        }
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_INNTEKTSMELDING_FERDIG_GAP)
    }

    @Test
    fun `ingen nav utbetaling kreves, blir automatisk behandlet og avsluttet`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 5.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 5.januar, 100))
        håndterYtelser(0)   // No history
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertFalse(hendelselogg.hasErrors())

        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)), 3.januar)
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history

        assertTrue(person.aktivitetslogg.logg(inspektør.vedtaksperioder(0)).hasOnlyInfoAndNeeds())
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_INNTEKTSMELDING_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
    }

    @Test
    fun `ikke automatisk behandling hvis warnings`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 5.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 5.januar, 100))
        håndterSøknad(Sykdom(3.januar, 5.januar, 100))
        håndterYtelser(0)   // No history
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertFalse(hendelselogg.hasErrors())

        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)), 3.januar)
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history

        assertFalse(person.aktivitetslogg.logg(inspektør.vedtaksperioder(0)).hasOnlyInfoAndNeeds())
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_INNTEKTSMELDING_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVVENTER_GODKJENNING
        )
    }

    @Test
    fun `To perioder med opphold`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertTrue(hendelselogg.hasMessages(), hendelselogg.toString())
        håndterInntektsmeldingMedValidering(1, listOf(Periode(1.februar, 16.februar)))
        håndterSøknadMedValidering(1, Sykdom(1.februar, 23.februar, 100))
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `Kiler tilstand i uferdig venter for inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(1, Sykdom(1.februar, 23.februar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertTrue(hendelselogg.hasMessages(), hendelselogg.toString())
        håndterYtelser(1)   // No history
        håndterInntektsmeldingMedValidering(1, listOf(Periode(1.februar, 16.februar)))
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_UFERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_INNTEKTSMELDING_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `Kilt etter søknad og inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(1, Sykdom(1.februar, 23.februar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(1, listOf(Periode(1.februar, 16.februar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertTrue(hendelselogg.hasMessages(), hendelselogg.toString())
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_UFERDIG_GAP,
            AVVENTER_UFERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `Kilt etter inntektsmelding og søknad`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterInntektsmeldingMedValidering(1, listOf(Periode(1.februar, 16.februar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(1, Sykdom(1.februar, 23.februar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertTrue(hendelselogg.hasMessages(), hendelselogg.toString())
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
        assertTilstander(
            1,
            START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVVENTER_SØKNAD_UFERDIG_GAP, AVVENTER_UFERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
    }

    @Test
    fun `Kilt etter inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterInntektsmeldingMedValidering(1, listOf(Periode(1.februar, 16.februar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertTrue(hendelselogg.hasMessages(), hendelselogg.toString())
        håndterSøknadMedValidering(1, Sykdom(1.februar, 23.februar, 100))
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_SØKNAD_UFERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `forlengelse av infotrygd uten inntektsopplysninger`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 23.februar, 100))
        håndterSøknad(Sykdom(1.februar, 23.februar, 100))
        håndterYtelser(0, Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(
            1.januar,
            31.januar,
            INNTEKT.toInt(),
            100,
            ORGNUMMER
        ), inntektshistorikk = emptyList())
        assertForkastetPeriodeTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, TIL_INFOTRYGD)
    }

    @Test
    fun `forlengelse av infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 23.februar, 100))
        håndterSøknad(Sykdom(1.februar, 23.februar, 100))
        håndterYtelser(0, Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(
            1.januar,
            31.januar,
            INNTEKT.toInt(),
            100,
            ORGNUMMER
        ))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0, Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(
            1.januar,
            31.januar,
            INNTEKT.toInt(),
            100,
            ORGNUMMER
        ))
        håndterSimulering(0)

        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING
        )
    }

    @Test
    fun `første fraværsdato fra inntektsmelding er ulik utregnet første fraværsdato`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)), 4.januar)
        inspektør.also { assertTrue(it.personLogg.hasWarnings()) }
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP)
    }

    @Test
    fun `første fraværsdato fra inntektsmelding er ulik utregnet første fraværsdato for påfølgende perioder`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(27.januar, 7.februar, 100))
        håndterInntektsmeldingMedValidering(
            0,
            listOf(Periode(3.januar, 18.januar)),
            3.januar,
            listOf(Periode(27.januar, 27.januar))
        )
        inspektør.also { assertFalse(it.personLogg.hasWarnings()) }
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_SØKNAD_UFERDIG_FORLENGELSE)
    }

    @Test
    fun `første fraværsdato i inntektsmelding er utenfor perioden`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)), 27.januar)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP)
    }

    @Test
    fun `første fraværsdato i inntektsmelding, før søknad, er utenfor perioden`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)), 27.januar)
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP
        )
    }

    @Test
    fun `Sammenblandede hendelser fra forskjellige perioder med søknad først`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(1.februar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(1, listOf(Periode(1.februar, 16.februar)))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        forventetEndringTeller++
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        assertTrue(hendelselogg.hasMessages(), hendelselogg.toString())
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertNotNull(it.maksdato(0))
            assertNotNull(it.maksdato(1))
            assertEquals(10_017, it.totalBeløp[0])
            assertEquals(10_017, it.nettoBeløp[0])
            assertEquals(34_344, it.totalBeløp[1])
            assertEquals(24_327, it.nettoBeløp[1])
        }
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_UFERDIG_GAP,
            AVVENTER_UFERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `To forlengelser som forlenger utbetaling fra infotrygd skal ha samme maksdato`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 31.januar, 100))
        håndterSøknad(Sykdom(3.januar, 31.januar, 100))
        håndterYtelser(0, Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(
            1.januar,
            2.januar,
            INNTEKT.toInt(),
            100,
            ORGNUMMER
        ))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0, Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(
            1.januar,
            2.januar,
            INNTEKT.toInt(),
            100,
            ORGNUMMER
        ))
        håndterSimulering(0)
        forventetEndringTeller++
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 23.februar, 100))
        håndterSøknad(Sykdom(1.februar, 23.februar, 100))
        håndterYtelser(1, Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(
            1.januar,
            2.januar,
            INNTEKT.toInt(),
            100,
            ORGNUMMER
        ))
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertEquals(inspektør.maksdato(0), inspektør.maksdato(1))
    }

    @Test
    fun `fortsettelse av Infotrygd-perioder skal ikke generere utbetalingslinjer for Infotrygd-periode`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 31.januar, 100))
        håndterSøknad(Sykdom(3.januar,  31.januar, 100))
        håndterYtelser(0, Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(1.januar, 2.januar, INNTEKT.toInt(), 100, ORGNUMMER))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0, Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(1.januar, 2.januar, INNTEKT.toInt(), 100, ORGNUMMER))

        inspektør.also {
            assertEquals(3.januar, it.arbeidsgiverOppdrag[0][0].fom)
        }
    }

    @Test
    fun `To tilstøtende perioder søknad først`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(29.januar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)

        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }


    @Test
    fun `Venter å på bli kilt etter søknad og inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 7.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(8.januar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 7.januar, 100))
        håndterSøknadMedValidering(1, Sykdom(8.januar, 23.februar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history

        håndterUtbetalingsgodkjenning(0, true)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_GODKJENNING, AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_SØKNAD_UFERDIG_FORLENGELSE,
            AVVENTER_UFERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `søknad til arbeidsgiver etter inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 7.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(8.januar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(8.januar, 23.februar, 100))

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(38, it.dagtelling[Sykedag::class])
            assertEquals(14, it.dagtelling[SykHelgedag::class])
        }
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_SØKNAD_UFERDIG_FORLENGELSE,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD
        )
    }

    @Test
    fun `Venter på å bli kilt etter inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 7.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(8.januar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 7.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterUtbetalingsgodkjenning(0, true)

        håndterSøknadMedValidering(1, Sykdom(8.januar, 23.februar, 100))
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_GODKJENNING, AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_SØKNAD_UFERDIG_FORLENGELSE,
            MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `Fortsetter før andre søknad`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(
            vedtaksperiodeIndex = 0,
            arbeidsgiverperioder = listOf(Periode(3.januar, 18.januar)),
            førsteFraværsdag = 3.januar
        )
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterSøknadMedValidering(1, Sykdom(29.januar, 23.februar, 100))
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
            assertEquals(3.januar, it.førsteFraværsdag(0))
            assertEquals(3.januar, it.førsteFraværsdag(1))
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
        assertTilstander(
            1,
            START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
    }

    @Test
    fun `To tilstøtende perioder der den første er utbetalt`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(29.januar, 23.februar, 100))
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `To tilstøtende perioder inntektsmelding først`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 7.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(8.januar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(8.januar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 7.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history

        håndterUtbetalingsgodkjenning(0, true)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_GODKJENNING, AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_UFERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `To tilstøtende perioder der den første er i utbetaling feilet`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AVVIST)

        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(29.januar, 23.februar, 100))

        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, UTBETALING_FEILET
        )
        assertTilstander(
            1,
            START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE
        )
    }

    @Test
    fun `når utbetaling er ikke godkjent skal påfølgende perioder også kastes ut`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(29.januar, 23.februar, 100))
        håndterUtbetalingsgodkjenning(0, false)

        assertForkastetPeriodeTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, TIL_INFOTRYGD)
    }

    @Test
    fun `kan ikke forlenge en periode som er gått TilInfotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(29.januar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, false) // går til TilInfotrygd

        assertForkastetPeriodeTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, TIL_INFOTRYGD)
    }

    @Test
    fun `ignorer inntektsmeldinger på påfølgende perioder`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(1, Sykdom(29.januar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(1, listOf(Periode(3.januar, 18.januar)))
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)


        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
        assertTilstander(
            1,
            START, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
    }

    @Test
    fun `kiler bare andre periode og ikke tredje periode i en rekke`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 23.februar, 100))
        håndterSykmelding(Sykmeldingsperiode(1.mars, 28.mars, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(1, Sykdom(1.februar, 23.februar, 100))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(
            1,
            START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVVENTER_INNTEKTSMELDING_UFERDIG_GAP, AVVENTER_GAP
        )

        assertTilstander(
            2,
            START, MOTTATT_SYKMELDING_UFERDIG_GAP
        )
    }

    @Test
    fun `Sykmelding med gradering`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 50))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 50, 50))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP, AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING
        )
    }

    @Test
    fun `dupliserte hendelser produserer bare advarsler`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)))
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterSøknad(Sykdom(3.januar, 26.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterInntektsmelding(listOf(Periode(3.januar, 18.januar)))
        håndterSøknad(Sykdom(3.januar, 26.januar, 100))
        håndterInntektsmelding(listOf(Periode(3.januar, 18.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterInntektsmelding(listOf(Periode(3.januar, 18.januar)))
        håndterSøknad(Sykdom(3.januar, 26.januar, 100))
        inspektør.also {
            assertNoErrors(it)
            assertWarnings(it)
            assertEquals(INNTEKT.toBigDecimal(), it.inntektshistorikk.inntekt(2.januar))
            assertEquals(3, it.sykdomshistorikk.size)
            assertEquals(18, it.dagtelling[Sykedag::class])
            assertEquals(6, it.dagtelling[SykHelgedag::class])
        }
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTrue(observatør.utbetalteVedtaksperioder.contains(inspektør.vedtaksperiodeId(0)))
    }

    @Test
    fun `Sykmelding i omvendt rekkefølge`() {
        håndterSykmelding(Sykmeldingsperiode(10.januar, 20.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(3.januar, 5.januar, 100))
        assertForkastetPeriodeTilstander(0, START, TIL_INFOTRYGD)
        assertForkastetPeriodeTilstander(1, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
    }

    @Test
    fun `Inntektsmelding vil ikke utvide vedtaksperiode til tidligere vedtaksperiode`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)), 3.januar)
        inspektør.also {
            assertNoErrors(it)
            assertNoWarnings(it)
        }
        håndterSøknadMedValidering(0, Sykdom(3.januar, 26.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        forventetEndringTeller++
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 23.februar, 100))
        håndterInntektsmeldingMedValidering(1, listOf(Periode(16.januar, 16.februar))) // Touches prior periode
        assertNoErrors(inspektør)

        håndterSøknadMedValidering(1, Sykdom(1.februar, 23.februar, 100))
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        assertNoErrors(inspektør)

        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
        assertTilstander(
            1,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
    }

    @Test
    fun `Ber om inntektsmelding når vi ankommer AVVENTER_INNTEKTSMELDING_FERDIG_GAP`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100))
        håndterYtelser(0)

        assertNoErrors(inspektør)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_INNTEKTSMELDING_FERDIG_GAP)
        assertEquals(1, observatør.manglendeInntektsmeldingVedtaksperioder.size)
    }

    @Test
    fun `Ber om inntektsmelding når vi ankommer AVVENTER_INNTEKTSMELDING_UFERDIG_GAP`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 20.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100))

        assertNoErrors(inspektør)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVVENTER_INNTEKTSMELDING_UFERDIG_GAP)
        assertEquals(1, observatør.manglendeInntektsmeldingVedtaksperioder.size)
    }

    @Test
    fun `Ber om inntektsmelding når vi ankommer AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 20.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(21.januar, 28.februar, 100))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100))

        assertNoErrors(inspektør)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE)
        assertEquals(1, observatør.manglendeInntektsmeldingVedtaksperioder.size)
    }

    @Test
    fun `søknad med papirsykmelding`() {
        håndterSykmelding(Sykmeldingsperiode(21.januar, 28.februar, 100))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100), Søknad.Søknadsperiode.Papirsykmelding(1.januar, 20.januar))
        assertForkastetPeriodeTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
    }

    @Test
    fun `vedtaksperiode med søknad som går til infotrygd ber om inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(21.januar, 28.februar, 100))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100))
        håndterYtelser(0, Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(18.januar, 21.januar, 15000, 100, ORGNUMMER)) // -> TIL_INFOTRYGD

        assertEquals(1, observatør.manglendeInntektsmeldingVedtaksperioder.size)
    }

    @Test
    fun `vedtaksperiode uten søknad som går til infotrygd ber ikke om inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(21.januar, 28.februar, 100))
        håndterPåminnelse(0, MOTTATT_SYKMELDING_FERDIG_GAP)

        assertEquals(0, observatør.manglendeInntektsmeldingVedtaksperioder.size)
    }

    @Test
    fun `refusjon opphører i perioden`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)), refusjon = Triple(14.januar, INNTEKT, emptyList()))
        assertForkastetPeriodeTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
    }

    @Test
    fun `refusjon endres i perioden`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 26.januar, 100))
        håndterInntektsmeldingMedValidering(0, listOf(Periode(3.januar, 18.januar)), refusjon = Triple(null, INNTEKT, listOf(14.januar)))
        assertForkastetPeriodeTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
    }

    @Test
    fun `data for vilkårsvurdering propageres til tilstøtende`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100))
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100))
        håndterSykmelding(Sykmeldingsperiode(5.april, 30.april, 100))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100))
        håndterInntektsmelding(arbeidsgiverperioder = listOf(Periode(1.januar, 16.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)

        assertNotNull(inspektør.vilkårsgrunnlag(0))
        assertEquals(inspektør.vilkårsgrunnlag(0), inspektør.vilkårsgrunnlag(1))
        assertEquals(inspektør.vilkårsgrunnlag(0), inspektør.vilkårsgrunnlag(2))
        assertThrows<AssertionFailedError> {
            assertNull(inspektør.vilkårsgrunnlag(3))
        }
    }

    @Test
    fun `data for vilkårsvurdering hentes fra foregående`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100))
        håndterInntektsmelding(arbeidsgiverperioder = listOf(Periode(1.januar, 16.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100))
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100))
        håndterSykmelding(Sykmeldingsperiode(5.april, 30.april, 100))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100))
        håndterSøknad(Sykdom(5.april, 30.april, 100))

        assertNotNull(inspektør.vilkårsgrunnlag(0))
        assertEquals(inspektør.vilkårsgrunnlag(0), inspektør.vilkårsgrunnlag(1))
        assertEquals(inspektør.vilkårsgrunnlag(0), inspektør.vilkårsgrunnlag(2))
        assertThrows<AssertionFailedError> {
            assertNull(inspektør.vilkårsgrunnlag(3))
        }
    }

    @Test
    fun `perioden henter vilkårsgrunnlag selv om tidligere periode har lest inntektsmelding når det er gap mellom periodene`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 4.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(8.januar, 15.januar, 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(1.januar, 4.januar, 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(8.januar, 15.januar, 100))
        håndterInntektsmelding(arbeidsgiverperioder = listOf(Periode(1.januar, 15.januar)))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterVilkårsgrunnlag(1, INNTEKT)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVSLUTTET_UTEN_UTBETALING, AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD, AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVSLUTTET_UTEN_UTBETALING, AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD, AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING)
        assertNotNull(inspektør.vilkårsgrunnlag(0))
        assertNotNull(inspektør.vilkårsgrunnlag(1))
        assertNotEquals(inspektør.vilkårsgrunnlag(0), inspektør.vilkårsgrunnlag(1))
    }

    @Test
    fun `avvis sykmelding over 6 måneder gammel`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 15.februar, 100))
        person.håndter(sentSykmelding(Sykmeldingsperiode(1.januar, 15.januar, 100)))
        assertTrue(inspektør.personLogg.hasErrors())
        assertEquals(1, inspektør.sykdomshistorikk.size)
    }

    @Test
    fun `Maksdato og antall gjenstående dager beregnes riktig når det er ferie sist i perioden`() {
        håndterSykmelding(Sykmeldingsperiode(22.juni(2020), 11.juli(2020), 100))
        håndterSøknad(Sykdom(22.juni(2020), 11.juli(2020), 100), Søknad.Søknadsperiode.Ferie(6.juli(2020), 11.juli(2020)))
        håndterYtelser(0,
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(7.august(2019), 7.august(2019), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(8.august(2019), 4.september(2019), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(5.september(2019), 20.september(2019), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(21.september(2019), 2.november(2019), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(3.november(2019), 3.februar(2020), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(4.februar(2020), 28.februar(2020), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(29.februar(2020), 27.mars(2020), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(28.mars(2020), 26.april(2020), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(27.april(2020), 25.mai(2020), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(26.mai(2020), 21.juni(2020), 2304, 100, ORGNUMMER),
            inntektshistorikk = listOf(Utbetalingshistorikk.Inntektsopplysning(7.august(2019), 50005.0, ORGNUMMER, true))
        )
        håndterVilkårsgrunnlag(0, 50005.0)
        håndterYtelser(0,
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(7.august(2019), 7.august(2019), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(8.august(2019), 4.september(2019), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(5.september(2019), 20.september(2019), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(21.september(2019), 2.november(2019), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(3.november(2019), 3.februar(2020), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(4.februar(2020), 28.februar(2020), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(29.februar(2020), 27.mars(2020), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(28.mars(2020), 26.april(2020), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(27.april(2020), 25.mai(2020), 2304, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(26.mai(2020), 21.juni(2020), 2304, 100, ORGNUMMER),
            inntektshistorikk = listOf(Utbetalingshistorikk.Inntektsopplysning(7.august(2019), 50005.0, ORGNUMMER, true))
        )
        håndterSimulering(0)
        assertEquals(10, inspektør.gjenståendeSykedager(0))
        assertEquals(24.juli(2020), inspektør.maksdato(0))
    }
}
