package no.nav.helse.person

import no.nav.helse.etterspurteBehov
import no.nav.helse.hendelser.*
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.spleis.e2e.TestArbeidsgiverInspektør
import no.nav.helse.testhelpers.desember
import no.nav.helse.testhelpers.inntektperioder
import no.nav.helse.testhelpers.januar
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class PåminnelserOgTimeoutTest {

    private companion object {
        private const val UNG_PERSON_FNR_2018 = "12020052345"
        private const val orgnummer = "1234"
        private val nå = LocalDate.now()
    }

    private lateinit var person: Person
    private lateinit var hendelse: ArbeidstakerHendelse
    private val inspektør get() = TestArbeidsgiverInspektør(person)

    @BeforeEach
    internal fun opprettPerson() {
        person = Person("12345", UNG_PERSON_FNR_2018)
    }

    @Test
    fun `påminnelse i mottatt sykmelding innenfor makstid`() {
        person.håndter(sykmelding(Sykmeldingsperiode(nå.minusDays(30), nå, 100)))
        person.håndter(påminnelse(TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP))
        assertTilstand(TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP)
        assertEquals(1, hendelse.behov().size)
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(0), Behovtype.Sykepengehistorikk))
    }

    @Test
    fun `påminnelse i mottatt søknad`() {
        person.håndter(sykmelding())
        person.håndter(søknad())
        assertTilstand(TilstandType.AVVENTER_GAP)
        assertEquals(6, hendelse.behov().size)
        person.håndter(påminnelse(TilstandType.AVVENTER_GAP))
        assertTilstand(TilstandType.AVVENTER_GAP)
        assertEquals(6, hendelse.behov().size)
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(0), Behovtype.Foreldrepenger))
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(0), Behovtype.Pleiepenger))
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(0), Behovtype.Omsorgspenger))
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(0), Behovtype.Opplæringspenger))
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(0), Behovtype.Institusjonsopphold))
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(0), Behovtype.Sykepengehistorikk))
    }

    @Test
    fun `påminnelse i mottatt søknad innenfor makstid`() {
        person.håndter(sykmelding(Sykmeldingsperiode(nå.minusDays(60), nå.minusDays(31), 100)))
        person.håndter(sykmelding(Sykmeldingsperiode(nå.minusDays(30), nå, 100)))
        person.håndter(søknad(Søknad.Søknadsperiode.Sykdom(nå.minusDays(30), nå, 100)))
        assertTilstand(TilstandType.AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, 1)
        person.håndter(påminnelse(TilstandType.AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, 1))
        assertTilstand(TilstandType.AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, 1)
        assertEquals(1, hendelse.behov().size)
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(1), Behovtype.Sykepengehistorikk))
    }

    @Test
    fun `påminnelse i mottatt inntektsmelding`() {
        person.håndter(sykmelding(Sykmeldingsperiode(nå.minusDays(30), nå, 100)))
        person.håndter(inntektsmelding(Periode(nå.minusDays(30), nå.minusDays(14))))
        person.håndter(påminnelse(TilstandType.AVVENTER_SØKNAD_FERDIG_GAP))
        assertEquals(1, hendelse.behov().size)
        assertTilstand(TilstandType.AVVENTER_SØKNAD_FERDIG_GAP)
    }

    @Test
    fun `påminnelse i vilkårsprøving`() {
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(inntektsmelding())
        assertEquals(5, hendelse.behov().size)
        person.håndter(påminnelse(TilstandType.AVVENTER_VILKÅRSPRØVING_GAP))
        assertTilstand(TilstandType.AVVENTER_VILKÅRSPRØVING_GAP)
        assertEquals(5, hendelse.behov().size)
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(0), Behovtype.Dagpenger))
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(0), Behovtype.Arbeidsavklaringspenger))
        assertTrue(
            hendelse.etterspurteBehov(
                inspektør.vedtaksperiodeId(0),
                Behovtype.InntekterForSammenligningsgrunnlag
            )
        )
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(0), Behovtype.Opptjening))
    }

    @Test
    fun `påminnelse i ytelser`() {
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(inntektsmelding())
        person.håndter(vilkårsgrunnlag())
        assertEquals(6, hendelse.behov().size)
        person.håndter(påminnelse(TilstandType.AVVENTER_HISTORIKK))
        assertTilstand(TilstandType.AVVENTER_HISTORIKK)
        assertEquals(6, hendelse.behov().size)
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(0), Behovtype.Foreldrepenger))
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(0), Behovtype.Pleiepenger))
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(0), Behovtype.Omsorgspenger))
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(0), Behovtype.Opplæringspenger))
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(0), Behovtype.Institusjonsopphold))
        assertTrue(hendelse.etterspurteBehov(inspektør.vedtaksperiodeId(0), Behovtype.Sykepengehistorikk))
    }

    @Test
    fun `påminnelse i avventer simulering`() {
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(inntektsmelding())
        person.håndter(vilkårsgrunnlag())
        person.håndter(ytelser())
        assertTilstand(TilstandType.AVVENTER_SIMULERING)
        assertEquals(1, hendelse.behov().size)
        assertTrue(hendelse.behov().any { it.type == Behovtype.Simulering })
        person.håndter(påminnelse(TilstandType.AVVENTER_SIMULERING))
        assertTilstand(TilstandType.AVVENTER_SIMULERING)
        assertEquals(1, hendelse.behov().size)
    }

    @Test
    fun `påminnelse i til godkjenning`() {
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(inntektsmelding())
        person.håndter(vilkårsgrunnlag())
        person.håndter(ytelser())
        person.håndter(simulering())
        assertTilstand(TilstandType.AVVENTER_GODKJENNING)
        assertEquals(1, hendelse.behov().size)
        assertTrue(hendelse.behov().any { it.type == Behovtype.Godkjenning })
        person.håndter(påminnelse(TilstandType.AVVENTER_GODKJENNING))
        assertTilstand(TilstandType.AVVENTER_GODKJENNING)
        assertEquals(1, hendelse.behov().size)
    }

    @Test
    fun `påminnelse i til utbetaling`() {
        person.håndter(sykmelding())
        person.håndter(søknad())
        person.håndter(inntektsmelding())
        person.håndter(vilkårsgrunnlag())
        person.håndter(ytelser())
        person.håndter(simulering())
        person.håndter(utbetalingsgodkjenning())
        assertEquals(1, hendelse.behov().size)
        person.håndter(påminnelse(TilstandType.TIL_UTBETALING))
        assertTilstand(TilstandType.TIL_UTBETALING)
        assertEquals(0, hendelse.behov().size)
    }

    @Test
    fun `ignorerer påminnelser på tidligere tilstander`() {
        person.håndter(sykmelding())
        person.håndter(påminnelse(TilstandType.TIL_INFOTRYGD))
        assertTilstand(TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP)

        person.håndter(søknad())
        person.håndter(påminnelse(TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP))
        assertTilstand(TilstandType.AVVENTER_GAP)

        person.håndter(inntektsmelding())
        person.håndter(påminnelse(TilstandType.AVVENTER_GAP))
        assertTilstand(TilstandType.AVVENTER_VILKÅRSPRØVING_GAP)

        person.håndter(vilkårsgrunnlag())
        person.håndter(påminnelse(TilstandType.AVVENTER_VILKÅRSPRØVING_GAP))
        assertTilstand(TilstandType.AVVENTER_HISTORIKK)

        person.håndter(ytelser())
        person.håndter(påminnelse(TilstandType.AVVENTER_HISTORIKK))
        assertTilstand(TilstandType.AVVENTER_SIMULERING)

        person.håndter(simulering())
        person.håndter(påminnelse(TilstandType.AVVENTER_SIMULERING))
        assertTilstand(TilstandType.AVVENTER_GODKJENNING)

        person.håndter(utbetalingsgodkjenning())
        person.håndter(påminnelse(TilstandType.AVVENTER_GODKJENNING))
        assertTilstand(TilstandType.TIL_UTBETALING)

        person.håndter(påminnelse(TilstandType.TIL_UTBETALING))
        assertTilstand(TilstandType.TIL_UTBETALING)
    }

    private fun søknad(
        vararg perioder: Søknad.Søknadsperiode = arrayOf(
            Søknad.Søknadsperiode.Sykdom(
                1.januar,
                20.januar,
                100
            )
        )
    ) =
        Søknad(
            meldingsreferanseId = UUID.randomUUID(),
            fnr = UNG_PERSON_FNR_2018,
            aktørId = "12345",
            orgnummer = orgnummer,
            perioder = perioder.toList(),
            harAndreInntektskilder = false,
            sendtTilNAV = 20.januar.atStartOfDay(),
            permittert = false
        ).apply {
            hendelse = this
        }

    private fun sykmelding(
        vararg perioder: Sykmeldingsperiode = arrayOf(
            Sykmeldingsperiode(
                1.januar,
                20.januar,
                100
            )
        )
    ) =
        Sykmelding(
            meldingsreferanseId = UUID.randomUUID(),
            fnr = UNG_PERSON_FNR_2018,
            aktørId = "12345",
            orgnummer = orgnummer,
            sykeperioder = perioder.toList(),
            mottatt = perioder.minOfOrNull { it.fom }?.atStartOfDay() ?: LocalDateTime.now()
        ).apply {
            hendelse = this
        }

    private fun inntektsmelding(
        vararg arbeidsgiverperiode: Periode = arrayOf(Periode(1.januar, 1.januar.plusDays(15))),
        førsteFraværsdag: LocalDate = 1.januar
    ) =
        Inntektsmelding(
            meldingsreferanseId = UUID.randomUUID(),
            refusjon = Inntektsmelding.Refusjon(null, 31000.månedlig, emptyList()),
            orgnummer = orgnummer,
            fødselsnummer = UNG_PERSON_FNR_2018,
            aktørId = "aktørId",
            førsteFraværsdag = førsteFraværsdag,
            beregnetInntekt = 31000.månedlig,
            arbeidsgiverperioder = arbeidsgiverperiode.toList(),
            ferieperioder = emptyList(),
            arbeidsforholdId = null,
            begrunnelseForReduksjonEllerIkkeUtbetalt = null
        ).apply {
            hendelse = this
        }

    private fun vilkårsgrunnlag() =
        Vilkårsgrunnlag(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = inspektør.vedtaksperiodeId(0).toString(),
            aktørId = "aktørId",
            fødselsnummer = UNG_PERSON_FNR_2018,
            orgnummer = orgnummer,
            inntektsvurdering = Inntektsvurdering(inntektperioder {
                1.januar(2018) til 1.desember(2018) inntekter {
                    orgnummer inntekt 31000.månedlig
                }
            }),
            medlemskapsvurdering = Medlemskapsvurdering(Medlemskapsvurdering.Medlemskapstatus.Ja),
            opptjeningvurdering = Opptjeningvurdering(
                listOf(
                    Opptjeningvurdering.Arbeidsforhold(
                        orgnummer,
                        1.januar(2017)
                    )
                )
            ),
            dagpenger = Dagpenger(emptyList()),
            arbeidsavklaringspenger = Arbeidsavklaringspenger(emptyList())
        ).apply {
            hendelse = this
        }

    private fun simulering() =
        Simulering(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = inspektør.vedtaksperiodeId(0).toString(),
            aktørId = "aktørId",
            fødselsnummer = UNG_PERSON_FNR_2018,
            orgnummer = orgnummer,
            simuleringOK = true,
            melding = "",
            simuleringResultat = Simulering.SimuleringResultat(
                totalbeløp = 2000,
                perioder = listOf(
                    Simulering.SimulertPeriode(
                        periode = Periode(17.januar, 20.januar),
                        utbetalinger = listOf(
                            Simulering.SimulertUtbetaling(
                                forfallsdato = 21.januar,
                                utbetalesTil = Simulering.Mottaker(
                                    id = orgnummer,
                                    navn = "Org Orgesen AS"
                                ),
                                feilkonto = false,
                                detaljer = listOf(
                                    Simulering.Detaljer(
                                        periode = Periode(17.januar, 20.januar),
                                        konto = "81549300",
                                        beløp = 2000,
                                        klassekode = Simulering.Klassekode(
                                            kode = "SPREFAG-IOP",
                                            beskrivelse = "Sykepenger, Refusjon arbeidsgiver"
                                        ),
                                        uføregrad = 100,
                                        utbetalingstype = "YTELSE",
                                        tilbakeføring = false,
                                        sats = Simulering.Sats(
                                            sats = 1000,
                                            antall = 2,
                                            type = "DAGLIG"
                                        ),
                                        refunderesOrgnummer = orgnummer
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ).apply {
            hendelse = this
        }

    private fun ytelser(): Ytelser {
        val meldingsreferanseId = UUID.randomUUID()
        return Ytelser(
            meldingsreferanseId = meldingsreferanseId,
            aktørId = "aktørId",
            fødselsnummer = UNG_PERSON_FNR_2018,
            organisasjonsnummer = orgnummer,
            vedtaksperiodeId = inspektør.vedtaksperiodeId(0).toString(),
            utbetalingshistorikk = Utbetalingshistorikk(
                meldingsreferanseId = meldingsreferanseId,
                aktørId = "aktørId",
                fødselsnummer = UNG_PERSON_FNR_2018,
                organisasjonsnummer = orgnummer,
                vedtaksperiodeId = inspektør.vedtaksperiodeId(0).toString(),
                utbetalinger = listOf(
                    Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(
                        17.januar(2017),
                        20.januar(2017),
                        1000,
                        100,
                        orgnummer
                    )
                ),
                inntektshistorikk = listOf(
                    Utbetalingshistorikk.Inntektsopplysning(
                        1.januar(2017),
                        31000.månedlig,
                        orgnummer,
                        true
                    )
                ),
                aktivitetslogg = Aktivitetslogg()
            ),
            foreldrepermisjon = Foreldrepermisjon(
                foreldrepengeytelse = null,
                svangerskapsytelse = null,
                aktivitetslogg = Aktivitetslogg()
            ),
            pleiepenger = Pleiepenger(
                perioder = emptyList(),
                aktivitetslogg = Aktivitetslogg()
            ),
            omsorgspenger = Omsorgspenger(
                perioder = emptyList(),
                aktivitetslogg = Aktivitetslogg()
            ),
            opplæringspenger = Opplæringspenger(
                perioder = emptyList(),
                aktivitetslogg = Aktivitetslogg()
            ),
            institusjonsopphold = Institusjonsopphold(
                perioder = emptyList(),
                aktivitetslogg = Aktivitetslogg()
            ),
            aktivitetslogg = Aktivitetslogg()
        ).apply {
            hendelse = this
        }
    }

    private fun utbetalingsgodkjenning() = Utbetalingsgodkjenning(
        meldingsreferanseId = UUID.randomUUID(),
        aktørId = "aktørId",
        fødselsnummer = UNG_PERSON_FNR_2018,
        organisasjonsnummer = orgnummer,
        vedtaksperiodeId = inspektør.vedtaksperiodeId(0).toString(),
        saksbehandler = "Ola Nordmann",
        utbetalingGodkjent = true,
        godkjenttidspunkt = LocalDateTime.now(),
        automatiskBehandling = false,
        saksbehandlerEpost = "ola@normann.ss"
    ).apply {
        hendelse = this
    }

    private fun påminnelse(tilstandType: TilstandType, indeks: Int = 0) = Påminnelse(
        meldingsreferanseId = UUID.randomUUID(),
        aktørId = "aktørId",
        fødselsnummer = UNG_PERSON_FNR_2018,
        organisasjonsnummer = orgnummer,
        vedtaksperiodeId = inspektør.vedtaksperiodeId(indeks).toString(),
        tilstand = tilstandType,
        antallGangerPåminnet = 1,
        tilstandsendringstidspunkt = LocalDateTime.now(),
        påminnelsestidspunkt = LocalDateTime.now(),
        nestePåminnelsestidspunkt = LocalDateTime.now()
    ).apply {
        hendelse = this
    }

    private fun assertTilstand(expectedTilstand: TilstandType, indeks: Int = 0) {
        assertEquals(
            expectedTilstand,
            inspektør.sisteTilstand(indeks)
        )
    }
}
