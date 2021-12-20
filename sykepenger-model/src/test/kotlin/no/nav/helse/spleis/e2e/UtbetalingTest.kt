package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.SendtSøknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.testhelpers.februar
import no.nav.helse.testhelpers.januar
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class UtbetalingTest : AbstractEndToEndTest() {
    val ANNET_ORGNUMMER = "foo"

    @Test
    fun `Utbetaling endret får rett organisasjonsnummer ved overlappende sykemelding`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser(
            1.vedtaksperiode,
            ArbeidsgiverUtbetalingsperiode(ANNET_ORGNUMMER.toString(), 1.januar(2016), 31.januar(2016), 100.prosent, 1000.daglig),
            inntektshistorikk = listOf(
                Inntektsopplysning(ANNET_ORGNUMMER.toString(), 1.januar(2016), 1000.daglig, true)
            )
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(2.februar, 28.februar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))

        assertEquals(ORGNUMMER, observatør.utbetaltEndretEventer.single().orgnummer())
    }

    @Test
    fun `første periode er kun arbeidsgiverperiode og helg`() {
        håndterSykmelding(Sykmeldingsperiode(4.januar, 21.januar, 100.prosent))
        håndterSøknad(Sykdom(4.januar, 21.januar, 100.prosent))
        håndterInntektsmelding(listOf(4.januar til 19.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSykmelding(Sykmeldingsperiode(22.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(22.januar, 31.januar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt(2.vedtaksperiode)
        val første = inspektør.utbetaling(0)
        val andre = inspektør.utbetaling(1)
        assertEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
    }
}
