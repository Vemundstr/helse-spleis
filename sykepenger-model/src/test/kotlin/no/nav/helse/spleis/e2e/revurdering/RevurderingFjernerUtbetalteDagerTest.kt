package no.nav.helse.spleis.e2e.revurdering

import no.nav.helse.april
import no.nav.helse.hendelser.Inntektsvurdering
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.personLogg
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.sisteBehov
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.assertVarsler
import no.nav.helse.spleis.e2e.forlengVedtak
import no.nav.helse.spleis.e2e.håndterAnnullerUtbetaling
import no.nav.helse.spleis.e2e.håndterInntektsmeldingMedValidering
import no.nav.helse.spleis.e2e.håndterOverstyrTidslinje
import no.nav.helse.spleis.e2e.håndterSimulering
import no.nav.helse.spleis.e2e.håndterSykmelding
import no.nav.helse.spleis.e2e.håndterSøknadMedValidering
import no.nav.helse.spleis.e2e.håndterUtbetalingsgodkjenning
import no.nav.helse.spleis.e2e.håndterUtbetalt
import no.nav.helse.spleis.e2e.håndterVilkårsgrunnlag
import no.nav.helse.spleis.e2e.håndterYtelser
import no.nav.helse.spleis.e2e.manuellFeriedag
import no.nav.helse.testhelpers.inntektperioderForSammenligningsgrunnlag
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Suppress("UNCHECKED_CAST")
internal class RevurderingFjernerUtbetalteDagerTest : AbstractEndToEndTest() {

    @Test
    fun `overstyre inn ferie med forgående periode med avsluttende ferie`() {
        /* Hvis forgående periode avsluttes med ferie og vi revurderer starten av etterfølgende periode med ferie blir det feil i utgående behov.
           Da får vi ikke med oss at feriedagene skal trekkes tilbake */
        håndterSykmelding(Sykmeldingsperiode(1.april(2021), 23.april(2021), 100.prosent))
        håndterSøknadMedValidering(
            1.vedtaksperiode,
            Sykdom(1.april(2021), 23.april(2021), 100.prosent),
            Ferie(23.april(2021), 23.april(2021))
        )
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.april(2021), 16.april(2021))), førsteFraværsdag = 1.april(2021))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.april(2020) til 1.mars(2021) inntekter {
                    ORGNUMMER inntekt INNTEKT
                }
            }
        ))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()
        forlengVedtak(24.april(2021), 7.mai(2021))

        håndterOverstyrTidslinje((26.april(2021) til 27.april(2021)).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        val behov = person.personLogg.sisteBehov(Aktivitetslogg.Aktivitet.Behov.Behovtype.Utbetaling)
        assertEquals(2, (behov.detaljer()["linjer"] as List<*>).size)

        assertEquals("2021-04-24", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["fom"])
        assertEquals("2021-05-07", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["tom"])
        assertEquals("2021-04-24", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["datoStatusFom"])
        assertEquals("OPPH", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["statuskode"])
        assertEquals("2021-04-28", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[1]["fom"])
        assertEquals("2021-05-07", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[1]["tom"])
        assertVarsler()
    }

    @Test
    fun `overstyre inn ferie med forgående periode uten avsluttende ferie`() {
        håndterSykmelding(Sykmeldingsperiode(1.april(2021), 23.april(2021), 100.prosent))
        håndterSøknadMedValidering(
            1.vedtaksperiode,
            Sykdom(1.april(2021), 23.april(2021), 100.prosent)
        )
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.april(2021), 16.april(2021))), førsteFraværsdag = 1.april(2021))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.april(2020) til 1.mars(2021) inntekter {
                    ORGNUMMER inntekt INNTEKT
                }
            }
        ))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()
        forlengVedtak(24.april(2021), 7.mai(2021))

        håndterOverstyrTidslinje((26.april(2021) til 27.april(2021)).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        val behov = person.personLogg.sisteBehov(Aktivitetslogg.Aktivitet.Behov.Behovtype.Utbetaling)
        assertEquals(2, (behov.detaljer()["linjer"] as List<*>).size)

        assertEquals("2021-04-17", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["fom"])
        assertEquals("2021-04-23", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["tom"])
        assertEquals("2021-04-28", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[1]["fom"])
        assertEquals("2021-05-07", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[1]["tom"])
    }

    @Test
    fun `overstyre inn ferie med forgående periode med annullering`() {
        håndterSykmelding(Sykmeldingsperiode(1.april(2021), 23.april(2021), 100.prosent))
        håndterSøknadMedValidering(
            1.vedtaksperiode,
            Sykdom(1.april(2021), 23.april(2021), 100.prosent),
            Ferie(23.april(2021), 23.april(2021))
        )
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.april(2021), 16.april(2021))), førsteFraværsdag = 1.april(2021))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.april(2020) til 1.mars(2021) inntekter {
                    ORGNUMMER inntekt INNTEKT
                }
            }
        ))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()
        forlengVedtak(24.april(2021), 7.mai(2021))

        håndterOverstyrTidslinje((26.april(2021) til 27.april(2021)).map { manuellFeriedag(it) })
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt()

        håndterAnnullerUtbetaling()
        val oppdrag = inspektør.arbeidsgiverOppdrag.last()
        assertEquals(0, oppdrag.linjerUtenOpphør().size)

        val behov = person.personLogg.sisteBehov(Aktivitetslogg.Aktivitet.Behov.Behovtype.Utbetaling)
        assertEquals(1, (behov.detaljer()["linjer"] as List<*>).size)

        assertEquals("2021-04-17", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["datoStatusFom"])
        assertEquals("OPPH", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["statuskode"])
    }

    @Test
    fun `revurdering fjerner en hel linje i oppdraget`() {
        håndterSykmelding(
            Sykmeldingsperiode(1.april(2021), 23.april(2021), 60.prosent),
            Sykmeldingsperiode(24.april(2021), 30.april(2021), 40.prosent)
        )
        håndterSøknadMedValidering(
            1.vedtaksperiode,
            Sykdom(1.april(2021), 23.april(2021), 60.prosent),
            Sykdom(24.april(2021), 30.april(2021), 40.prosent)
        )
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.april(2021), 16.april(2021))), førsteFraværsdag = 1.april(2021))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.april(2020) til 1.mars(2021) inntekter {
                    ORGNUMMER inntekt INNTEKT
                }
            }
        ))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        håndterOverstyrTidslinje((24.april(2021) til 30.april(2021)).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)

        val oppdrag = inspektør.arbeidsgiverOppdrag.last()
        assertEquals(1, oppdrag.linjerUtenOpphør().size)

        val behov = person.personLogg.sisteBehov(Aktivitetslogg.Aktivitet.Behov.Behovtype.Utbetaling)
        assertEquals(1, (behov.detaljer()["linjer"] as List<*>).size)

        assertEquals("2021-04-17", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["fom"])
        assertEquals("2021-04-23", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["tom"])
        assertEquals("NY", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["endringskode"])
    }

    @Test
    fun `opphører hele perioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.april(2021), 23.april(2021), 100.prosent))
        håndterSøknadMedValidering(
            1.vedtaksperiode,
            Sykdom(1.april(2021), 23.april(2021), 60.prosent)
        )
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.april(2021), 16.april(2021))), førsteFraværsdag = 1.april(2021))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.april(2020) til 1.mars(2021) inntekter {
                    ORGNUMMER inntekt INNTEKT
                }
            }
        ))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        håndterOverstyrTidslinje((17.april(2021) til 23.april(2021)).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        val oppdrag = inspektør.arbeidsgiverOppdrag.last()
        assertEquals(0, oppdrag.linjerUtenOpphør().size)

        val behov = person.personLogg.sisteBehov(Aktivitetslogg.Aktivitet.Behov.Behovtype.Utbetaling)
        assertEquals(1, (behov.detaljer()["linjer"] as List<*>).size)

        assertEquals("2021-04-17", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["datoStatusFom"])
        assertEquals("OPPH", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["statuskode"])
    }

    @Test
    fun `forlenger en opphørt periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.april(2021), 23.april(2021), 100.prosent))
        håndterSøknadMedValidering(
            1.vedtaksperiode,
            Sykdom(1.april(2021), 23.april(2021), 60.prosent)
        )
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.april(2021), 16.april(2021))), førsteFraværsdag = 1.april(2021))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.april(2020) til 1.mars(2021) inntekter {
                    ORGNUMMER inntekt INNTEKT
                }
            }
        ))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        håndterOverstyrTidslinje((17.april(2021) til 23.april(2021)).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        forlengVedtak(24.april(2021), 15.mai(2021))

        val oppdrag = inspektør.arbeidsgiverOppdrag.last()
        assertEquals(1, oppdrag.linjerUtenOpphør().size)

        // Ny fagsystemId fordi perioden vi forlenger har blitt opphørt
        assertEquals(inspektør.arbeidsgiverOppdrag.first().fagsystemId(), inspektør.arbeidsgiverOppdrag.last().fagsystemId())
        assertEquals(15, inspektør.arbeidsgiverOppdrag.last().stønadsdager())
        assertEquals(24.april(2021) til 14.mai(2021), Oppdrag.periode(inspektør.arbeidsgiverOppdrag.last()))
    }

    @Test
    fun `pågående forlengelse fanget i en revurdering med opphør`() {
        håndterSykmelding(Sykmeldingsperiode(1.april(2021), 23.april(2021), 100.prosent))
        håndterSøknadMedValidering(
            1.vedtaksperiode,
            Sykdom(1.april(2021), 23.april(2021), 60.prosent)
        )
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.april(2021), 16.april(2021))), førsteFraværsdag = 1.april(2021))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.april(2020) til 1.mars(2021) inntekter {
                    ORGNUMMER inntekt INNTEKT
                }
            }
        ))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        håndterSykmelding(Sykmeldingsperiode(24.april(2021), 15.mai(2021), 100.prosent))
        håndterSøknadMedValidering(
            2.vedtaksperiode,
            Sykdom(24.april(2021), 15.mai(2021), 100.prosent)
        )

        håndterOverstyrTidslinje((17.april(2021) til 23.april(2021)).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt()

        assertEquals(inspektør.arbeidsgiverOppdrag.first().fagsystemId(), inspektør.arbeidsgiverOppdrag.last().fagsystemId())
        assertEquals(15, inspektør.arbeidsgiverOppdrag.last().stønadsdager())
        assertEquals(24.april(2021) til 14.mai(2021), Oppdrag.periode(inspektør.arbeidsgiverOppdrag.last()))
    }

    @Test
    fun `påfølgende periode har laget en utbetaling og forgående periode revurderes bort`() {
        håndterSykmelding(Sykmeldingsperiode(1.april(2021), 23.april(2021), 100.prosent))
        håndterSøknadMedValidering(
            1.vedtaksperiode,
            Sykdom(1.april(2021), 23.april(2021), 60.prosent)
        )
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.april(2021), 16.april(2021))), førsteFraværsdag = 1.april(2021))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.april(2020) til 1.mars(2021) inntekter {
                    ORGNUMMER inntekt INNTEKT
                }
            }
        ))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        håndterSykmelding(Sykmeldingsperiode(24.april(2021), 15.mai(2021), 100.prosent))
        håndterSøknadMedValidering(
            2.vedtaksperiode,
            Sykdom(24.april(2021), 15.mai(2021), 100.prosent)
        )
        håndterYtelser(2.vedtaksperiode) // 2. periode har beregnet utbetalingen sin som en forlengelse

        håndterOverstyrTidslinje((17.april(2021) til 23.april(2021)).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt()

        assertEquals(inspektør.arbeidsgiverOppdrag.first().fagsystemId(), inspektør.arbeidsgiverOppdrag.last().fagsystemId())
        assertEquals(15, inspektør.arbeidsgiverOppdrag.last().stønadsdager())
        assertEquals(24.april(2021) til 14.mai(2021), Oppdrag.periode(inspektør.arbeidsgiverOppdrag.last()))
    }

    @Test
    fun `overstyrer inn ferie i starten av perioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.april(2021), 23.april(2021), 100.prosent))
        håndterSøknadMedValidering(
            1.vedtaksperiode,
            Sykdom(1.april(2021), 23.april(2021), 60.prosent)
        )
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.april(2021), 16.april(2021))), førsteFraværsdag = 1.april(2021))
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.april(2020) til 1.mars(2021) inntekter {
                    ORGNUMMER inntekt INNTEKT
                }
            }
        ))
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt()

        håndterOverstyrTidslinje((17.april(2021) til 20.april(2021)).map { manuellFeriedag(it) })
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)

        val behov = person.personLogg.sisteBehov(Aktivitetslogg.Aktivitet.Behov.Behovtype.Utbetaling)
        assertEquals(2, (behov.detaljer()["linjer"] as List<*>).size)

        assertEquals("2021-04-17", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["datoStatusFom"])
        assertEquals("OPPH", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[0]["statuskode"])
        assertEquals("2021-04-21", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[1]["fom"])
        assertEquals("2021-04-23", (behov.detaljer()["linjer"] as List<Map<String, Any>>)[1]["tom"])
        assertVarsler()
    }
}
