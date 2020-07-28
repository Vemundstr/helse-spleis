package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.*
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.person.Periodetype
import no.nav.helse.person.TilstandType
import no.nav.helse.testhelpers.april
import no.nav.helse.testhelpers.februar
import no.nav.helse.testhelpers.januar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PeriodetypeTest : AbstractEndToEndTest() {

    @Test
    fun `periodetype settes til førstegangs hvis foregående ikke hadde utbetalingsdager`() {
        håndterSykmelding(Sykmeldingsperiode(28.januar(2020), 10.februar(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(11.februar(2020), 21.februar(2020), 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(28.januar(2020), 10.februar(2020), 100))
        håndterSøknad(Sykdom(11.februar(2020), 21.februar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(28.januar(2020), 12.februar(2020))),
            førsteFraværsdag = 28.januar(2020)
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(2.vedtaksperiode)   // No history
        håndterSimulering(2.vedtaksperiode)

        assertTilstander(
            1.vedtaksperiode,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVSLUTTET_UTEN_UTBETALING,
            TilstandType.AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            TilstandType.AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
        assertTilstander(
            2.vedtaksperiode,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            TilstandType.AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            TilstandType.AVVENTER_UFERDIG_FORLENGELSE,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING
        )

        assertEquals(
            Periodetype.FØRSTEGANGSBEHANDLING.name,
            hendelselogg.behov().first().detaljer()["periodetype"]
        )
    }

    @Test
    fun `periodetype settes til førstegangs hvis foregående ikke hadde utbetalingsdager - ikke arbeidsgiversøknad`() {
        håndterSykmelding(Sykmeldingsperiode(28.januar(2020), 10.februar(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(11.februar(2020), 21.februar(2020), 100))
        håndterSøknad(Sykdom(28.januar(2020), 10.februar(2020), 100))
        håndterSøknad(Sykdom(11.februar(2020), 21.februar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(28.januar(2020), 12.februar(2020))),
            førsteFraværsdag = 28.januar(2020)
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        assertTilstander(
            1.vedtaksperiode,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_GAP,
            TilstandType.AVVENTER_VILKÅRSPRØVING_GAP,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
        assertTilstander(
            2.vedtaksperiode,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            TilstandType.AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            TilstandType.AVVENTER_UFERDIG_FORLENGELSE,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING
        )

        assertEquals(
            Periodetype.FØRSTEGANGSBEHANDLING.name,
            hendelselogg.behov().first().detaljer()["periodetype"]
        )
    }

    @Test
    fun `periodetype er overgang fra Infotrygd hvis foregående ble behandlet i Infotrygd`() {
        val historikk = Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(3.januar, 26.januar, 1000, 100, ORGNUMMER)

        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(1.vedtaksperiode, Sykdom(29.januar, 23.februar, 100))
        håndterUtbetalingshistorikk(1.vedtaksperiode, historikk)
        håndterYtelser(1.vedtaksperiode, historikk)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode, historikk)
        håndterSimulering(1.vedtaksperiode)

        assertTilstander(
            1.vedtaksperiode,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_GAP,
            TilstandType.AVVENTER_VILKÅRSPRØVING_GAP,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING
        )
        assertEquals(
            Periodetype.OVERGANG_FRA_IT.name,
            hendelselogg.behov().first().detaljer()["periodetype"]
        )
    }

    @Test
    fun `periodetype er forlengelse fra Infotrygd hvis førstegangsbehandlingen skjedde i Infotrygd`() {
        val historikk = Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(3.januar, 26.januar, 1000, 100, ORGNUMMER)

        håndterSykmelding(Sykmeldingsperiode(29.januar, 23.februar, 100))
        håndterSøknadMedValidering(1.vedtaksperiode, Sykdom(29.januar, 23.februar, 100))
        håndterUtbetalingshistorikk(1.vedtaksperiode, historikk)
        håndterYtelser(1.vedtaksperiode, historikk)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode, historikk)
        håndterSimulering(1.vedtaksperiode)

        assertEquals(
            Periodetype.OVERGANG_FRA_IT.name,
            hendelselogg.behov().first().detaljer()["periodetype"]
        )

        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Sykmeldingsperiode(26.februar, 15.april, 100))
        håndterSøknadMedValidering(2.vedtaksperiode, Sykdom(26.februar, 15.april, 100))
        håndterUtbetalingshistorikk(2.vedtaksperiode, historikk)
        håndterYtelser(2.vedtaksperiode, historikk)
        håndterSimulering(2.vedtaksperiode)

        assertEquals(
            Periodetype.INFOTRYGDFORLENGELSE.name,
            hendelselogg.behov().first().detaljer()["periodetype"]
        )
    }

    @Test
    fun `periodetype settes til førstegangs hvis foregående ikke hadde utbetalingsdager pga lav sykdomsgrad`() {
        håndterSykmelding(Sykmeldingsperiode(20.januar(2020), 10.februar(2020), 15))
        håndterSykmelding(Sykmeldingsperiode(11.februar(2020), 21.februar(2020), 100))
        håndterSøknad(Sykdom(20.januar(2020), 10.februar(2020), 15))
        håndterSøknad(Sykdom(11.februar(2020), 21.februar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(20.januar(2020), 4.februar(2020))),
            førsteFraværsdag = 20.januar(2020)
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)   // No history
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterYtelser(2.vedtaksperiode)   // No history
        håndterSimulering(2.vedtaksperiode)

        assertTilstander(
            1.vedtaksperiode,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_GAP,
            TilstandType.AVVENTER_VILKÅRSPRØVING_GAP,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_GODKJENNING,
            TilstandType.AVSLUTTET
        )
        assertTilstander(
            2.vedtaksperiode,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            TilstandType.AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING
        )

        assertEquals(
            Periodetype.FØRSTEGANGSBEHANDLING.name,
            hendelselogg.behov().first().detaljer()["periodetype"]
        )
    }

}
