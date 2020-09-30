package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.Institusjonsopphold.Institusjonsoppholdsperiode
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.person.TilstandType
import no.nav.helse.testhelpers.desember
import no.nav.helse.testhelpers.februar
import no.nav.helse.testhelpers.januar
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class InstitusjonsoppholdBehovTest : AbstractEndToEndTest() {

    @Test
    fun `Periode for person der det ikke foreligger institusjonsopphold blir behandlet og sendt til godkjenning`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020), 100))
        håndterSøknadMedValidering(1.vedtaksperiode, Sykdom(1.januar(2020), 31.januar(2020), 100))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar(2020), 16.januar(2020))))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode, institusjonsoppholdsperioder = emptyList())
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertTilstander(
            1.vedtaksperiode,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_GAP,
            TilstandType.AVVENTER_VILKÅRSPRØVING_GAP,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING,
            TilstandType.TIL_UTBETALING,
            TilstandType.AVSLUTTET
        )
    }

    @Test
    fun `Periode som overlapper med institusjonsopphold blir sendt til Infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020), 100))
        håndterSøknadMedValidering(1.vedtaksperiode, Sykdom(1.januar(2020), 31.januar(2020), 100))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar(2020), 16.januar(2020))))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode, institusjonsoppholdsperioder = listOf(1.januar(2020) til 31.januar(2020)))

        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_GAP,
            TilstandType.AVVENTER_VILKÅRSPRØVING_GAP,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.TIL_INFOTRYGD
        )
    }

    @Test
    fun `Periode som overlapper med institusjonsopphold i starten av perioden blir sendt til Infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020), 100))
        håndterSøknadMedValidering(1.vedtaksperiode, Sykdom(1.januar(2020), 31.januar(2020), 100))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar(2020), 16.januar(2020))))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode, institusjonsoppholdsperioder = listOf(1.desember(2019) til 1.januar(2020)))

        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_GAP,
            TilstandType.AVVENTER_VILKÅRSPRØVING_GAP,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.TIL_INFOTRYGD
        )
    }

    @Test
    fun `Periode som overlapper med institusjonsopphold i slutten av perioden blir sendt til Infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020), 100))
        håndterSøknadMedValidering(1.vedtaksperiode, Sykdom(1.januar(2020), 31.januar(2020), 100))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar(2020), 16.januar(2020))))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode, institusjonsoppholdsperioder = listOf(31.januar(2020) til 14.februar(2020)))

        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_GAP,
            TilstandType.AVVENTER_VILKÅRSPRØVING_GAP,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.TIL_INFOTRYGD
        )
    }

    @Test
    fun `Periode som ikke overlapper med institusjonsopphold blir behandlet og sendt til godkjenning`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020), 100))
        håndterSøknadMedValidering(1.vedtaksperiode, Sykdom(1.januar(2020), 31.januar(2020), 100))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar(2020), 16.januar(2020))))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(
            1.vedtaksperiode,
            institusjonsoppholdsperioder = listOf(
                1.desember(2019) til 31.desember(2019),
                1.februar(2020) til 29.februar(2020)
            )
        )
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertTilstander(
            1.vedtaksperiode,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_GAP,
            TilstandType.AVVENTER_VILKÅRSPRØVING_GAP,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING,
            TilstandType.TIL_UTBETALING,
            TilstandType.AVSLUTTET
        )
    }

    @Test
    fun `Periode som er før fom i institusjonsopphold, uten tom, blir behandlet og sendt til godkjenning`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020), 100))
        håndterSøknadMedValidering(1.vedtaksperiode, Sykdom(1.januar(2020), 31.januar(2020), 100))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar(2020), 16.januar(2020))))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode, institusjonsoppholdsperioder = listOf(1.februar(2020) til null))
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertTilstander(
            1.vedtaksperiode,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_GAP,
            TilstandType.AVVENTER_VILKÅRSPRØVING_GAP,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.AVVENTER_SIMULERING,
            TilstandType.AVVENTER_GODKJENNING,
            TilstandType.TIL_UTBETALING,
            TilstandType.AVSLUTTET
        )
    }

    @Test
    fun `Periode som overlapper med fom i institusjonsopphold, uten tom, blir sendt til Infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020), 100))
        håndterSøknadMedValidering(1.vedtaksperiode, Sykdom(1.januar(2020), 31.januar(2020), 100))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar(2020), 16.januar(2020))))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode, institusjonsoppholdsperioder = listOf(31.januar(2020) til null))

        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_GAP,
            TilstandType.AVVENTER_VILKÅRSPRØVING_GAP,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.TIL_INFOTRYGD
        )
    }

    @Test
    fun `Periode som er etter fom i institusjonsopphold, uten tom, blir sendt til Infotrygd`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020), 100))
        håndterSøknadMedValidering(1.vedtaksperiode, Sykdom(1.januar(2020), 31.januar(2020), 100))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar(2020), 16.januar(2020))))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode, institusjonsoppholdsperioder = listOf(31.desember(2019) til null))

        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            TilstandType.START,
            TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP,
            TilstandType.AVVENTER_GAP,
            TilstandType.AVVENTER_VILKÅRSPRØVING_GAP,
            TilstandType.AVVENTER_HISTORIKK,
            TilstandType.TIL_INFOTRYGD
        )
    }
}

private infix fun LocalDate.til(tom: LocalDate?) = Institusjonsoppholdsperiode(this, tom)
