package no.nav.helse.spleis.e2e.brukerutbetaling

import no.nav.helse.februar
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Inntektsmelding.Refusjon.EndringIRefusjon
import no.nav.helse.hendelser.Inntektsvurdering
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INFOTRYGDHISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_2
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_3
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_IM_4
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_RE_1
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertIngenVarsel
import no.nav.helse.spleis.e2e.assertIngenVarsler
import no.nav.helse.spleis.e2e.assertSisteTilstand
import no.nav.helse.spleis.e2e.assertTilstander
import no.nav.helse.spleis.e2e.assertUtbetalingsbeløp
import no.nav.helse.spleis.e2e.assertVarsel
import no.nav.helse.spleis.e2e.finnSkjæringstidspunkt
import no.nav.helse.spleis.e2e.grunnlag
import no.nav.helse.spleis.e2e.håndterInntektsmelding
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknad
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.nyttVedtak
import no.nav.helse.spleis.e2e.repeat
import no.nav.helse.spleis.e2e.sammenligningsgrunnlag
import no.nav.helse.testhelpers.assertNotNull
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DelvisRefusjonTest : AbstractEndToEndTest() {

    @Test
    fun `Full refusjon til en arbeidsgiver med RefusjonPerDag på`() {
        nyttVedtak(1.januar, 31.januar, refusjon = Inntektsmelding.Refusjon(INNTEKT, null, emptyList()))

        assertTrue(inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.isNotEmpty())
        inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.forEach { assertEquals(1431, it.beløp) }
        assertTrue(inspektør.utbetalinger.last().inspektør.personOppdrag.isEmpty())
        assertUtbetalingsbeløp(1.vedtaksperiode, 0, 1431, subset = 1.januar til 16.januar)
        assertUtbetalingsbeløp(1.vedtaksperiode, 1431, 1431, subset = 17.januar til 31.januar)
    }

    @Test
    fun `Full refusjon til en arbeidsgiver med forlengelse og opphørsdato treffer ferie`() {
        nyttVedtak(1.januar, 31.januar, refusjon = Inntektsmelding.Refusjon(INNTEKT, 27.februar, emptyList()))

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(27.februar, 28.februar))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING
        )
        assertTrue(inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.isNotEmpty())
        inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.forEach { assertEquals(1431, it.beløp) }
        assertTrue(inspektør.utbetalinger.last().inspektør.personOppdrag.isEmpty())
        assertUtbetalingsbeløp(2.vedtaksperiode, 1431, 1431, subset = 1.februar til 26.februar)
        assertUtbetalingsbeløp(2.vedtaksperiode, 0, 1431, subset = 27.februar til 27.februar)
        assertUtbetalingsbeløp(2.vedtaksperiode, 0, 0, subset = 28.februar til 28.februar)
    }

    @Test
    fun `Refusjonsbeløpet er forskjellig fra beregnet inntekt i inntektsmeldingen`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            beregnetInntekt = 30000.månedlig,
            refusjon = Inntektsmelding.Refusjon(25000.månedlig, null, emptyList())
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Test
    fun `arbeidsgiver refunderer ikke`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            beregnetInntekt = INNTEKT,
            refusjon = Inntektsmelding.Refusjon(INGEN, null, emptyList())
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Test
    fun `tidligere vedtaksperiode har opphør i refusjon`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, 20.januar, emptyList())
        )
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

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        håndterInntektsmelding(
            listOf(1.mars til 16.mars)
        )
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )


        assertTrue(inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.isNotEmpty())
        inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.forEach { assertEquals(1431, it.beløp) }
        assertTrue(inspektør.utbetalinger.last().inspektør.personOppdrag.isEmpty())
        assertUtbetalingsbeløp(2.vedtaksperiode, 0, 1431, subset = 1.mars til 16.mars)
        assertUtbetalingsbeløp(2.vedtaksperiode, 1431, 1431, subset = 17.mars til 31.mars)
    }

    @Test
    fun `kaster ikke ut vedtaksperiode når refusjonopphører`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, 20.januar, emptyList())
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Test
    fun `ikke kast ut vedtaksperiode ved endring i refusjon`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, null, listOf(EndringIRefusjon(15000.månedlig, 20.januar)))
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Test
    fun `kaster ikke ut vedtaksperiode hvor endring i refusjon er etter perioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, null, listOf(EndringIRefusjon(15000.månedlig, 1.februar)))
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
        assertUtbetalingsbeløp(1.vedtaksperiode, 0, 1431, subset = 1.januar til 16.januar)
        assertUtbetalingsbeløp(1.vedtaksperiode, 1431, 1431, subset = 17.januar til 31.januar)
    }

    @Test
    fun `ikke kast ut vedtaksperiode ved ferie i slutten av perioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(25.januar, 31.januar))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, 24.januar, emptyList())
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )

        assertTrue(inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.isNotEmpty())
        inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.forEach { assertEquals(1431, it.beløp) }
        assertTrue(inspektør.utbetalinger.last().inspektør.personOppdrag.isEmpty())

        assertUtbetalingsbeløp(1.vedtaksperiode, 0, 1431, 1.januar til 16.januar)
        assertUtbetalingsbeløp(1.vedtaksperiode, 1431, 1431, 17.januar til 24.januar)
        assertUtbetalingsbeløp(1.vedtaksperiode, 0, 0, 25.januar til 31.januar)
    }

    @Test
    fun `to arbeidsgivere med ulik fom hvor den første har utbetalingsdager før arbeisdgiverperioden til den andre, ingen felles utbetalingsdager`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(21.januar, 10.februar), orgnummer = a2)

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(21.januar, 10.februar, 100.prosent), orgnummer = a2)

        håndterInntektsmelding(arbeidsgiverperioder = listOf(1.januar til 16.januar), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING, orgnummer = a2)

        håndterInntektsmelding(arbeidsgiverperioder = listOf(21.januar til 5.februar), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)

        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurdering = Inntektsvurdering(
                listOf(
                    sammenligningsgrunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12)),
                    sammenligningsgrunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12))
                )
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3)),
                    grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3))
                )
            , arbeidsforhold = emptyList())
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)

        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 0,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 1.januar til 16.januar,
            orgnummer = a1
        )
        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 1080,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 17.januar til 31.januar,
            orgnummer = a1
        )
        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 0,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 1.februar til 10.februar,
            orgnummer = a1
        )
        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 0,
            forventetArbeidsgiverRefusjonsbeløp = 0,
            subset = 1.januar til 20.januar,
            orgnummer = a2
        )
        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 0,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 21.januar til 5.februar,
            orgnummer = a2
        )
        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 1080,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 6.februar til 10.februar,
            orgnummer = a2
        )
    }

    @Test
    fun `to arbeidsgivere med ulik fom hvor den første har utbetalingsdager før arbeisdgiverperioden til den andre`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 10.februar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(21.januar, 10.februar), orgnummer = a2)

        håndterSøknad(Sykdom(1.januar, 10.februar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(21.januar, 10.februar, 100.prosent), orgnummer = a2)

        håndterInntektsmelding(arbeidsgiverperioder = listOf(1.januar til 16.januar), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING, orgnummer = a2)

        håndterInntektsmelding(arbeidsgiverperioder = listOf(21.januar til 5.februar), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)

        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurdering = Inntektsvurdering(
                listOf(
                    sammenligningsgrunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12)),
                    sammenligningsgrunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12))
                )
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3)),
                    grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3))
                )
            , arbeidsforhold = emptyList())
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)

        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 0,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 1.januar til 16.januar,
            orgnummer = a1
        )
        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 1080,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 17.januar til 5.februar,
            orgnummer = a1
        )
        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 1081,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 6.februar til 10.februar,
            orgnummer = a1
        )
        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 0,
            forventetArbeidsgiverRefusjonsbeløp = 0,
            subset = 1.januar til 20.januar,
            orgnummer = a2
        )
        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 0,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 21.januar til 5.februar,
            orgnummer = a2
        )
        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 1080,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 6.februar til 10.februar,
            orgnummer = a2
        )
    }

    @Test
    fun `to arbeidsgivere med ulik fom hvor den andre har utbetalingsdager før arbeidsgiverperioden til den første`() {
        håndterSykmelding(Sykmeldingsperiode(21.januar, 10.februar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 10.februar), orgnummer = a2)

        håndterSøknad(Sykdom(21.januar, 10.februar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 10.februar, 100.prosent), orgnummer = a2)

        håndterInntektsmelding(arbeidsgiverperioder = listOf(1.januar til 16.januar), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING, orgnummer = a1)

        håndterInntektsmelding(arbeidsgiverperioder = listOf(21.januar til 5.februar), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING, orgnummer = a2)
        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            orgnummer = a2,
            inntektsvurdering = Inntektsvurdering(
                listOf(
                    sammenligningsgrunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12)),
                    sammenligningsgrunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12))
                )
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3)),
                    grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3))
                ), arbeidsforhold = emptyList()
            )
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)

        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 0,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 21.januar til 5.februar,
            orgnummer = a1
        )
        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 1081,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 6.februar til 10.februar,
            orgnummer = a1
        )
        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 0,
            forventetArbeidsgiverRefusjonsbeløp = 0,
            subset = 1.januar til 20.januar,
            orgnummer = a1
        )
        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 0,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 1.januar til 16.januar,
            orgnummer = a2
        )
        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 1080,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 17.januar til 20.januar,
            orgnummer = a2
        )
        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 1080,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 21.januar til 5.februar,
            orgnummer = a2
        )
        assertUtbetalingsbeløp(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            forventetArbeidsgiverbeløp = 1080,
            forventetArbeidsgiverRefusjonsbeløp = 1431,
            subset = 6.februar til 10.februar,
            orgnummer = a2
        )
    }

    @Test
    fun `gradert sykmelding med en arbeidsgiver`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT / 2, null, emptyList())
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Test
    fun `korrigerende inntektsmelding endrer på refusjonsbeløp`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, null, emptyList())
        )
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            beregnetInntekt = INNTEKT+100.månedlig,
            refusjon = Inntektsmelding.Refusjon(INNTEKT / 2, null, emptyList())
        )
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INFOTRYGDHISTORIKK,
            AVVENTER_INNTEKTSMELDING,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
        val vilkårsgrunnlag = inspektør.vilkårsgrunnlag(1.januar)
        assertNotNull(vilkårsgrunnlag)
        val inntektsopplysninger = vilkårsgrunnlag.inspektør.sykepengegrunnlag.inspektør.arbeidsgiverInntektsopplysningerPerArbeidsgiver.getValue(ORGNUMMER).inspektør
        assertEquals(INNTEKT+100.månedlig, inntektsopplysninger.inntektsopplysning.inspektør.beløp)
        assertEquals(INNTEKT/2, inntektsopplysninger.refusjonsopplysninger.inspektør.refusjonsopplysninger.single().inspektør.beløp)
    }

    @Test
    fun `to arbeidsgivere hvor andre arbeidsgiver har delvis refusjon`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2, refusjon = Inntektsmelding.Refusjon(
            INNTEKT, 20.januar))
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurdering = Inntektsvurdering(
                listOf(
                    sammenligningsgrunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12)),
                    sammenligningsgrunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12))
                )
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3)),
                    grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3))
                ), arbeidsforhold = emptyList()
            ),
            orgnummer = a1
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        inspektør(a1).utbetaling(0).also { utbetaling ->
            assertEquals(2, utbetaling.inspektør.arbeidsgiverOppdrag.size)
            utbetaling.inspektør.arbeidsgiverOppdrag[0].inspektør.also { linje ->
                assertEquals(1081, linje.beløp)
                assertEquals(17.januar til 21.januar, linje.fom til linje.tom)
            }
            utbetaling.inspektør.arbeidsgiverOppdrag[1].inspektør.also { linje ->
                assertEquals(1431, linje.beløp)
                assertEquals(22.januar til 31.januar, linje.fom til linje.tom)
            }
            assertTrue(utbetaling.inspektør.personOppdrag.isEmpty())
        }
        inspektør(a2).utbetaling(1).also { utbetaling ->
            assertEquals(1, utbetaling.inspektør.arbeidsgiverOppdrag.size)
            utbetaling.inspektør.arbeidsgiverOppdrag[0].inspektør.also { linje ->
                assertEquals(1080, linje.beløp)
                assertEquals(17.januar til 19.januar, linje.fom til linje.tom)
            }

            assertEquals(1, utbetaling.inspektør.personOppdrag.size)
            utbetaling.inspektør.personOppdrag[0].inspektør.also { linje ->
                assertEquals(730, linje.beløp)
                assertEquals(22.januar til 31.januar, linje.fom til linje.tom)
            }
        }

    }

    @Test
    fun `Første utbetalte dag er før første fraværsdag`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(), førsteFraværsdag = 17.januar)

        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertIngenVarsel(RV_IM_2, 1.vedtaksperiode.filter())
        assertVarsel(RV_IM_3, 1.vedtaksperiode.filter())
        assertVarsel(RV_RE_1, 1.vedtaksperiode.filter())
    }

    @Test
    fun `Korrigerende inntektsmelding med feil skjæringstidspunkt går til manuell behandling på grunn av warning`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar)
        håndterInntektsmelding(emptyList(), førsteFraværsdag = 17.januar)

        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertVarsel(RV_IM_4, 1.vedtaksperiode.filter())
    }

    @Test
    fun `arbeidsgiver sender unødvendig inntektsmelding ved forlengelse før sykmelding`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterInntektsmelding(emptyList(), førsteFraværsdag = 1.februar)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)

        assertIngenVarsler(1.vedtaksperiode.filter())
        assertIngenVarsler(2.vedtaksperiode.filter())
    }

    @Test
    fun `to arbeidsgivere, hvor den andre har opphør i refusjon`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2, refusjon = Inntektsmelding.Refusjon(
            INNTEKT, 15.januar, emptyList()))
        håndterVilkårsgrunnlag(
            1.vedtaksperiode, orgnummer = a1, inntektsvurdering = Inntektsvurdering(
                listOf(
                    sammenligningsgrunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12)),
                    sammenligningsgrunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12))
                )
            )
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        inspektør(a1).utbetaling(0).also { utbetaling ->
            val linje = utbetaling.inspektør.arbeidsgiverOppdrag[0].inspektør
            assertEquals(1431, linje.beløp)
            assertEquals(17.januar til 31.januar, linje.fom til linje.tom)
            assertTrue(utbetaling.inspektør.personOppdrag.isEmpty())
        }
        inspektør(a2).utbetaling(1).also { utbetaling ->
            val linje = utbetaling.inspektør.personOppdrag[0].inspektør
            assertEquals(730, linje.beløp)
            assertEquals(17.januar til 31.januar, linje.fom til linje.tom)
            assertTrue(utbetaling.inspektør.arbeidsgiverOppdrag.isEmpty())
        }
    }
}
