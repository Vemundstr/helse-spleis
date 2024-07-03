package no.nav.helse.spleis.e2e

import no.nav.helse.august
import no.nav.helse.februar
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.utbetalingslinjer.Utbetalingtype.ANNULLERING
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EnArbeidsgiverTest : AbstractEndToEndTest() {

    @Test
    fun `en sprø case som ender opp med å trekker masse penger uten at vedtaksperiodene får vite om det`(){
        nyttVedtak(1.august(2017) til 5.januar) // Lang for pengenes skyld
        val korrelasjonsIdAugust2017 = inspektør.utbetalinger.single().inspektør.korrelasjonsId
        val førsteUtbetalingsdag = inspektør.utbetalinger.single().inspektør.arbeidsgiverOppdrag[0].inspektør.fom

        håndterSøknad(Sykdom(6.januar, 4.februar, 100.prosent), Arbeid(6.januar, 4.februar))
        håndterSøknad(Sykdom(5.februar, 24.februar, 100.prosent), Ferie(5.februar, 11.februar))
        håndterInntektsmelding(listOf(5.februar til 20.februar))
        håndterVilkårsgrunnlag(3.vedtaksperiode)
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()

        // Her ser jo alt fint & flott ut :)
        assertEquals(5.februar, inspektør.skjæringstidspunkt(3.vedtaksperiode))
        assertEquals(2, inspektør.utbetalinger.size)
        assertEquals(1.august(2017) til 5.januar, inspektør.utbetalinger[0].inspektør.periode)
        assertEquals(korrelasjonsIdAugust2017,inspektør.utbetalinger[0].inspektør.korrelasjonsId)
        val korrelasjonsIdFebruar2018 = inspektør.utbetalinger[1].inspektør.korrelasjonsId
        assertEquals(5.februar til 24.februar, inspektør.utbetalinger[1].inspektør.periode)
        assertNotEquals(korrelasjonsIdAugust2017, korrelasjonsIdFebruar2018)

        // Her begynner det å balle på seg..
        håndterInntektsmelding(listOf(12.februar til 27.februar))
        assertEquals(12.februar, inspektør.skjæringstidspunkt(3.vedtaksperiode))

        håndterVilkårsgrunnlag(3.vedtaksperiode)
        håndterYtelser(3.vedtaksperiode)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()
        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET)

        assertEquals(4, inspektør.utbetalinger.size)
        assertEquals(korrelasjonsIdFebruar2018, inspektør.utbetalinger[2].inspektør.korrelasjonsId)
        assertEquals(ANNULLERING, inspektør.utbetalinger[2].inspektør.type)
        assertEquals(korrelasjonsIdAugust2017, inspektør.utbetalinger[3].inspektør.korrelasjonsId)
        assertEquals(1.august(2017) til 24.februar, inspektør.utbetalinger[3].inspektør.periode)

        // Og nå stokker det seg skikkelig til..
        nullstillTilstandsendringer()
        håndterSøknad(Sykdom(25.februar, 15.mars, 100.prosent))
        håndterYtelser(4.vedtaksperiode)

        assertEquals(5, inspektør.utbetalinger.size)
        val utbetalingenSomTrekkerPenger = inspektør.utbetalinger[4]
        assertEquals(korrelasjonsIdAugust2017, utbetalingenSomTrekkerPenger.inspektør.korrelasjonsId)
        assertEquals(2, utbetalingenSomTrekkerPenger.inspektør.arbeidsgiverOppdrag.size)
        val opphørslinje = utbetalingenSomTrekkerPenger.inspektør.arbeidsgiverOppdrag[0]
        assertEquals(førsteUtbetalingsdag, opphørslinje.inspektør.datoStatusFom)
        assertEquals("OPPH", opphørslinje.inspektør.statuskode)
        assertEquals(-82998, utbetalingenSomTrekkerPenger.inspektør.nettobeløp)

        val utbetalingslinje = utbetalingenSomTrekkerPenger.inspektør.arbeidsgiverOppdrag[1]
        assertEquals(28.februar, utbetalingslinje.inspektør.fom)
        assertEquals(15.mars, utbetalingslinje.inspektør.tom)

        // 1 og 3 vedtaksperiode har fått utbetalingene sine opphørt uten at de har beveget på seg :/
        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertFalse(utbetalingenSomTrekkerPenger.inspektør.utbetalingId in utbetalingIder(1.vedtaksperiode))
        assertTilstander(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET)
        assertFalse(utbetalingenSomTrekkerPenger.inspektør.utbetalingId in utbetalingIder(3.vedtaksperiode))
        assertTilstander(4.vedtaksperiode, START, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, AVVENTER_SIMULERING)
        assertTrue(utbetalingenSomTrekkerPenger.inspektør.utbetalingId in utbetalingIder(4.vedtaksperiode))
    }

    private fun utbetalingIder(vedtaksperiode: IdInnhenter) = inspektør.vedtaksperioder(vedtaksperiode).inspektør.behandlinger.flatMap { it.endringer.mapNotNull { endring -> endring.utbetaling?.inspektør?.utbetalingId } }

    @Test
    fun `Arbeid gjenopptatt i minst 16 dager fører til at vi bygger videre på feil utbetaling`() {
        nyttVedtak(januar)
        håndterSøknad(Sykdom(1.februar, 18.februar, 100.prosent), Arbeid(3.februar, 18.februar))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()
        håndterSøknad(Sykdom(19.februar, 19.mars, 100.prosent))
        håndterInntektsmelding(listOf(19.februar til 6.mars))
        håndterVilkårsgrunnlag(3.vedtaksperiode)
        håndterYtelser(3.vedtaksperiode)

        assertEquals(1.januar til 16.januar, inspektør.arbeidsgiverperiode(1.vedtaksperiode))
        assertEquals(1.januar til 16.januar, inspektør.arbeidsgiverperiode(2.vedtaksperiode))
        assertEquals(19.februar til 6.mars, inspektør.arbeidsgiverperiode(3.vedtaksperiode))

        assertEquals(3, inspektør.utbetalinger.size)
        val januar = inspektør.utbetalinger.first()
        val mars = inspektør.utbetalinger.last()
        assertNotEquals(januar.inspektør.korrelasjonsId, mars.inspektør.korrelasjonsId)
    }

    @Test
    fun `drawio -- misc -- oppvarming`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

        håndterInntektsmelding(listOf(1.januar til 16.januar),)

        utbetalPeriode(1.vedtaksperiode)

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )

        utbetalPeriodeEtterVilkårsprøving(2.vedtaksperiode)

        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `Én arbeidsgiver - førstegangsbehandling`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `Forlengelse av en avsluttet periode går til AvventerHistorikk`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
    }

    @Test
    fun `Førstegangsbehandling går ikke videre dersom vi har en tidligere uferdig periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        håndterInntektsmelding(listOf(1.mars til 16.mars),)

        assertTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE
        )
    }

    @Test
    fun `Førstegangsbehandling går videre etter at en tidligere uferdig periode er ferdig`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        håndterInntektsmelding(listOf(1.mars til 16.mars),)

        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstand(2.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)
    }

    @Test
    fun `Kort periode går til AvsluttetUtenUtbetaling, pusher neste periode til AvventerHistorikk`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 16.januar))
        håndterSykmelding(Sykmeldingsperiode(17.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent))

        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)

        nullstillTilstandsendringer()
        val im = håndterInntektsmelding(listOf(1.januar til 16.januar),)

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, AVVENTER_BLOKKERENDE_PERIODE, AVSLUTTET_UTEN_UTBETALING)
        assertTrue(im in observatør.inntektsmeldingFørSøknad.map { it.inntektsmeldingId })
        assertFalse(im in observatør.inntektsmeldingHåndtert.map(Pair<InntektsmeldingId, *>::first))

        nullstillTilstandsendringer()

        håndterSøknad(Sykdom(17.januar, 31.januar, 100.prosent))
        assertTrue(im in observatør.inntektsmeldingHåndtert.map(Pair<InntektsmeldingId, *>::first))

        assertTilstander(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING)
    }

    @Test
    fun `Inntektsmelding kommer før søknad - vi kommer oss videre til AvventerHistorikk pga replay`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING
        )
    }

    @Test
    fun `Kort periode skal ikke blokkeres av mangelende søknad`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 20.januar))
        håndterSykmelding(Sykmeldingsperiode(21.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar),)

        assertTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)
    }

    @Test
    fun `Går til AvventerInntektsmelding ved gap`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 22.januar))
        håndterSykmelding(Sykmeldingsperiode(25.januar, 17.februar))

        håndterSøknad(Sykdom(1.januar, 22.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar),)

        håndterSøknad(Sykdom(25.januar, 17.februar, 100.prosent))

        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `drawio -- Out of order`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterInntektsmelding(listOf(1.januar til 16.januar),)

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        assertTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertTilstand(2.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)

        utbetalPeriode(2.vedtaksperiode)
        assertTilstand(2.vedtaksperiode, AVSLUTTET)

        utbetalPeriodeEtterVilkårsprøving(1.vedtaksperiode)
        assertTilstand(1.vedtaksperiode, AVSLUTTET)
    }

    @Test
    fun `går tilbake til AvventerTidligereEllerOverlappende dersom vi får en tilbakedatert søknad før periode i AvventerGodkjenning`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        håndterInntektsmelding(listOf(1.mars til 16.mars),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `går tilbake til AvventerTidligereEllerOverlappende dersom vi får en tilbakedatert søknad før periode i AvventerHistorikk`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        håndterInntektsmelding(listOf(1.mars til 16.mars),)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `går tilbake til AvventerTidligereEllerOverlappende dersom vi får en tilbakedatert søknad før periode i AvventerVilkårsprøving`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        håndterInntektsmelding(listOf(1.mars til 16.mars),)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `går tilbake til AvventerTidligereEllerOverlappende dersom vi får en tilbakedatert søknad før periode i AvventerSimulering`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        håndterInntektsmelding(listOf(1.mars til 16.mars),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `blir i AvventerInntektsmelding dersom vi får en out-of-order søknad forran`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        assertTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `blir i AvventerTidligereEllerOverlappende dersom vi får en out-of-order søknad forran`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterInntektsmelding(listOf(1.mars til 16.mars),)

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `hopper ikke videre fra AvventerInntektsmelding dersom vi får en out-of-order søknad foran og IM kommer på den seneste vedtaksperioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        håndterInntektsmelding(listOf(1.mars til 16.mars),)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
    }

    @Test
    fun `To perioder med gap, den siste venter på at den første skal bli ferdig - dersom den første blir forkastet skal den siste perioden forkastes`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSykmelding(Sykmeldingsperiode(1.mai, 31.mai))

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.mai, 31.mai, 100.prosent))

        håndterInntektsmelding(listOf(1.mai til 16.mai),)
        håndterPåminnelse(
            1.vedtaksperiode,
            påminnetTilstand = AVVENTER_INNTEKTSMELDING,
            tilstandsendringstidspunkt = 5.februar.atStartOfDay()
        )

        assertTilstand(1.vedtaksperiode, TIL_INFOTRYGD)
        assertTilstand(2.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `Kort periode med en tidligere kort periode som har lagret inntekt for første fraværsdag`() {
        /* skal ikke gå videre til AVVENTER_HISTORIKK siden perioden ikke går forbi AGP */
        håndterSykmelding(Sykmeldingsperiode(1.januar, 2.januar))
        håndterSykmelding(Sykmeldingsperiode(10.januar, 11.januar))

        håndterSøknad(Sykdom(1.januar, 2.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 2.januar, 10.januar til 23.januar), førsteFraværsdag = 10.januar,)
        håndterSøknad(Sykdom(10.januar, 11.januar, 100.prosent))

        assertTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
    }

    @Test
    fun `Periode skal ha utbetaling grunnet inntektsmelding vi mottok før søknad`() {
        håndterSykmelding(Sykmeldingsperiode(11.januar, 26.januar))
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterSøknad(Sykdom(11.januar, 26.januar, 100.prosent))
        assertTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)
    }

    @Test
    fun `Skal ikke forkaste periode som mottar to identiske søknader`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING)
    }

    @Test
    fun `Skal ikke forkaste periode som mottar to identiske søknader for forlengelse i AvventerTidligereEllerOverlappendePerioder`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
        assertTilstand(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
    }

    @Test
    fun `ikke opprett ny vedtaksperiode dersom vi tidligere har forkastet en i samme periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        forkastAlle(hendelselogg)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
        assertTrue(inspektør.periodeErForkastet(2.vedtaksperiode))
    }

    @Test
    fun `Dersom alle perioder forkastes skal ingen av dem pokes videre fra gjenopptaBehandling`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        forkastAlle(hendelselogg)

        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, AVVENTER_BLOKKERENDE_PERIODE, TIL_INFOTRYGD)
    }

    @Test
    fun `Infotrygdhistorikk fører til at en senere periode ikke trenger ny AGP - må vente på infotrygdhistorikk før vi bestemmer om vi skal til AUU`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        utbetalPeriode(1.vedtaksperiode)

        håndterUtbetalingshistorikkEtterInfotrygdendring(
            utbetalinger = arrayOf(ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.februar, 10.februar, 100.prosent, INNTEKT)),
            inntektshistorikk = listOf(Inntektsopplysning(ORGNUMMER, 1.februar, INNTEKT, true))
        )
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 20.februar,)
        håndterSykmelding(Sykmeldingsperiode(20.februar, 28.februar))
        håndterSøknad(Sykdom(20.februar, 28.februar, 100.prosent))
        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, TIL_INFOTRYGD)
    }

    @Test
    fun `overlappende sykmeldinger - første bit skal kunne gå videre selv om vi ikke har mottatt søknad for halen`() {
        tilGodkjenning(1.januar til 31.januar, ORGNUMMER)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 20.februar))
        håndterSøknad(Sykdom(1.februar, 20.februar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK)
    }

    @Test
    fun `korrigert sykmelding skal ikke blokkere for behandlingen av den første`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar)) // dup
        håndterInntektsmelding(listOf(1.januar til 16.januar),)
        assertEquals(emptyList<Periode>(), inspektør.sykmeldingsperioder())
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)
    }

    @Test
    fun `tilbakedatert sykmelding som ikke overlapper skal ikke hindre at tidligere periode kan gå videre`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(31.januar, 31.januar))
        håndterInntektsmelding(listOf(1.februar til 16.februar),)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING)
    }

    private fun utbetalPeriodeEtterVilkårsprøving(vedtaksperiode: IdInnhenter) {
        håndterYtelser(vedtaksperiode)
        håndterSimulering(vedtaksperiode)
        håndterUtbetalingsgodkjenning(vedtaksperiode)
        håndterUtbetalt()
    }

    private fun utbetalPeriode(vedtaksperiode: IdInnhenter) {
        håndterVilkårsgrunnlag(vedtaksperiode)
        utbetalPeriodeEtterVilkårsprøving(vedtaksperiode)
    }
}