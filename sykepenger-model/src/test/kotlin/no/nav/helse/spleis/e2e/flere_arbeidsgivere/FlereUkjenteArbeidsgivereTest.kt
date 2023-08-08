package no.nav.helse.spleis.e2e.flere_arbeidsgivere

import java.time.LocalDate
import no.nav.helse.Toggle
import no.nav.helse.dsl.lagStandardSammenligningsgrunnlag
import no.nav.helse.dsl.lagStandardSykepengegrunnlag
import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonObserver.OverstyringIgangsatt.VedtaksperiodeData
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GJENNOMFØRT_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_REVURDERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING_REVURDERING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_SV_2
import no.nav.helse.person.inntekt.Inntektsmelding
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.person.nullstillTilstandsendringer
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertForkastetPeriodeTilstander
import no.nav.helse.spleis.e2e.assertFunksjonellFeil
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyPeriode
import no.nav.helse.spleis.e2e.nyeVedtak
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class FlereUkjenteArbeidsgivereTest : AbstractEndToEndTest() {

    @Test
    fun `én arbeidsgiver blir to - søknad for ag1 først`() {
        nyttVedtak(1.januar, 31.januar, orgnummer = a1)
        nyPeriode(1.februar til 20.februar, a1)
        nyPeriode(1.februar til 20.februar, a2)
        assertFunksjonellFeil("Minst en arbeidsgiver inngår ikke i sykepengegrunnlaget", 1.vedtaksperiode.filter(a2))
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertTrue(inspektør(a1).periodeErForkastet(2.vedtaksperiode))
        assertTrue(inspektør(a2).periodeErForkastet(1.vedtaksperiode))
    }

    @Test
    fun `én arbeidsgiver blir to - forlenges kun av ny ag`() {
        nyttVedtak(1.januar, 31.januar, orgnummer = a1)
        nyPeriode(1.februar til 20.februar, a2)
        håndterInntektsmelding(listOf(1.februar til 16.februar), orgnummer = a2)
        val vilkårsgrunnlag = inspektør.vilkårsgrunnlag(1.vedtaksperiode)
        assertNotNull(vilkårsgrunnlag)
        val sykepengegrunnlagInspektør = vilkårsgrunnlag.inspektør.sykepengegrunnlag.inspektør
        assertEquals(1, sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysninger.size)

        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a1).inspektør.also {
            assertEquals(INNTEKT, it.inntektsopplysning.inspektør.beløp)
            assertEquals(Inntektsmelding::class, it.inntektsopplysning::class)
        }
        assertNull(sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver[a2])


        assertFunksjonellFeil(RV_SV_2, 1.vedtaksperiode.filter(a2))
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertTrue(inspektør(a2).periodeErForkastet(1.vedtaksperiode))
    }

    @Test
    fun `én arbeidsgiver blir to - førstegangsbehandlingen hos ag2 forkastes`() {
        nyttVedtak(1.januar, 31.januar, orgnummer = a1)
        forlengVedtak(1.februar, 28.februar, orgnummer = a1)
        forlengVedtak(1.mars, 31.mars, orgnummer = a1)

        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT + 500.daglig, orgnummer = a1)
        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        håndterSimulering(3.vedtaksperiode, orgnummer = a1)

        håndterInntektsmelding(listOf(1.mars til 16.mars), begrunnelseForReduksjonEllerIkkeUtbetalt = "TidligereVirksomhet", orgnummer = a2)
        nullstillTilstandsendringer()
        nyPeriode(1.mars til 20.mars, a2)

        assertTrue(inspektør(a2).periodeErForkastet(1.vedtaksperiode)) {
            "Om dette ikke lengre stemmer så kan testen slettes. Det finnes en tilsvarende test som sjekker 'ikke forkaster'-scenario."
        }

        val overstyringerIgangsatt = observatør.overstyringIgangsatt
        assertEquals(2, overstyringerIgangsatt.size)

        overstyringerIgangsatt.first().also { event ->
            assertEquals(PersonObserver.OverstyringIgangsatt(
                årsak = "KORRIGERT_INNTEKTSMELDING_ARBEIDSGIVERPERIODE",
                skjæringstidspunkt = 1.januar,
                periodeForEndring = 1.januar til 31.januar,
                berørtePerioder = listOf(
                    VedtaksperiodeData(a1, 1.vedtaksperiode.id(a1), 1.januar til 31.januar, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a1, 2.vedtaksperiode.id(a1), 1.februar til 28.februar, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a1, 3.vedtaksperiode.id(a1), 1.mars til 31.mars, 1.januar, "REVURDERING")
                )
            ), event)
        }

        overstyringerIgangsatt.last().also { event ->
            assertEquals(PersonObserver.OverstyringIgangsatt(
                årsak = "KORRIGERT_INNTEKTSMELDING_INNTEKTSOPPLYSNINGER",
                skjæringstidspunkt = 1.januar,
                periodeForEndring = 1.januar til 1.januar,
                berørtePerioder = listOf(
                    VedtaksperiodeData(a1, 1.vedtaksperiode.id(a1), 1.januar til 31.januar, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a1, 2.vedtaksperiode.id(a1), 1.februar til 28.februar, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a1, 3.vedtaksperiode.id(a1), 1.mars til 31.mars, 1.januar, "REVURDERING")
                )
            ), event)
        }

        assertTilstander(1.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING, orgnummer = a1)
        assertTilstander(3.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, orgnummer = a1)
        assertForkastetPeriodeTilstander(1.vedtaksperiode, START, TIL_INFOTRYGD, orgnummer = a2)
    }


    @Test
    fun `én arbeidsgiver blir to - førstegangsbehandlingen hos ag2 forkastes med toggle disabled`() = Toggle.RevurdereAgpFraIm.disable {
        nyttVedtak(1.januar, 31.januar, orgnummer = a1)
        forlengVedtak(1.februar, 28.februar, orgnummer = a1)
        forlengVedtak(1.mars, 31.mars, orgnummer = a1)

        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = INNTEKT + 500.daglig, orgnummer = a1)
        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        håndterSimulering(3.vedtaksperiode, orgnummer = a1)

        håndterInntektsmelding(listOf(1.mars til 16.mars), begrunnelseForReduksjonEllerIkkeUtbetalt = "TidligereVirksomhet", orgnummer = a2)
        nullstillTilstandsendringer()
        nyPeriode(1.mars til 20.mars, a2)

        assertTrue(inspektør(a2).periodeErForkastet(1.vedtaksperiode)) {
            "Om dette ikke lengre stemmer så kan testen slettes. Det finnes en tilsvarende test som sjekker 'ikke forkaster'-scenario."
        }

        val overstyringerIgangsatt = observatør.overstyringIgangsatt
        assertEquals(1, overstyringerIgangsatt.size)

        overstyringerIgangsatt.last().also { event ->
            assertEquals(PersonObserver.OverstyringIgangsatt(
                årsak = "KORRIGERT_INNTEKTSMELDING_INNTEKTSOPPLYSNINGER",
                skjæringstidspunkt = 1.januar,
                periodeForEndring = 1.januar til 1.januar,
                berørtePerioder = listOf(
                    VedtaksperiodeData(a1, 1.vedtaksperiode.id(a1), 1.januar til 31.januar, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a1, 2.vedtaksperiode.id(a1), 1.februar til 28.februar, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a1, 3.vedtaksperiode.id(a1), 1.mars til 31.mars, 1.januar, "REVURDERING")
                )
            ), event)
        }

        assertTilstander(1.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING, orgnummer = a1)
        assertTilstander(3.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, orgnummer = a1)
        assertForkastetPeriodeTilstander(1.vedtaksperiode, START, TIL_INFOTRYGD, orgnummer = a2)
    }


    @Test
    fun `to arbeidsgivere - ny overlappende førstegangsbehandlingen hos ag2`() {
        val inntektA1 = INNTEKT + 500.daglig
        val inntektA2 = INNTEKT

        nyeVedtak(1.januar, 31.januar, a1, a2, inntekt = INNTEKT)
        forlengVedtak(1.februar, 28.februar, orgnummer = a1)
        forlengVedtak(1.mars, 31.mars, orgnummer = a1)

        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = inntektA1, orgnummer = a1)
        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        håndterSimulering(3.vedtaksperiode, orgnummer = a1)

        håndterInntektsmelding(listOf(1.mars til 16.mars), beregnetInntekt = inntektA2, orgnummer = a2)
        nullstillTilstandsendringer()
        nyPeriode(1.mars til 20.mars, a2)

        val vilkårsgrunnlag = inspektør(a1).vilkårsgrunnlag(3.vedtaksperiode)?.inspektør ?: fail { "må ha vilkårsgrunnlag" }
        val inntektsopplysninger = vilkårsgrunnlag.sykepengegrunnlag.inspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver

        assertEquals(2, inntektsopplysninger.size)
        val a1Inspektør = inntektsopplysninger.getValue(a1).inspektør
        assertEquals(Inntektsmelding::class, a1Inspektør.inntektsopplysning::class)
        assertEquals(inntektA1, a1Inspektør.inntektsopplysning.inspektør.beløp)
        val a2Inspektør = inntektsopplysninger.getValue(a2).inspektør
        assertEquals(Inntektsmelding::class, a2Inspektør.inntektsopplysning::class)
        assertEquals(inntektA2, a2Inspektør.inntektsopplysning.inspektør.beløp)

        val overstyringerIgangsatt = observatør.overstyringIgangsatt
        assertEquals(4, overstyringerIgangsatt.size)

        overstyringerIgangsatt[0].also { event ->
            assertEquals(PersonObserver.OverstyringIgangsatt(
                årsak = "KORRIGERT_INNTEKTSMELDING_ARBEIDSGIVERPERIODE",
                skjæringstidspunkt = 1.januar,
                periodeForEndring = 1.januar til 31.januar,
                berørtePerioder = listOf(
                    VedtaksperiodeData(a1, 1.vedtaksperiode.id(a1), 1.januar til 31.januar, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a1, 2.vedtaksperiode.id(a1), 1.februar til 28.februar, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a1, 3.vedtaksperiode.id(a1), 1.mars til 31.mars, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a2, 1.vedtaksperiode.id(a2), 1.januar til 31.januar, 1.januar, "REVURDERING")
                )
            ), event)
        }

        overstyringerIgangsatt[1].also { event ->
            assertEquals(PersonObserver.OverstyringIgangsatt(
                årsak = "KORRIGERT_INNTEKTSMELDING_INNTEKTSOPPLYSNINGER",
                skjæringstidspunkt = 1.januar,
                periodeForEndring = 1.januar til 1.januar,
                berørtePerioder = listOf(
                    VedtaksperiodeData(a1, 1.vedtaksperiode.id(a1), 1.januar til 31.januar, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a1, 2.vedtaksperiode.id(a1), 1.februar til 28.februar, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a1, 3.vedtaksperiode.id(a1), 1.mars til 31.mars, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a2, 1.vedtaksperiode.id(a2), 1.januar til 31.januar, 1.januar, "REVURDERING")
                )
            ), event)
        }


        overstyringerIgangsatt[2].also { event ->
            assertEquals(PersonObserver.OverstyringIgangsatt(
                årsak = "NY_PERIODE",
                skjæringstidspunkt = 1.januar,
                periodeForEndring = 1.mars til 20.mars,
                berørtePerioder = listOf(
                    VedtaksperiodeData(a1, 3.vedtaksperiode.id(a1), 1.mars til 31.mars, 1.januar, "REVURDERING")
                )
            ), event)
        }

        assertTilstander(1.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, orgnummer = a1)
        assertTilstander(3.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
    }

    @Test
    fun `to arbeidsgivere - ny overlappende førstegangsbehandlingen hos ag2 med toggle disabled`() = Toggle.RevurdereAgpFraIm.disable {
        val inntektA1 = INNTEKT + 500.daglig
        val inntektA2 = INNTEKT

        nyeVedtak(1.januar, 31.januar, a1, a2, inntekt = INNTEKT)
        forlengVedtak(1.februar, 28.februar, orgnummer = a1)
        forlengVedtak(1.mars, 31.mars, orgnummer = a1)

        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = inntektA1, orgnummer = a1)
        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        håndterSimulering(3.vedtaksperiode, orgnummer = a1)

        håndterInntektsmelding(listOf(1.mars til 16.mars), beregnetInntekt = inntektA2, orgnummer = a2)
        nullstillTilstandsendringer()
        nyPeriode(1.mars til 20.mars, a2)

        val vilkårsgrunnlag = inspektør(a1).vilkårsgrunnlag(3.vedtaksperiode)?.inspektør ?: fail { "må ha vilkårsgrunnlag" }
        val inntektsopplysninger = vilkårsgrunnlag.sykepengegrunnlag.inspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver

        assertEquals(2, inntektsopplysninger.size)
        val a1Inspektør = inntektsopplysninger.getValue(a1).inspektør
        assertEquals(Inntektsmelding::class, a1Inspektør.inntektsopplysning::class)
        assertEquals(inntektA1, a1Inspektør.inntektsopplysning.inspektør.beløp)
        val a2Inspektør = inntektsopplysninger.getValue(a2).inspektør
        assertEquals(Inntektsmelding::class, a2Inspektør.inntektsopplysning::class)
        assertEquals(inntektA2, a2Inspektør.inntektsopplysning.inspektør.beløp)

        val overstyringerIgangsatt = observatør.overstyringIgangsatt
        assertEquals(3, overstyringerIgangsatt.size)

        overstyringerIgangsatt[0].also { event ->
            assertEquals(PersonObserver.OverstyringIgangsatt(
                årsak = "KORRIGERT_INNTEKTSMELDING_INNTEKTSOPPLYSNINGER",
                skjæringstidspunkt = 1.januar,
                periodeForEndring = 1.januar.somPeriode(),
                berørtePerioder = listOf(
                    VedtaksperiodeData(a1, 1.vedtaksperiode.id(a1), 1.januar til 31.januar, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a1, 2.vedtaksperiode.id(a1), 1.februar til 28.februar, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a1, 3.vedtaksperiode.id(a1), 1.mars til 31.mars, 1.januar, "REVURDERING"),
                    VedtaksperiodeData(a2, 1.vedtaksperiode.id(a2), 1.januar til 31.januar, 1.januar, "REVURDERING")
                )
            ), event)
        }

        overstyringerIgangsatt[1].also { event ->
            assertEquals(PersonObserver.OverstyringIgangsatt(
                årsak = "NY_PERIODE",
                skjæringstidspunkt = 1.januar,
                periodeForEndring = 1.mars til 20.mars,
                berørtePerioder = listOf(
                    VedtaksperiodeData(a1, 3.vedtaksperiode.id(a1), 1.mars til 31.mars, 1.januar, "REVURDERING")
                )
            ), event)
        }

        assertTilstander(1.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, orgnummer = a1)
        assertTilstander(2.vedtaksperiode, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, orgnummer = a1)
        assertTilstander(3.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, AVVENTER_REVURDERING, AVVENTER_GJENNOMFØRT_REVURDERING, AVVENTER_HISTORIKK_REVURDERING, orgnummer = a1)
        assertTilstander(1.vedtaksperiode, AVVENTER_REVURDERING, orgnummer = a2)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
    }

    @Test
    fun `én arbeidsgiver blir to - førstegangsbehandlingen hos ag2 forkastes ikke`() {
        val inntektA1 = INNTEKT + 500.daglig

        nyttVedtak(1.januar, 31.januar, orgnummer = a1)
        forlengVedtak(1.februar, 28.februar, orgnummer = a1)
        forlengVedtak(1.mars, 31.mars, orgnummer = a1)

        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = inntektA1, orgnummer = a1)
        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        håndterSimulering(3.vedtaksperiode, orgnummer = a1)

        nullstillTilstandsendringer()
        nyPeriode(1.mars til 20.mars, a2)

        assertForkastetPeriodeTilstander(1.vedtaksperiode, START, TIL_INFOTRYGD, orgnummer = a2)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_GODKJENNING_REVURDERING, orgnummer = a1)
    }

    @Test
    fun `to arbeidsgivere - ny overlappende førstegangsbehandling hos ag2 som først var ansett som ghost`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to INNTEKT, a2 to INNTEKT), 1.januar),
            inntektsvurdering = lagStandardSammenligningsgrunnlag(listOf(a1 to INNTEKT, a2 to INNTEKT), 1.januar),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH),
            ), orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        nullstillTilstandsendringer()

        // a2 sent til festen
        val id = håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2, begrunnelseForReduksjonEllerIkkeUtbetalt = "ja")
        assertEquals(id, observatør.inntektsmeldingIkkeHåndtert.single())
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a2)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        assertVarsel(Varselkode.RV_IM_8, 1.vedtaksperiode.filter(a2))
        assertTilstander(
            1.vedtaksperiode,
            AVVENTER_GODKJENNING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING, orgnummer = a1
        )
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
    }

    @Test
    fun `to arbeidsgivere - ny overlappende førstegangsbehandling hos ag2 som først var ansett som ghost - a1 revurderes`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to INNTEKT, a2 to INNTEKT), 1.januar),
            inntektsvurdering = lagStandardSammenligningsgrunnlag(listOf(a1 to INNTEKT, a2 to INNTEKT), 1.januar),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH),
            ), orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()
        nullstillTilstandsendringer()

        // a2 sent til festen
        val imId = håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2, begrunnelseForReduksjonEllerIkkeUtbetalt = "ja")
        assertEquals(imId, observatør.inntektsmeldingIkkeHåndtert.single())
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a2)
        val søknadId = håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)

        assertEquals(listOf(
            Dokumentsporing.søknad(søknadId),
            Dokumentsporing.inntektsmeldingDager(imId),
            Dokumentsporing.inntektsmeldingInntekt(imId)
        ), inspektør(a2).hendelser(1.vedtaksperiode))

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()

        assertVarsel(Varselkode.RV_IM_8, 1.vedtaksperiode.filter(a2))
        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET,
            orgnummer = a1
        )
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, orgnummer = a2)
    }

    @Test
    fun `to arbeidsgivere - ny overlappende førstegangsbehandling hos ag2 som først var antatt å være frisk - men tidlig inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        val imId = håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2, begrunnelseForReduksjonEllerIkkeUtbetalt = "ja")
        assertEquals(imId, observatør.inntektsmeldingIkkeHåndtert.single())

        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode,
            inntektsvurderingForSykepengegrunnlag = lagStandardSykepengegrunnlag(listOf(a1 to INNTEKT, a2 to INNTEKT), 1.januar),
            inntektsvurdering = lagStandardSammenligningsgrunnlag(listOf(a1 to INNTEKT, a2 to INNTEKT), 1.januar),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, LocalDate.EPOCH),
                Vilkårsgrunnlag.Arbeidsforhold(a2, LocalDate.EPOCH),
            ), orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()
        nullstillTilstandsendringer()

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a2) // a2 sent til festen, men med ting liggende i vilkårsgrunnlaget
        val sykepengegrunnlagInspektør = inspektør.vilkårsgrunnlag(1.vedtaksperiode)!!.inspektør.sykepengegrunnlag.inspektør
        sykepengegrunnlagInspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(a2).inspektør.also {
            assertEquals(INNTEKT, it.inntektsopplysning.fastsattÅrsinntekt())
            assertEquals(Refusjonsopplysning(imId, 1.januar, null, beløp = INNTEKT), it.refusjonsopplysninger.inspektør.refusjonsopplysninger.single())
        }

        val søknadId = håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        assertEquals(
            listOf(
                Dokumentsporing.søknad(søknadId),
                Dokumentsporing.inntektsmeldingDager(imId),
                Dokumentsporing.inntektsmeldingInntekt(imId)
            ), inspektør(a2).hendelser(1.vedtaksperiode)
        )
        assertVarsel(Varselkode.RV_IM_8, 1.vedtaksperiode.filter(a2))
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt()
        assertTilstander(
            1.vedtaksperiode,
            AVSLUTTET,
            AVVENTER_REVURDERING,
            AVVENTER_GJENNOMFØRT_REVURDERING,
            AVVENTER_HISTORIKK_REVURDERING,
            AVVENTER_SIMULERING_REVURDERING,
            AVVENTER_GODKJENNING_REVURDERING,
            TIL_UTBETALING,
            AVSLUTTET,
            orgnummer = a1
        )
        assertTilstander(1.vedtaksperiode, START, AVVENTER_INFOTRYGDHISTORIKK, AVVENTER_INNTEKTSMELDING, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK, orgnummer = a2)
    }

    @Test
    fun `Bytter arbeidsgiver i løpet i sykefraværet - ny arbeidsgiver kastes ut`() {
        nyttVedtak(1.januar, 31.januar, orgnummer = a1)
        nyPeriode(1.februar til 28.februar, orgnummer = a2)
        assertFunksjonellFeil(RV_SV_2, 1.vedtaksperiode.filter(a2))
        assertForkastetPeriodeTilstander(1.vedtaksperiode, START, TIL_INFOTRYGD, orgnummer = a2)
    }
}