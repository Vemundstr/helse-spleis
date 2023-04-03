package no.nav.helse.spleis.e2e.refusjon

import java.util.UUID
import no.nav.helse.august
import no.nav.helse.dsl.AbstractDslTest
import no.nav.helse.dsl.TestPerson.Companion.INNTEKT
import no.nav.helse.februar
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.i
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.juli
import no.nav.helse.oktober
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.aktivitetslogg.Varselkode.RV_RE_1
import no.nav.helse.person.inntekt.Refusjonsopplysning
import no.nav.helse.person.inntekt.Refusjonsopplysning.Refusjonsopplysninger
import no.nav.helse.september
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RefusjonsopplysningerE2ETest : AbstractDslTest() {

    @Test
    fun `første fraværsdag oppgitt til dagen etter arbeidsgiverperioden`(){
        a1 {
            nyttVedtak(førsteFraværsdag = 17.januar, arbeidsgiverperiode = listOf(1.januar til 16.januar), fom = 1.januar, tom = 31.januar)
            assertIngenInfoSomInneholder("Mangler refusjonsopplysninger på orgnummer")
        }
    }

    @Test
    fun `første fraværsdag oppgitt til dagen etter arbeidsgiverperioden over helg`(){
        a1 {
            nyttVedtak(førsteFraværsdag = 22.januar, arbeidsgiverperiode = listOf(4.januar til 19.januar), fom = 4.januar, tom = 31.januar)
            assertIngenInfoSomInneholder("Mangler refusjonsopplysninger på orgnummer")
        }
    }

    @Test
    fun `første fraværsdag oppgitt til dagen etter arbeidsgiverperioden over helg med en dags gap`(){
        a1 {
            håndterSykmelding(Sykmeldingsperiode(4.januar, 31.januar))
            håndterSøknad(Sykdom(4.januar, 31.januar, 100.prosent))
            håndterInntektsmelding(listOf(4.januar til 19.januar), førsteFraværsdag = 23.januar)
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING)
            assertVarsel(RV_RE_1)
        }
    }

    @Test
    fun `første fraværsdag oppgitt med en dags gap til arbeidsgiverperioden`(){
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 18.januar)
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING)
            assertVarsel(RV_RE_1)
        }
    }

    @Test
    fun `lager nytt innslag i vilkårsgrunnlaghistorikken med oppdaterte refusjonsopplysninger ved ny inntektsmelding`() {
        a1 {
            val arbeidsgiverperiode = listOf(1.januar til 16.januar)
            nyttVedtak(1.januar, 31.januar, arbeidsgiverperiode = arbeidsgiverperiode, førsteFraværsdag = 1.januar, refusjon = Inntektsmelding.Refusjon(INNTEKT, opphørsdato = null))
            inspektør.refusjonsopplysningerFraVilkårsgrunnlag().assertRefusjonsbeløp(1.januar til 31.januar, INNTEKT)
            assertEquals(1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            håndterInntektsmelding(arbeidsgiverperioder = arbeidsgiverperiode, førsteFraværsdag = 22.januar, refusjon = Inntektsmelding.Refusjon(beløp = INGEN, opphørsdato = null))
            assertEquals(2, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            inspektør.refusjonsopplysningerFraVilkårsgrunnlag().let { refusjonsopplysninger ->
                refusjonsopplysninger.assertRefusjonsbeløp(1.januar til 21.januar, INNTEKT)
                refusjonsopplysninger.assertRefusjonsbeløp(22.januar til 31.januar, INGEN)
            }
        }
    }

    @Test
    fun `Duplikat innhold i ny inntektsmelding`() {
        a1 {
            val arbeidsgiverperiode = listOf(1.januar til 16.januar)
            nyttVedtak(1.januar, 31.januar, arbeidsgiverperiode = arbeidsgiverperiode, førsteFraværsdag = 1.januar, refusjon = Inntektsmelding.Refusjon(INNTEKT, opphørsdato = null))
            inspektør.refusjonsopplysningerFraVilkårsgrunnlag().assertRefusjonsbeløp(1.januar til 31.januar, INNTEKT)
            assertEquals(1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            håndterInntektsmelding(arbeidsgiverperioder = arbeidsgiverperiode, førsteFraværsdag = 1.januar, refusjon = Inntektsmelding.Refusjon(beløp = INNTEKT, opphørsdato = null))
            assertEquals(1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            inspektør.refusjonsopplysningerFraVilkårsgrunnlag().assertRefusjonsbeløp(1.januar til 31.januar, INNTEKT)
        }
    }
    @Test
    fun `Duplikat inntektsmelding`() {
        a1 {
            val arbeidsgiverperiode = listOf(1.januar til 16.januar)
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            val inntektsmeldingId = håndterInntektsmelding(arbeidsgiverperiode)
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt()
            inspektør.refusjonsopplysningerFraVilkårsgrunnlag().assertRefusjonsbeløp(1.januar til 31.januar, INNTEKT)
            assertEquals(1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)

            håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar))
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
            håndterInntektsmelding(
                id = inntektsmeldingId,
                arbeidsgiverperioder = arbeidsgiverperiode,
            )
            assertEquals(1, inspektør.vilkårsgrunnlagHistorikkInnslag().size)
            inspektør.refusjonsopplysningerFraVilkårsgrunnlag().assertRefusjonsbeløp(1.januar til 31.januar, INNTEKT)
        }
    }

    @Test
    fun `bruker først refusjonsopplysning også i gråsonen`(){
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 20.januar)
            håndterVilkårsgrunnlag(1.vedtaksperiode)
            håndterYtelser(1.vedtaksperiode)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING)
            assertVarsel(RV_RE_1)
        }
    }

    @Test
    fun `Automatisk revurdering av AUU ved inntektsmelding som mangler refusjonsopplysninger`() {
        a1 {
            (12.juli til 17.juli i 2022).avsluttUtenUtbetaling()
            (17.august til 17.august i 2022).avsluttUtenUtbetaling()
            (22.august til 22.august i 2022).avsluttUtenUtbetaling()
            (23.august til 23.august i 2022).avsluttUtenUtbetaling()
            val treffesAvInntektsmelding = (12.september til 23.september i 2022).avsluttUtenUtbetaling()
            (24.oktober til 28.oktober i 2022).avsluttUtenUtbetaling()

            val arbeidsgiverperioder = listOf(
                (17.august til 17.august i 2022),
                (22.august til 26.august i 2022),
                (7.september til 7.september i 2022),
                (12.september til 20.september i 2022)
            )
            håndterInntektsmelding(arbeidsgiverperioder, førsteFraværsdag = 22.september i 2022)
            håndterVilkårsgrunnlag(treffesAvInntektsmelding)
            håndterYtelser(treffesAvInntektsmelding)
            assertSisteTilstand(treffesAvInntektsmelding, AVVENTER_SIMULERING)
            assertVarsel(RV_RE_1)
        }
    }

    @Test
    fun `Inntektsmelding uten refusjonsopplysninger tolkes som ingen refusjon`() {
        a1 {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar))
            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
            assertEquals(0, inspektør.arbeidsgiver.inspektør.refusjonshistorikk.inspektør.antall)
            assertEquals(emptyList<Refusjonsopplysning>(), inspektør.refusjonsopplysningerFraRefusjonshistorikk(1.januar).inspektør.refusjonsopplysninger)
            val inntektsmeldingId = håndterInntektsmelding(listOf(1.januar til 16.januar), refusjon = Inntektsmelding.Refusjon(null, null, emptyList()), beregnetInntekt = INNTEKT)
            assertEquals(1, inspektør.arbeidsgiver.inspektør.refusjonshistorikk.inspektør.antall)
            assertEquals(listOf(Refusjonsopplysning(inntektsmeldingId, 1.januar, null, INGEN)), inspektør.refusjonsopplysningerFraRefusjonshistorikk(1.januar).inspektør.refusjonsopplysninger)
        }
    }

    private fun Periode.avsluttUtenUtbetaling(): UUID {
        håndterSykmelding(Sykmeldingsperiode(start, endInclusive))
        håndterSøknad(Sykdom(start, endInclusive, 100.prosent))
        return observatør.sisteVedtaksperiodeId(a1)
    }

    private fun Refusjonsopplysninger.assertRefusjonsbeløp(periode: Periode, beløp: Inntekt) {
        periode.forEach { dag ->
            assertEquals(beløp, refusjonsbeløpOrNull(dag))
        }
    }
}