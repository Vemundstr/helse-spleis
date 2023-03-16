package no.nav.helse.spleis.e2e.revurdering

import java.time.LocalDate
import no.nav.helse.april
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.Dagtype
import no.nav.helse.hendelser.Dagtype.Feriedag
import no.nav.helse.hendelser.ManuellOverskrivingDag
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.juli
import no.nav.helse.juni
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GJENNOMFØRT_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.REVURDERING_FEILET
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_AY_5
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_VV_4
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertIngenFunksjonelleFeil
import no.nav.helse.spleis.e2e.assertIngenVarsler
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.forlengTilGodkjenning
import no.nav.helse.spleis.e2e.forlengTilSimulering
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.håndterOverstyrTidslinje
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.tilGodkjenning
import no.nav.helse.spleis.e2e.tilGodkjent
import no.nav.helse.spleis.e2e.tilSimulering
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.SykHelgedag
import no.nav.helse.sykdomstidslinje.Dag.Sykedag
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.utbetalingslinjer.Utbetalingstatus
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.NavDag
import no.nav.helse.utbetalingstidslinje.Utbetalingsdag.NavHelgDag
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RevurderingV2E2ETest : AbstractEndToEndTest() {

    @Test
    fun `forlengelse etter revurdering uten endring`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.april, 30.april)
        forlengVedtak(1.mai, 31.mai)

        håndterOverstyrTidslinje(listOf(
            ManuellOverskrivingDag(31.januar, Feriedag)
        ))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterYtelser(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)

        forlengVedtak(1.juni, 30.juni)

        val utbetaling4 = inspektør.utbetaling(4).inspektør
        val utbetaling5 = inspektør.utbetaling(5).inspektør
        assertEquals(utbetaling5.korrelasjonsId, utbetaling4.korrelasjonsId)
        assertEquals(Utbetalingstatus.GODKJENT_UTEN_UTBETALING, utbetaling4.tilstand)
        assertEquals(Utbetalingstatus.UTBETALT, utbetaling5.tilstand)
    }

    @Test
    fun `revurdere første periode`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        forlengVedtak(1.mars, 31.mars)
        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(19.januar, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(19.januar, Feriedag)))
        håndterYtelser(3.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(19.januar, 0.0)
        assertDiff(-1431)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVSLUTTET)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
    }

    @Test
    fun `revurdere periode med forlengelse i avventer godkjenning`() {
        nyttVedtak(1.januar, 31.januar)
        forlengTilGodkjenning(1.februar, 28.februar)
        nullstillTilstandsendringer()

        assertDag<Sykedag, NavDag>(17.januar, 1431.0)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))
        håndterYtelser(1.vedtaksperiode)

        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(17.januar, 0.0)
        assertDiff(-1431)
        val utbetalinger = inspektør.utbetalinger(2.vedtaksperiode)
        assertEquals(1, utbetalinger.size)
        assertEquals(Utbetalingstatus.FORKASTET, utbetalinger.single().inspektør.tilstand)

        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
    }

    @Test
    fun `revurdere andre periode`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        forlengVedtak(1.mars, 31.mars)
        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(5.februar, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(5.februar, Feriedag)))
        håndterYtelser(3.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(5.februar, 0.0)
        assertDiff(-1431)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVSLUTTET)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
    }

    @Test
    fun `revurdere tredje periode`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        forlengVedtak(1.mars, 31.mars)
        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(5.mars, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(5.mars, Feriedag)))
        håndterYtelser(3.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(5.mars, 0.0)
        assertDiff(-1431)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
    }

    @Test
    fun `revurdere første to perioder`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        forlengVedtak(1.mars, 31.mars)
        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(31.januar, 1431.0)
        assertDag<Sykedag, NavDag>(1.februar, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Feriedag)))
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(1.februar, Feriedag)))
        håndterYtelser(3.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(31.januar, 0.0)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(1.februar, 0.0)
        assertDiff(-2862)
        håndterSimulering(3.vedtaksperiode)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVSLUTTET)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
    }

    @Test
    fun `en ny forlengelse på en aktiv revurdering`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)

        nyPeriode(1.mars til 31.mars)
        assertIngenVarsler(1.vedtaksperiode.filter())
        assertIngenVarsler(2.vedtaksperiode.filter())
        assertIngenVarsler(3.vedtaksperiode.filter())
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING)
        assertTilstander(3.vedtaksperiode, START, AVVENTER_BLOKKERENDE_PERIODE)
    }

    @Test
    fun `revurdere dager i arbeidsgiverperioden på tidligere utbetaling`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)
        nullstillTilstandsendringer()
        assertDag<Sykedag, ArbeidsgiverperiodeDag>(5.januar,0.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(5.januar, Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        assertDag<Dag.Feriedag, ArbeidsgiverperiodeDag>(5.januar, 0.0)
        assertDiff(0)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_GODKJENNING_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
    }

    @Test
    fun `revurdere periode med nyere førstegangsbehandling innenfor samme agp`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(10.februar, 28.februar, arbeidsgiverperiode = listOf(1.januar til 16.januar))
        nullstillTilstandsendringer()
        assertDag<Sykedag, ArbeidsgiverperiodeDag>(5.januar,0.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(5.januar, Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        assertDag<Dag.Feriedag, ArbeidsgiverperiodeDag>(5.januar, 0.0)
        assertDiff(0)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_GODKJENNING_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)

        assertEquals(3, inspektør.utbetalinger.size)
        inspektør.utbetaling(2).inspektør.also { revurdering ->
            val januarutbetaling = inspektør.utbetaling(0).inspektør
            val februarutbetaling = inspektør.utbetaling(1).inspektør
            assertEquals(revurdering.korrelasjonsId, januarutbetaling.korrelasjonsId)
            assertEquals(revurdering.korrelasjonsId, februarutbetaling.korrelasjonsId)
            assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNN", revurdering.utbetalingstidslinje.toString().trim())
            assertEquals(1.januar til 31.januar, januarutbetaling.periode)
            assertEquals(1.januar til 28.februar, februarutbetaling.periode)
            assertEquals(1.januar til 31.januar, revurdering.periode)
            assertEquals(0, revurdering.personOppdrag.size)
            assertEquals(2, revurdering.arbeidsgiverOppdrag.size)
            assertEquals(Endringskode.UEND, revurdering.arbeidsgiverOppdrag.inspektør.endringskode)
            assertEquals(0, revurdering.arbeidsgiverOppdrag.inspektør.nettoBeløp)
            revurdering.arbeidsgiverOppdrag.inspektør.also { arbeidsgiveroppdrag ->
                assertEquals(17.januar, arbeidsgiveroppdrag.fom(0))
                assertEquals(31.januar, arbeidsgiveroppdrag.tom(0))
                assertEquals(1431, arbeidsgiveroppdrag.beløp(0))
                assertEquals(Endringskode.UEND, arbeidsgiveroppdrag.endringskode(0))

                assertEquals(10.februar, arbeidsgiveroppdrag.fom(1))
                assertEquals(28.februar, arbeidsgiveroppdrag.tom(1))
                assertEquals(1431, arbeidsgiveroppdrag.beløp(1))
                assertEquals(Endringskode.UEND, arbeidsgiveroppdrag.endringskode(1))
            }
        }
    }

    @Test
    fun `starte revurdering av eldre skjæringstidspunkt, så revurdere nyere`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        nyttVedtak(1.april, 30.april)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Feriedag)))
        håndterYtelser(2.vedtaksperiode)

        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(31.januar, 0.0)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)

        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(10.februar, Feriedag)))
        håndterYtelser(2.vedtaksperiode)

        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(10.februar, 0.0)
        assertTilstander(1.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVVENTER_REVURDERING)

        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(20.april, Feriedag)))

        assertDag<Dag.Feriedag, Utbetalingsdag.UkjentDag>(20.april, null, null)
        assertTilstander(1.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVVENTER_REVURDERING)
    }

    @Test
    fun `revurdere nyere skjæringstidspunkt så revurdere eldste`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(10.februar, 28.februar, arbeidsgiverperiode = listOf(1.januar til 16.januar))
        forlengVedtak(1.mars, 31.mars)
        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(15.februar, 1431.0)
        assertDag<Sykedag, NavDag>(19.januar, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(15.februar, Feriedag)))
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(19.januar, Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(19.januar, 0.0)
        assertDiff(-1431)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterYtelser(3.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(15.februar, 0.0)
        assertDiff(-1431)

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING)
    }

    @Test
    fun `revurdere nyere arbeidsgiverperiode så revurdere eldste`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)
        forlengVedtak(1.april, 30.april)
        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(19.mars, 1431.0)
        assertDag<Sykedag, NavDag>(19.januar, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(19.mars, Feriedag)))
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(19.januar, Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(19.januar, 0.0)
        assertDiff(-1431)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterYtelser(3.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(19.mars, 0.0)
        assertDiff(-1431)

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING)
    }

    @Test
    fun `revurdere eldre skjæringstidspunkt mens nyere revurdert skjæringstidspunkt utbetales`() {
        nyttVedtak(1.januar, 20.januar)
        forlengVedtak(21.januar, 31.januar)
        nyttVedtak(10.februar, 28.februar, arbeidsgiverperiode = listOf(1.januar til 16.januar))
        forlengVedtak(1.mars, 31.mars)
        nullstillTilstandsendringer()

        assertDag<Sykedag, NavDag>(15.februar, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(15.februar, Feriedag)))
        håndterYtelser(4.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(15.februar, 0.0)
        assertDiff(-1431)
        håndterSimulering(4.vedtaksperiode)
        håndterUtbetalingsgodkjenning(4.vedtaksperiode)

        assertEquals(5, inspektør.utbetalinger.size)
        inspektør.utbetaling(4).inspektør.also { revurdering ->
            val januar1utbetaling = inspektør.utbetaling(0).inspektør
            val januar2utbetaling = inspektør.utbetaling(1).inspektør
            val februarutbetaling = inspektør.utbetaling(2).inspektør
            val marsutbetaling = inspektør.utbetaling(3).inspektør
            assertEquals(revurdering.korrelasjonsId, januar1utbetaling.korrelasjonsId)
            assertEquals(revurdering.korrelasjonsId, januar2utbetaling.korrelasjonsId)
            assertEquals(revurdering.korrelasjonsId, februarutbetaling.korrelasjonsId)
            assertEquals(revurdering.korrelasjonsId, marsutbetaling.korrelasjonsId)
            assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNNAAFF AAAAAHH NNNFNHH NNNNNHH NNNNNHH NNNNNHH NNNNNHH NNNNNHH NNNNNH", revurdering.utbetalingstidslinje.toString().trim())
            assertEquals(1.januar til 20.januar, januar1utbetaling.periode)
            assertEquals(1.januar til 31.januar, januar2utbetaling.periode)
            assertEquals(1.januar til 28.februar, februarutbetaling.periode)
            assertEquals(1.januar til 31.mars, marsutbetaling.periode)
            assertEquals(1.januar til 31.mars, revurdering.periode)
            assertEquals(0, revurdering.personOppdrag.size)
            assertEquals(3, revurdering.arbeidsgiverOppdrag.size)
            assertEquals(Endringskode.ENDR, revurdering.arbeidsgiverOppdrag.inspektør.endringskode)
            assertEquals(-1431, revurdering.arbeidsgiverOppdrag.inspektør.nettoBeløp)
            revurdering.arbeidsgiverOppdrag.inspektør.also { arbeidsgiveroppdrag ->
                assertEquals(17.januar, arbeidsgiveroppdrag.fom(0))
                assertEquals(31.januar, arbeidsgiveroppdrag.tom(0))
                assertEquals(1431, arbeidsgiveroppdrag.beløp(0))
                assertEquals(Endringskode.UEND, arbeidsgiveroppdrag.endringskode(0))

                assertEquals(10.februar, arbeidsgiveroppdrag.fom(1))
                assertEquals(14.februar, arbeidsgiveroppdrag.tom(1))
                assertEquals(1431, arbeidsgiveroppdrag.beløp(1))
                assertEquals(Endringskode.ENDR, arbeidsgiveroppdrag.endringskode(1))

                assertEquals(16.februar, arbeidsgiveroppdrag.fom(2))
                assertEquals(30.mars, arbeidsgiveroppdrag.tom(2))
                assertEquals(1431, arbeidsgiveroppdrag.beløp(2))
                assertEquals(Endringskode.NY, arbeidsgiveroppdrag.endringskode(2))
            }
        }

        assertDag<SykHelgedag, NavHelgDag>(21.januar, 0.0)
        assertDag<Sykedag, NavDag>(19.januar, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(21.januar, Feriedag)))
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(19.januar, Feriedag)))

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_REVURDERING)
        assertTilstander(4.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVVENTER_REVURDERING)

        nullstillTilstandsendringer()
        håndterUtbetalt()
        assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVVENTER_REVURDERING)
        assertTilstander(4.vedtaksperiode, AVVENTER_REVURDERING)

        assertNotNull(observatør.vedtakFattetEvent[4.vedtaksperiode.id(ORGNUMMER)])

        nullstillTilstandsendringer()
        håndterYtelser(2.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(21.januar, 0.0)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(19.januar, 0.0)
        assertDiff(-1431)

        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(1.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(3.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(4.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
    }

    @Test
    fun `revurdering av eldre skjæringstidspunkt mens nyere skjæringstidspunkt utbetales`() {
        nyttVedtak(1.januar, 31.januar)
        tilGodkjent(1.mars, 31.mars, 100.prosent, førsteFraværsdag = 1.mars)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)

        håndterUtbetalt()
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)

        assertNotNull(observatør.vedtakFattetEvent[2.vedtaksperiode.id(ORGNUMMER)])
    }

    @Test
    fun `Periode med forlengelse - forlengelse blir revurdert, deretter revurdering på første`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(5.februar, Feriedag)))
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(5.januar, Feriedag)))
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
    }

    @Test
    fun `Periode med to forlengelser - forlengelse nummer en blir revurdert, deretter revurdering på første`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        forlengVedtak(1.mars, 31.mars)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(5.februar, Feriedag)))
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(5.januar, Feriedag)))
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
    }

    @Test
    fun `periode med forlengelse etterfulgt av kort periode - kort periode avsluttes ikke før revurdering er ferdig`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))

        håndterSykmelding(Sykmeldingsperiode(1.april, 16.april))
        håndterSøknad(Sykdom(1.april, 16.april, 100.prosent))

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(3.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)
    }

    @Test
    fun `revurdere eldste skjæringstidspunkt så revurdere nyeste`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(10.februar, 28.februar, arbeidsgiverperiode = listOf(1.januar til 16.januar))
        forlengVedtak(1. mars, 31.mars)
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(19.januar, Feriedag)))
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(15.februar, Feriedag)))
        assertTrue(inspektør.sykdomstidslinje[15.februar] is Dag.Feriedag)
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
    }

    @Test
    fun `nyere revurdering til simulering, så revurdering på eldre`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)
        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(20.mars, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(20.mars, Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(20.mars, 0.0)
        assertDiff(-1431)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(20.januar, Feriedag)))
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_REVURDERING)
    }

    @Test
    fun `nyere revurdering til godkjenning, så revurdering på eldre`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)
        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(20.mars, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(20.mars, Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(20.mars, 0.0)
        assertDiff(-1431)
        håndterSimulering(2.vedtaksperiode)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(20.januar, Feriedag)))
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVVENTER_REVURDERING)
    }

    @Test
    fun `Periode med forlengelse - forlengelse blir revurdert og er til godkjenning, deretter revurdering på første`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(5.februar, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(5.februar, Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(5.februar, 0.0)
        assertDiff(-1431)
        håndterSimulering(2.vedtaksperiode)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(5.januar, Feriedag)))
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
    }

    @Test
    fun `revudering påvirkes ikke av gjenoppta behandling ved avsluttet uten utbetaling`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        assertDag<Sykedag, NavDag>(17.januar, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(17.januar, 0.0)
        assertDiff(-1431)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()
        nullstillTilstandsendringer()

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))

        håndterSykmelding(Sykmeldingsperiode(1.juni, 10.juni))
        håndterSøknad(Sykdom(1.juni, 10.juni, 100.prosent))
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(3.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVSLUTTET_UTEN_UTBETALING)
    }

    @Test
    fun `periode til utbetaling blir overstyrt`() {
        tilGodkjent(1.januar, 31.januar, 100.prosent, 1.januar)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))
        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
        håndterUtbetalt()
        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertNotNull(observatør.vedtakFattetEvent[1.vedtaksperiode.id(ORGNUMMER)])
    }

    @Test
    fun `revurdert periode til utbetaling blir revurdert`() {
        nyttVedtak(1.januar, 31.januar)
        assertDag<Sykedag, NavDag>(17.januar, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(17.januar, 0.0)
        assertDiff(-1431)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))

        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING)
        håndterUtbetalt()
        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertNotNull(observatør.vedtakFattetEvent[1.vedtaksperiode.id(ORGNUMMER)])
    }

    @Test
    fun `revurdering på tidligere skjæringstidspunkt mens nyere førstegangsbehandling står i avventer simulering`() {
        nyttVedtak(1.januar, 31.januar)
        tilSimulering(1.mars, 31.mars, 100.prosent, 1.mars)
        assertDag<Sykedag, NavDag>(17.januar, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(17.januar, 0.0)
        assertDiff(-1431)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        nullstillTilstandsendringer()
        håndterUtbetalt()

        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
    }

    @Test
    fun `revurdering på tidligere skjæringstidspunkt mens nyere førstegangsbehandling står i avventer godkjenning`() {
        nyttVedtak(1.januar, 31.januar)
        tilGodkjenning(1.mars, 31.mars, 100.prosent, 1.mars)
        assertDag<Sykedag, NavDag>(17.januar, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(17.januar, 0.0)
        assertDiff(-1431)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        nullstillTilstandsendringer()
        håndterUtbetalt()
        assertTilstander(1.vedtaksperiode, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
    }

    @Test
    fun `revurdering på tidligere skjæringstidspunkt mens nyere forlengelse står i avventer simulering`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)
        forlengTilSimulering(1.april, 30.april, 100.prosent)
        assertDag<Sykedag, NavDag>(17.januar, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(17.januar, 0.0)
        assertDiff(-1431)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        håndterYtelser(2.vedtaksperiode)
        nullstillTilstandsendringer()
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, AVSLUTTET)
        assertTilstander(3.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
    }

    @Test
    fun `revurdering på tidligere skjæringstidspunkt mens nyere forlengelse står i avventer godkjenning`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)
        forlengTilGodkjenning(1.april, 30.april, 100.prosent)
        assertDag<Sykedag, NavDag>(17.januar, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(17.januar, 0.0)
        assertDiff(-1431)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        håndterYtelser(2.vedtaksperiode)
        nullstillTilstandsendringer()
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, AVSLUTTET)
        assertTilstander(3.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
    }

    @Test
    fun `revurdering på tidligere skjæringstidspunkt mens nyere revurdering står i avventer simulering revurdering`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)

        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(19.mars, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(19.mars, Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(19.mars, 0.0)
        assertDiff(-1431)

        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING)

        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING, AVVENTER_REVURDERING)
    }

    @Test
    fun `revurdering på tidligere skjæringstidspunkt mens nyere revurdering står i avventer godkjenning revurdering`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)

        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(19.mars, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(19.mars, Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(19.mars, 0.0)
        assertDiff(-1431)
        håndterSimulering(2.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING)

        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, AVVENTER_REVURDERING)
    }

    @Test
    fun `fire skjæringstidspunkter der første og siste blir revurdert - kun skjæringstidspunkter med endring i utbetaling skal utbetales`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)
        nyttVedtak(1.mai, 31.mai)
        nyttVedtak(1.juli, 31.juli)

        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(17.januar, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(4.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)

        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(17.juli, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.juli, Feriedag)))

        assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVVENTER_REVURDERING)
        assertTilstander(4.vedtaksperiode, AVVENTER_REVURDERING)

        nullstillTilstandsendringer()
        håndterYtelser(1.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(17.januar, 0.0)
        assertDiff(-1431)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(1.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVVENTER_REVURDERING)
        assertTilstander(4.vedtaksperiode, AVVENTER_REVURDERING)

        nullstillTilstandsendringer()
        håndterYtelser(2.vedtaksperiode)
        assertDiff(0)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVSLUTTET)
        assertTilstander(3.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(4.vedtaksperiode, AVVENTER_REVURDERING)

        nullstillTilstandsendringer()
        håndterYtelser(3.vedtaksperiode)
        assertDiff(0)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET)
        assertTilstander(3.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, AVSLUTTET)
        assertTilstander(4.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)

        nullstillTilstandsendringer()
        håndterYtelser(4.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(17.juli, 0.0)
        assertDiff(-1431)
        håndterSimulering(4.vedtaksperiode)
        håndterUtbetalingsgodkjenning(4.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET)
        assertTilstander(3.vedtaksperiode, AVSLUTTET)
        assertTilstander(4.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
    }

    @Test
    fun `fire skjæringstidspunkter der første og siste blir revurdert - kun skjæringstidspunkter med endring i utbetaling skal utbetales - første skjæringstidspunkt har forlengelse`() {
        nyttVedtak(1.desember(2017), 31.desember(2017))
        forlengVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)
        nyttVedtak(1.mai, 31.mai)
        nyttVedtak(1.juli, 31.juli)

        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(19.desember(2017), 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(19.desember(2017), Feriedag)))

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(4.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
        assertTilstander(5.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)

        nullstillTilstandsendringer()
        assertDag<Sykedag, NavDag>(17.juli, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.juli, Feriedag)))

        assertTilstander(1.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(3.vedtaksperiode, AVVENTER_REVURDERING)
        assertTilstander(4.vedtaksperiode, AVVENTER_REVURDERING)
        assertTilstander(5.vedtaksperiode, AVVENTER_REVURDERING)

        nullstillTilstandsendringer()
        håndterYtelser(2.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(19.desember(2017), 0.0)
        assertDiff(-1431)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(1.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING, AVVENTER_GODKJENNING_REVURDERING, TIL_UTBETALING, AVSLUTTET)
        assertTilstander(3.vedtaksperiode, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(4.vedtaksperiode, AVVENTER_REVURDERING)
        assertTilstander(5.vedtaksperiode, AVVENTER_REVURDERING)
    }

    @Test
    fun `revurdering der vi har vært innom Infotrygd`() {
        createPingPongPerson()

        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))

        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING)
    }

    @Test
    fun `avslår revurdering uten utbetaling - som tidligere har vært utbetalt`() {
        nyttVedtak(1.januar, 31.januar)
        håndterSøknad(Sykdom(17.januar, 31.januar, 100.prosent), Ferie(17.januar, 31.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode) // why
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(31.januar, Dagtype.Permisjonsdag)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode) // why
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, utbetalingGodkjent = false)
        assertSisteTilstand(1.vedtaksperiode, REVURDERING_FEILET)
        assertTrue(inspektør.periodeErIkkeForkastet(1.vedtaksperiode))
    }

    @Test
    fun `revurdere nyeste periode når vi har vært innom Infotrygd, deretter eldste periode`() {
        createPingPongPerson()

        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.mars, Feriedag)))

        assertTilstander(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))
        assertTilstander(1.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVSLUTTET, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_REVURDERING)
    }

    @Test
    fun `revurdere frem og tilbake mellom feriedag og sykedag hvor første overstyring blir utbetalt`() {
        nyttVedtak(1.januar, 31.januar)
        assertDag<Sykedag, NavDag>(17.januar, 1431.0)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        assertDag<Dag.Feriedag, Utbetalingsdag.Fridag>(17.januar, 0.0)
        assertDiff(-1431)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Sykedag, 100)))
        håndterYtelser(1.vedtaksperiode)
        assertDag<Sykedag, NavDag>(17.januar, 1431.0)
        assertDiff(1431)
    }

    @Test
    fun `overstyr utkast til revurdering - tidslinje`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))
        håndterYtelser(1.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING)
    }

    @Test
    fun `overstyr utkast til revurdering - med flere perioder`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        nullstillTilstandsendringer()
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.februar, Feriedag)))
        håndterYtelser(2.vedtaksperiode)

        assertTilstander(1.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstander(2.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_SIMULERING_REVURDERING)
    }

    @Test
    fun `overlappende ytelser ved revurdering skal gi warning, ikke error`() {
        nyttVedtak(1.januar, 31.januar)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Feriedag)))
        håndterYtelser(
            1.vedtaksperiode,
            foreldrepenger = listOf(1.januar til 10.januar
            )        )

        assertVarsel(RV_AY_5)
        assertIngenFunksjonelleFeil()
    }

    @Test
    fun `valider ytelser kun for de periodene som påvirkes av revurderingen`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)
        forlengVedtak(1.mars, 31.mars)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(2.februar, Feriedag)))
        håndterYtelser(3.vedtaksperiode, foreldrepenger = listOf(20.januar til 31.januar))

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstand(2.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING)
        assertTilstand(3.vedtaksperiode, AVVENTER_SIMULERING_REVURDERING)
        assertIngenVarsler(1.vedtaksperiode.filter())
        assertVarsel(RV_AY_5, 2.vedtaksperiode.filter())
        assertIngenVarsler(3.vedtaksperiode.filter())
    }

    @Test
    fun `Alle perioder som er en del av revurdering skal kunne avvise dager`() {
        nyttVedtak(1.januar, 31.januar)
        forlengVedtak(1.februar, 28.februar)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Sykedag, 19)))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(16.februar, Dagtype.Sykedag, 19)))
        håndterYtelser(2.vedtaksperiode)

        assertEquals(19, inspektør.sykdomstidslinje.inspektør.grader[17.januar])
        assertEquals(19, inspektør.sykdomstidslinje.inspektør.grader[16.februar])
        val utbetalingstidslinje = inspektør.utbetalingUtbetalingstidslinje(3)
        utbetalingstidslinje[17.januar].økonomi.inspektør.also { økonomi ->
            assertEquals(19, økonomi.totalGrad.roundToInt())
        }
        utbetalingstidslinje[16.februar].økonomi.inspektør.also { økonomi ->
            assertEquals(19, økonomi.totalGrad.roundToInt())
        }
        assertVarsel(RV_VV_4, 2.vedtaksperiode.filter())
        assertVarsel(RV_VV_4, 1.vedtaksperiode.filter())
    }

    @Test
    fun `maksdato hensyntar ikke fremtidige perioder ved revurdering`() {
        nyttVedtak(1.januar, 31.januar)
        nyttVedtak(1.mars, 31.mars)

        håndterOverstyrTidslinje(listOf(ManuellOverskrivingDag(17.januar, Dagtype.Sykedag, 50)))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertEquals(28.desember, inspektør.utbetaling(0).inspektør.maksdato)
        assertEquals(inspektør.utbetaling(0).inspektør.maksdato, inspektør.utbetaling(2).inspektør.maksdato)
        assertEquals(11, inspektør.utbetaling(0).inspektør.forbrukteSykedager)
        assertEquals(inspektør.utbetaling(0).inspektør.forbrukteSykedager, inspektør.utbetaling(2).inspektør.forbrukteSykedager)
    }

    private inline fun <reified D: Dag, reified UD: Utbetalingsdag>assertDag(dato: LocalDate, arbeidsgiverbeløp: Double?, personbeløp: Double? = 0.0) {
        inspektør.sykdomshistorikk.sykdomstidslinje()[dato].let {
            assertTrue(it is D) { "Forventet at $dato er ${D::class.simpleName} men var ${it::class.simpleName}"}
        }
        inspektør.sisteUtbetalingUtbetalingstidslinje()[dato].let {
            assertTrue(it is UD) { "Forventet at $dato er ${UD::class.simpleName} men var ${it::class.simpleName}"}
            assertEquals(arbeidsgiverbeløp?.daglig, it.økonomi.inspektør.arbeidsgiverbeløp)
            assertEquals(personbeløp?.daglig, it.økonomi.inspektør.personbeløp)
        }
    }

    private fun assertDiff(diff: Int) {
        assertEquals(diff, inspektør.utbetalinger.last().inspektør.nettobeløp)
    }
}