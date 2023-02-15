package no.nav.helse.spleis.e2e.søknad

import no.nav.helse.assertForventetFeil
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Permisjon
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.september
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertForkastetPeriodeTilstander
import no.nav.helse.spleis.e2e.assertIngenVarsler
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalingshistorikk
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.tilGodkjent
import no.nav.helse.spleis.e2e.tilSimulering
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.Feriedag
import no.nav.helse.sykdomstidslinje.Dag.Permisjonsdag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.utbetalingslinjer.Utbetaling
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

internal class KorrigertSøknadTest : AbstractEndToEndTest() {

    @Test
    fun `korrigerer periode med bare ferie`() {
        nyttVedtak(3.januar, 26.januar)
        nyttVedtak(3.mars, 26.mars)
        nullstillTilstandsendringer()

        håndterSøknad(Sykdom(3.mars, 26.mars, 100.prosent), Ferie(3.mars, 26.mars))
        håndterYtelser(2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode)

        assertForventetFeil(
            forklaring = "Har laget en overlappende utbetaling fordi det ikke er noen ArbeidsgiverperiodeDag før januar." +
                    "OppdragBuilder stopper først ved ArbeidsgiverperiodeDag eller UkjentDag",
            nå = {
                assertThrows<IllegalStateException>("Har laget en overlappende utbetaling") {
                    håndterYtelser(2.vedtaksperiode)
                }
            },
            ønsket = {
                assertDoesNotThrow { håndterYtelser(2.vedtaksperiode) }
                val andreUtbetaling = inspektør.utbetaling(1).inspektør
                val tredjeUtbetaling = inspektør.utbetaling(2).inspektør
                assertEquals(andreUtbetaling.korrelasjonsId, tredjeUtbetaling.korrelasjonsId)
                assertEquals(1, tredjeUtbetaling.arbeidsgiverOppdrag.size)
                tredjeUtbetaling.arbeidsgiverOppdrag[0].inspektør.also { linje ->
                    assertEquals(18.mars, linje.fom)
                    assertEquals(26.mars, linje.tom)
                    assertEquals(18.mars, linje.datoStatusFom)
                    assertEquals("OPPH", linje.statuskode)
                }
            }
        )
    }

    @Test
    fun `korrigert søknad i til utbetaling - utbetaler først, så revurdere`() {
        tilGodkjent(3.januar, 26.januar, 100.prosent, 3.januar)
        nullstillTilstandsendringer()
        håndterSøknad(Sykdom(3.januar, 26.januar, 80.prosent))
        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
    }

    @Test
    fun `korrigert søknad i avventer simulering - forkaster utbetalingen`() {
        tilSimulering(3.januar, 26.januar, 100.prosent, 3.januar)
        nullstillTilstandsendringer()
        håndterSøknad(Sykdom(3.januar, 26.januar, 80.prosent))
        assertEquals(Utbetaling.Forkastet, inspektør.utbetalinger(1.vedtaksperiode).single().inspektør.tilstand)
        assertTilstander(1.vedtaksperiode, AVVENTER_SIMULERING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
    }

    @Test
    fun `Arbeidsdag i søknad nr 2 kaster ikke ut perioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Arbeid(31.januar, 31.januar), korrigerer = søknadId, opprinneligSendt = 1.februar)
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
    }

    @Test
    fun `Overlappende søknad som er lengre tilbake støttes ikke`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 31.januar))
        håndterSøknad(Sykdom(3.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            TIL_INFOTRYGD
        )
    }
    @Test
    fun `Overlappende søknad som er lengre frem støttes ikke`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 31.januar))
        håndterSøknad(Sykdom(3.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 1.februar, 100.prosent))
        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            TIL_INFOTRYGD
        )
    }

    @Test
    fun `Korrigerer fridager til sykedag`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Permisjon(30.januar, 30.januar), Ferie(31.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), korrigerer = søknadId, opprinneligSendt = 1.februar)
        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[30.januar] is Sykedag)
            assertTrue(it[31.januar] is Sykedag)
        }
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
    }

    @Test
    fun `Korrigerer sykedag til feriedag`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Permisjon(30.januar, 30.januar), Ferie(31.januar, 31.januar), korrigerer = søknadId, opprinneligSendt = 1.februar)
        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[30.januar] is Permisjonsdag)
            assertTrue(it[31.januar] is Feriedag)
        }
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
    }

    @Test
    fun `Korrigerer grad`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent), korrigerer = søknadId, opprinneligSendt = 1.februar)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertEquals(50, inspektør.sykdomstidslinje.inspektør.grader[17.januar])
        assertEquals(50, inspektør.utbetalingstidslinjer(1.vedtaksperiode).inspektør.grad(17.januar))
    }

    @Test
    fun `Korrigerer feriedag til sykedag i forlengelse`() {
        nyttVedtak(1.januar, 31.januar)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        val søknadId = håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(28.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), korrigerer = søknadId, opprinneligSendt = 1.mars)

        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[28.februar] is Sykedag)
        }
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK
        )
        assertIngenVarsler(1.vedtaksperiode.filter())
        assertIngenVarsler(2.vedtaksperiode.filter())
    }

    @Test
    fun `nytt skjæringstidspunkt på forlengelse etter friskmelding på førstegangsbehandling`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

        nullstillTilstandsendringer()
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Arbeid(25.januar, 31.januar))

        assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        nullstillTilstandsendringer()
        håndterUtbetalt()
        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_INNTEKTSMELDING_UFERDIG_GAP`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(2.februar, 28.februar))
        val søknadId = håndterSøknad(Sykdom(2.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(2.februar, 28.februar, 100.prosent), Ferie(28.februar, 28.februar), korrigerer = søknadId, opprinneligSendt = 1.mars)
        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[28.februar] is Feriedag)
        }
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
        assertIngenVarsler(1.vedtaksperiode.filter())
        assertIngenVarsler(2.vedtaksperiode.filter())
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        val søknadId = håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(28.februar, 28.februar), korrigerer = søknadId, opprinneligSendt = 1.mars)

        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[28.februar] is Feriedag)
        }
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
        assertIngenVarsler(1.vedtaksperiode.filter())
        assertIngenVarsler(2.vedtaksperiode.filter())
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVENTER_TIDLIGERE_ELLER_OVERLAPPENDE`() {
        håndterSykmelding(Sykmeldingsperiode(1.desember(2017), 11.desember(2017)))
        håndterSøknad(Sykdom(1.desember(2017), 11.desember(2017), 100.prosent))
        håndterUtbetalingshistorikk(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 10.januar))
        håndterSøknad(Sykdom(1.januar, 10.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(11.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(11.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar)
        håndterVilkårsgrunnlag(3.vedtaksperiode)
        håndterSøknad(Sykdom(11.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar), korrigerer = søknadId, opprinneligSendt = 1.februar)


        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[31.januar] is Feriedag)
        }
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, AVSLUTTET_UTEN_UTBETALING, AVSLUTTET_UTEN_UTBETALING)
        assertTilstander(3.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_VILKÅRSPRØVING, AVVENTER_HISTORIKK, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
        assertIngenVarsler(1.vedtaksperiode.filter())
        assertIngenVarsler(2.vedtaksperiode.filter())
        assertIngenVarsler(3.vedtaksperiode.filter())
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_INNTEKTSMELDING_FERDIG_GAP`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterUtbetalingshistorikk(1.vedtaksperiode)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar), korrigerer = søknadId, opprinneligSendt = 1.februar)

        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[31.januar] is Feriedag)
        }
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
        assertIngenVarsler(1.vedtaksperiode.filter())
    }

    @Test
    fun `avslutter periode med friskmelding -- avventer vilkårsprøving`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Arbeid(17.januar, 31.januar), korrigerer = søknadId, opprinneligSendt = 1.februar)

        inspektør.sykdomstidslinje.inspektør.also { inspektør ->
            (17.januar til 31.januar).forEach {
                assertTrue(inspektør[it] is Dag.Arbeidsdag || inspektør[it] is Dag.FriskHelgedag)
            }
        }
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
           AVVENTER_VILKÅRSPRØVING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVSLUTTET_UTEN_UTBETALING
        )
        assertIngenVarsler(1.vedtaksperiode.filter())
    }

    @Test
    fun `avslutter periode med 100 % ferie og ingen utbetaling -- avventer vilkårsprøving`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(17.januar, 31.januar), korrigerer = søknadId, opprinneligSendt = 1.februar)

        inspektør.sykdomstidslinje.inspektør.also { inspektør ->
            (17.januar til 31.januar).forEach {
                assertTrue(inspektør[it] is Feriedag)
            }
        }
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
           AVVENTER_VILKÅRSPRØVING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVSLUTTET_UTEN_UTBETALING
        )
        assertIngenVarsler(1.vedtaksperiode.filter())
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_VILKÅRSPRØVING`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar), korrigerer = søknadId, opprinneligSendt = 1.februar)

        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[31.januar] is Feriedag)
        }
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
           AVVENTER_VILKÅRSPRØVING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING
        )
        assertIngenVarsler(1.vedtaksperiode.filter())
    }

    @Test
    fun `Blir værende i nåværende tilstand dersom søknad kommer inn i AVVENTER_HISTORIKK`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar), korrigerer = søknadId, opprinneligSendt = 1.februar)

        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[31.januar] is Feriedag)
        }
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK
        )
        assertIngenVarsler(1.vedtaksperiode.filter())
    }

    @Test
    fun `Går tilbake til AVVENTER_HISTORIKK når søknaden kommer inn i AVVENTER_SIMULERING`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), korrigerer = søknadId, opprinneligSendt = 1.februar)

        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[31.januar] is Sykedag)
        }
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK
        )
        assertIngenVarsler(1.vedtaksperiode.filter())
    }

    @Test
    fun `Går tilbake til AVVENTER_HISTORIKK når søknaden kommer inn i AVVENTER_GODKJENNING`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), korrigerer = søknadId, opprinneligSendt = 1.februar)

        inspektør.sykdomstidslinje.inspektør.also {
            assertTrue(it[31.januar] is Sykedag)
        }
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK
        )
        assertIngenVarsler(1.vedtaksperiode.filter())
    }

    @Test
    fun `Ikke foreld dager ved sen korrigerende søknad om original søknad var innenfor avskjæringsdag`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 31.januar)
        assertEquals("SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSS", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar), sendtTilNAVEllerArbeidsgiver = 30.september, korrigerer = søknadId, opprinneligSendt = 31.januar)
        assertEquals("SSSSSHH SSSSSHH SSSSSHH SSSSSHH SSF", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
    }

    @Test
    fun `Foreld dager ved sen søknad, selv om vi mottar korrigerende søknad`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 1.mai)
        assertEquals("KKKKKHH KKKKKHH KKKKKHH KKKKKHH KKK", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(31.januar, 31.januar), sendtTilNAVEllerArbeidsgiver = 2.mai, korrigerer = søknadId, opprinneligSendt = 1.mai)
        assertEquals("KKKKKHH KKKKKHH KKKKKHH KKKKKHH KKF", inspektør.sykdomshistorikk.sykdomstidslinje().toShortString().trim())
    }
}
