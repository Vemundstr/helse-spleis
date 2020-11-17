package no.nav.helse.spleis.e2e

import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.testhelpers.februar
import no.nav.helse.testhelpers.januar
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class StatslønnWarningTest : AbstractEndToEndTest() {

    @Test
    fun `Warning ved statslønn`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar, 16.januar)))
        håndterSøknad(Sykdom(1.januar, 31.januar, gradFraSykmelding = 100), sendtTilNav = 18.februar)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(vedtaksperiodeId = 1.vedtaksperiode, statslønn = true)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertTrue(
            inspektør.personLogg.warn().toString()
                .contains("Det er lagt inn statslønn i Infotrygd, undersøk at sykepengegrunnlaget er fastsatt riktig.")
        ) {
            inspektør.personLogg.toString()
        }
    }

}
