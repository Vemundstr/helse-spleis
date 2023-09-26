package no.nav.helse.spleis.e2e

import no.nav.helse.april
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class VedtaksperiodeAnnullertEventTest: AbstractEndToEndTest() {

    @Test
    fun `vi sender vedtaksperiode annullert-hendelse når saksbehandler annullerer en vedtaksperiode`() {
        nyttVedtak(1.januar, 31.januar)
        håndterAnnullerUtbetaling()

        assertTrue(observatør.vedtaksperiodeAnnullertEventer.isNotEmpty())
    }

    @Test
    fun `vi sender vedtaksperiode annullert-hendelser når saksbehandler annullerer en vedtaksperiode i et lengre sykdomsforløp`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        nyttVedtak(5.mars, 31.mars)
        håndterAnnullerUtbetaling()

        assertEquals(3, observatør.vedtaksperiodeAnnullertEventer.size)
        assertEquals(1.januar til 31.januar, observatør.vedtaksperiodeAnnullertEventer[0].fom til observatør.vedtaksperiodeAnnullertEventer[0].tom)
        assertEquals(1.februar til 28.februar, observatør.vedtaksperiodeAnnullertEventer[1].fom til observatør.vedtaksperiodeAnnullertEventer[1].tom)
        assertEquals(5.mars til 31.mars, observatør.vedtaksperiodeAnnullertEventer[2].fom til observatør.vedtaksperiodeAnnullertEventer[2].tom)
    }

    @Test
    fun `også langt gap`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        nyttVedtak(1.april, 30.april)
        håndterAnnullerUtbetaling()

        assertEquals(1, observatør.vedtaksperiodeAnnullertEventer.size)
        assertEquals(1.april til 30.april, observatør.vedtaksperiodeAnnullertEventer[0].fom til observatør.vedtaksperiodeAnnullertEventer[0].tom)
    }

    @Test
    fun `arbeid ikke gjenopptatt`() {
        nyttVedtak(1.januar, 31.januar)

        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars, 31.mars, 100.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            førsteFraværsdag = 1.mars,
            begrunnelseForReduksjonEllerIkkeUtbetalt = "FerieEllerAvspasering"
        )
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        nullstillTilstandsendringer()

        håndterOverstyrTidslinje((1.februar til 28.februar).map { ManuellOverskrivingDag(it, Dagtype.ArbeidIkkeGjenopptattDag) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertEquals(0, observatør.vedtaksperiodeAnnullertEventer.size)
    }
}