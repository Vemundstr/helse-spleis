package no.nav.helse.spleis.e2e

import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DødIPeriodenTest : AbstractEndToEndTest() {

    @Test
    fun `Dager etter dødsdato avvises`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, dødsdato = 18.januar)
        assertEquals(9, inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.utbetalingstidslinje.inspektør.avvistDagTeller)
    }

    @Test
    fun `Ingen dager avvises når dødsdato er etter perioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, dødsdato = 1.februar)
        assertEquals(0, inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.utbetalingstidslinje.inspektør.avvistDagTeller)
    }

    @Test
    fun `Alle dager avvises når dødsdato er før perioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode, dødsdato = 31.desember(2017))
        inspektør.vedtaksperioder(1.vedtaksperiode).inspektør.utbetalingstidslinje.inspektør.also {
            assertEquals(11, it.avvistDagTeller)
            assertEquals(16, it.arbeidsgiverperiodeDagTeller)
            assertEquals(4, it.navHelgDagTeller)
        }
    }

}
