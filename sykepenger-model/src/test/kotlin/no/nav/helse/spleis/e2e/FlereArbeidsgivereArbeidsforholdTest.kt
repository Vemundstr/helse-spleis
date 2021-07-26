package no.nav.helse.spleis.e2e

import no.nav.helse.Toggles
import no.nav.helse.hendelser.*
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.TilstandType
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.serde.reflection.castAsList
import no.nav.helse.serde.reflection.castAsMap
import no.nav.helse.testhelpers.april
import no.nav.helse.testhelpers.februar
import no.nav.helse.testhelpers.januar
import no.nav.helse.testhelpers.mars
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class FlereArbeidsgivereArbeidsforholdTest : AbstractEndToEndTest() {
    private companion object {
        private const val a1 = "arbeidsgiver 1"
        private const val a2 = "arbeidsgiver 2"
    }

    @Test
    fun `Førstegangsbehandling med ekstra arbeidsforhold som ikke er aktivt - skal ikke få warning hvis det er flere arbeidsforhold`() {
        Toggles.FlereArbeidsgivereUlikFom.enable {
            håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent), orgnummer = a1)
            håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars, 31.mars, 100.prosent), orgnummer = a1)
            håndterInntektsmelding(
                listOf(1.mars til 16.mars),
                førsteFraværsdag = 1.mars,
                orgnummer = a1,
                refusjon = Refusjon(null, 10000.månedlig, emptyList())
            )
            val inntekter = listOf(
                grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode(a1)), 10000.månedlig.repeat(3)),
                grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode(a1)), 20000.månedlig.repeat(3))
            )
            val arbeidsforhold = listOf(
                Arbeidsforhold(orgnummer = a1, fom = LocalDate.EPOCH, tom = null),
                Arbeidsforhold(orgnummer = a2, fom = LocalDate.EPOCH, tom = 1.februar)
            )
            håndterUtbetalingsgrunnlag(1.vedtaksperiode(a1), inntekter = inntekter, orgnummer = a1, arbeidsforhold = arbeidsforhold)

            assertNoWarnings(inspektør(a1))
        }
    }

    @Test
    fun `Filtrerer ut irrelevante arbeidsforhold per arbeidsgiver`() {
        Toggles.FlereArbeidsgivereUlikFom.enable {
            håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent), orgnummer = a1)
            håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars, 31.mars, 100.prosent), orgnummer = a1)
            håndterInntektsmelding(
                listOf(1.mars til 16.mars),
                førsteFraværsdag = 1.mars,
                orgnummer = a1,
                refusjon = Refusjon(null, 10000.månedlig, emptyList())
            )
            val inntekter = listOf(
                grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode(a1)), 10000.månedlig.repeat(3)),
                grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode(a1)), 20000.månedlig.repeat(3))
            )
            val arbeidsforhold = listOf(
                Arbeidsforhold(orgnummer = a1, fom = LocalDate.EPOCH, tom = null),
                Arbeidsforhold(orgnummer = a2, fom = LocalDate.EPOCH, tom = null)
            )
            håndterUtbetalingsgrunnlag(1.vedtaksperiode(a1), inntekter = inntekter, orgnummer = a1, arbeidsforhold = arbeidsforhold)

            assertEquals(1, tellArbeidsforholdhistorikkinnslag(a1).size)
            assertEquals(1, tellArbeidsforholdhistorikkinnslag(a2).size)
        }
    }

    @Test
    fun `Infotrygdforlengelse av arbeidsgiver som ikke finnes i aareg, kan utbetales uten warning`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars, 31.mars, 100.prosent), orgnummer = a1)
        val inntekter = listOf(
            grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode(a1)), 10000.månedlig.repeat(3))
        )
        val arbeidsforhold = emptyList<Arbeidsforhold>()

        val utbetalinger = arrayOf(ArbeidsgiverUtbetalingsperiode(a1, 1.februar, 28.februar, 100.prosent, 10000.månedlig))
        val inntektshistorikk = listOf(Inntektsopplysning(a1, 1.februar, 10000.månedlig, true))
        håndterUtbetalingshistorikk(1.vedtaksperiode(a1), utbetalinger = utbetalinger, inntektshistorikk, orgnummer = a1)
        håndterUtbetalingsgrunnlag(1.vedtaksperiode(a1), a1, inntekter, arbeidsforhold)
        håndterYtelser(1.vedtaksperiode(a1), orgnummer = a1)
        håndterSimulering(1.vedtaksperiode(a1), orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode(a1), orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode(a1), orgnummer = a1)

        assertTilstand(a1, TilstandType.AVSLUTTET)
        assertNoWarnings(inspektør(a1))
    }

    @Test
    fun `Vanlige forlengelser av arbeidsgiver som ikke finnes i aareg, kan utbetales uten warning`() {
        Toggles.FlereArbeidsgivereUlikFom.enable {
            håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent), orgnummer = a1)
            håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars, 31.mars, 100.prosent), orgnummer = a1)
            håndterInntektsmelding(
                listOf(1.mars til 16.mars),
                førsteFraværsdag = 1.mars,
                orgnummer = a1,
                refusjon = Refusjon(null, 10000.månedlig, emptyList())
            )
            val inntekter1 = listOf(
                grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode(a1)), 10000.månedlig.repeat(3))
            )
            val sammenligningsgrunnlag1 = listOf(
                sammenligningsgrunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode(a1)), 10000.månedlig.repeat(12))
            )
            val arbeidsforhold1 = emptyList<Arbeidsforhold>()
            håndterUtbetalingsgrunnlag(1.vedtaksperiode(a1), a1, inntekter1, arbeidsforhold1)
            håndterYtelser(1.vedtaksperiode(a1), orgnummer = a1)
            håndterVilkårsgrunnlag(1.vedtaksperiode(a1), orgnummer = a1, inntektsvurdering = Inntektsvurdering(sammenligningsgrunnlag1))
            håndterYtelser(1.vedtaksperiode(a1), orgnummer = a1)
            håndterSimulering(1.vedtaksperiode(a1), orgnummer = a1)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode(a1), orgnummer = a1)
            håndterUtbetalt(1.vedtaksperiode(a1), orgnummer = a1)

            håndterSykmelding(Sykmeldingsperiode(1.april, 30.april, 100.prosent), orgnummer = a1)
            håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.april, 30.april, 100.prosent), orgnummer = a1)
            val inntekter2 = listOf(
                grunnlag(a1, finnSkjæringstidspunkt(a1, 2.vedtaksperiode(a1)), 10000.månedlig.repeat(3))
            )
            val arbeidsforhold2 = emptyList<Arbeidsforhold>()
            håndterUtbetalingsgrunnlag(2.vedtaksperiode(a1), a1, inntekter2, arbeidsforhold2)
            håndterYtelser(2.vedtaksperiode(a1), orgnummer = a1)
            håndterSimulering(2.vedtaksperiode(a1), orgnummer = a1)
            håndterUtbetalingsgodkjenning(2.vedtaksperiode(a1), orgnummer = a1)
            håndterUtbetalt(2.vedtaksperiode(a1), orgnummer = a1)

            assertTilstand(a1, TilstandType.AVSLUTTET, 2)

            val sisteGodkjenningsbehov = inspektør(a1).sisteBehov(Aktivitetslogg.Aktivitet.Behov.Behovtype.Godkjenning).detaljer()
            assertEquals(0, sisteGodkjenningsbehov["warnings"].castAsMap<String, Any>()["aktiviteter"].castAsList<Any>().size)
        }
    }

    @Test
    fun `Syk for a1, slutter i a1, syk for a2, a1 finnes ikke i Aa-reg lenger - ingen warning for manglende arbeidsforhold`() {
        /*
        * Siden vi ikke vet om arbeidsforhold for tidligere utbetalte perioder må vi passe på at ikke lar de periodene føre til advarsel på nye helt uavhengie vedtaksperioder
        * Sjekker kun arbeidsforhold for gjelende skjæringstidspunkt, derfor vil ikke mangel av arbeidsforhold for a1 skape problemer
        * */
        Toggles.FlereArbeidsgivereUlikFom.enable {
            håndterSykmelding(Sykmeldingsperiode(1.mars(2017), 31.mars(2017), 50.prosent), orgnummer = a1)
            håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars(2017), 31.mars(2017), 50.prosent), orgnummer = a1)
            håndterInntektsmelding(
                listOf(1.mars(2017) til 16.mars(2017)),
                førsteFraværsdag = 1.mars(2017),
                orgnummer = a1,
                refusjon = Refusjon(null, 30000.månedlig, emptyList())
            )
            val inntekterA1 = listOf(
                grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode(a1)), 35000.månedlig.repeat(3))
            )
            val arbeidsforholdA1 = listOf(
                Arbeidsforhold(orgnummer = a1, fom = LocalDate.EPOCH, tom = null)
            )
            håndterUtbetalingsgrunnlag(1.vedtaksperiode(a1), inntekter = inntekterA1, orgnummer = a1, arbeidsforhold = arbeidsforholdA1)
            håndterYtelser(1.vedtaksperiode(a1), orgnummer = a1)
            håndterVilkårsgrunnlag(
                1.vedtaksperiode(a1), inntektsvurdering = Inntektsvurdering(
                    listOf(
                        sammenligningsgrunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode(a1)), 35000.månedlig.repeat(12))
                    )
                ), orgnummer = a1
            )
            håndterYtelser(1.vedtaksperiode(a1), orgnummer = a1)
            håndterSimulering(1.vedtaksperiode(a1), orgnummer = a1)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode(a1), orgnummer = a1)
            håndterUtbetalt(1.vedtaksperiode(a1), orgnummer = a1)

            håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 50.prosent), orgnummer = a2)
            håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars, 31.mars, 50.prosent), orgnummer = a2)
            håndterInntektsmelding(
                listOf(1.mars til 16.mars),
                førsteFraværsdag = 1.mars,
                orgnummer = a2,
                refusjon = Refusjon(null, 30000.månedlig, emptyList())
            )
            val inntekterA2 = listOf(
                grunnlag(a2, finnSkjæringstidspunkt(a2, 1.vedtaksperiode(a2)), 35000.månedlig.repeat(3))
            )

            val arbeidsforholdA2 = listOf(
                Arbeidsforhold(orgnummer = a2, fom = LocalDate.EPOCH, tom = null)
            )

            håndterUtbetalingsgrunnlag(1.vedtaksperiode(a2), inntekter = inntekterA2, orgnummer = a2, arbeidsforhold = arbeidsforholdA2)
            håndterYtelser(1.vedtaksperiode(a2), orgnummer = a2)
            håndterVilkårsgrunnlag(
                1.vedtaksperiode(a2), inntektsvurdering = Inntektsvurdering(
                    listOf(
                        sammenligningsgrunnlag(a2, finnSkjæringstidspunkt(a2, 1.vedtaksperiode(a2)), 35000.månedlig.repeat(12))
                    )
                ), orgnummer = a2
            )
            håndterYtelser(1.vedtaksperiode(a2), orgnummer = a2)
            håndterSimulering(1.vedtaksperiode(a2), orgnummer = a2)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode(a2), orgnummer = a2)
            håndterUtbetalt(1.vedtaksperiode(a2), orgnummer = a2)

            val a2Linje = inspektør(a2).utbetalinger.last().arbeidsgiverOppdrag().last()
            assertEquals(17.mars, a2Linje.fom)
            assertEquals(31.mars, a2Linje.tom)
            assertEquals(692, a2Linje.beløp)

            assertNoWarnings(inspektør(a2))
        }
    }

    @Test
    fun `Warning om manglende arbeidsforhold dukker ikke opp når FlereArbeidsgivereUlikFom-toggle er false`() {
        Toggles.FlereArbeidsgivereUlikFom.disable {
            håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent), orgnummer = a1)
            håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars, 31.mars, 100.prosent), orgnummer = a1)
            håndterInntektsmelding(
                listOf(1.mars til 16.mars),
                førsteFraværsdag = 1.mars,
                orgnummer = a1,
                refusjon = Refusjon(null, 10000.månedlig, emptyList())
            )
            val inntekter1 = listOf(
                grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode(a1)), 10000.månedlig.repeat(3))
            )
            val sammenligningsgrunnlag1 = listOf(
                sammenligningsgrunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode(a1)), 10000.månedlig.repeat(12))
            )
            val arbeidsforhold1 = emptyList<Arbeidsforhold>()
            håndterUtbetalingsgrunnlag(1.vedtaksperiode(a1), a1, inntekter1, arbeidsforhold1)
            håndterYtelser(1.vedtaksperiode(a1), orgnummer = a1)
            håndterVilkårsgrunnlag(1.vedtaksperiode(a1), orgnummer = a1, inntektsvurdering = Inntektsvurdering(sammenligningsgrunnlag1))
            håndterYtelser(1.vedtaksperiode(a1), orgnummer = a1)
            håndterSimulering(1.vedtaksperiode(a1), orgnummer = a1)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode(a1), orgnummer = a1)
            håndterUtbetalt(1.vedtaksperiode(a1), orgnummer = a1)

            assertNoWarnings(inspektør(a1))
        }
    }

    @Test
    fun `lagrer kun arbeidsforhold som gjelder under skjæringstidspunkt`() = Toggles.FlereArbeidsgivereUlikFom.enable {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent), orgnummer = a1)
        håndterSøknad(Søknad.Søknadsperiode.Sykdom(1.mars, 31.mars, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(
            listOf(1.mars til 16.mars),
            førsteFraværsdag = 1.mars,
            orgnummer = a1,
            refusjon = Refusjon(null, 10000.månedlig, emptyList())
        )
        val inntekter1 = listOf(grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode(a1)), 10000.månedlig.repeat(3)))
        val arbeidsforhold1 = listOf(
            Arbeidsforhold(a1, LocalDate.EPOCH, 1.januar),
            Arbeidsforhold(a1, 1.januar, null), // Skal gjelde
            Arbeidsforhold(a1, 28.februar, 1.mars), // Skal gjelde
            Arbeidsforhold(a1, 1.mars, 31.mars), // Skal gjelde
            Arbeidsforhold(a1, 1.februar, 28.februar),
            Arbeidsforhold(a1, 2.mars, 31.mars)
        )
        håndterUtbetalingsgrunnlag(1.vedtaksperiode(a1), a1, inntekter1, arbeidsforhold1)

        assertEquals(3, tellArbeidsforholdINyesteHistorikkInnslag(a1))
    }
}
