package no.nav.helse.sykdomstidslinje

import java.time.LocalDate
import no.nav.helse.desember
import no.nav.helse.dsl.ArbeidsgiverHendelsefabrikk
import no.nav.helse.erRettFør
import no.nav.helse.februar
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.periode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Søknad.Søknadsperiode
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.perioder
import no.nav.helse.somPersonidentifikator
import no.nav.helse.testhelpers.A
import no.nav.helse.testhelpers.F
import no.nav.helse.testhelpers.H
import no.nav.helse.testhelpers.AIG
import no.nav.helse.testhelpers.N
import no.nav.helse.testhelpers.P
import no.nav.helse.testhelpers.S
import no.nav.helse.testhelpers.U
import no.nav.helse.testhelpers.UK
import no.nav.helse.testhelpers.YF
import no.nav.helse.testhelpers.opphold
import no.nav.helse.testhelpers.resetSeed
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SkjæringstidspunktTest {

    @BeforeEach
    fun setup() {
        resetSeed()
    }

    @Test
    fun `skjæringstidspunkt er null for ugyldige situasjoner`() {
        assertNull(1.F.sisteSkjæringstidspunkt())
        assertNull(1.P.sisteSkjæringstidspunkt())
        assertNull(1.opphold.sisteSkjæringstidspunkt())
        assertNull(1.A.sisteSkjæringstidspunkt())
        assertNull(1.AIG.sisteSkjæringstidspunkt())
        assertNull(1.YF.sisteSkjæringstidspunkt())
    }

    @Test
    fun `skjæringstidspunkt er sykedag, arbeidsgiverdag eller syk helgedag`() {
        assertFørsteDagErSkjæringstidspunkt(1.S)
        assertFørsteDagErSkjæringstidspunkt(1.U)
        assertFørsteDagErSkjæringstidspunkt(1.H)
    }

    @Test
    fun `syk-ferie uten sykmelding-helg-syk regnes som gap`() {
        assertDagenErSkjæringstidspunkt(22.januar, 15.S + 4.AIG + 2.opphold + 1.S)
    }

    @Test
    fun `syk-helg-ferie uten sykmelding-syk regnes ikke som gap`() {
        assertDagenErSkjæringstidspunkt(17.januar, 12.S + 2.opphold + 2.AIG + 1.S)
    }

    @Test
    fun `syk-ferie-helg-syk regnes ikke som gap`() {
        assertFørsteDagErSkjæringstidspunkt(15.S + 4.F + 2.opphold + 1.S)
    }

    @Test
    fun `syk-helg-ferie-syk regnes ikke som gap`() {
        assertFørsteDagErSkjæringstidspunkt(12.S + 2.opphold + 2.F + 1.S)
    }

    @Test
    fun `skjæringstidspunkt er første arbeidsgiverdag, sykedag eller syk helgedag i en sammenhengende periode`() {
        assertFørsteDagErSkjæringstidspunkt(2.S)
        assertFørsteDagErSkjæringstidspunkt(2.U)

        resetSeed(4.januar)
        perioder(2.S, 2.opphold, 2.S) { periode1, _, _ ->
            assertFørsteDagErSkjæringstidspunkt(periode1, this)
        }
        perioder(2.S, 2.A, 2.F, 2.S) { _, _, _, periode4 ->
            assertFørsteDagErSkjæringstidspunkt(periode4, this)
        }
        perioder(2.S, 2.A, 2.P, 2.S) { _, _, _, periode4 ->
            assertFørsteDagErSkjæringstidspunkt(periode4, this)
        }
        perioder(2.S, 2.F, 2.S) { periode1, _, _ ->
            assertFørsteDagErSkjæringstidspunkt(periode1, this)
        }
        perioder(2.S, 2.P, 2.S) { periode1, _, _ ->
            assertFørsteDagErSkjæringstidspunkt(periode1, this)
        }
        perioder(2.S, 2.opphold, 1.H) { _, _, sisteSykedager ->
            assertFørsteDagErSkjæringstidspunkt(sisteSykedager, this)
        }
        perioder(2.S, 2.opphold, 1.U) { _, _, sisteSykedager ->
            assertFørsteDagErSkjæringstidspunkt(sisteSykedager, this)
        }
        perioder(2.S, 2.A, 2.S, 2.A) { _, _, sisteSykedager, _ ->
            assertFørsteDagErSkjæringstidspunkt(sisteSykedager, this)
        }
        perioder(2.S, 2.AIG, 2.F, 2.S) { _, _, _, periode4 ->
            assertFørsteDagErSkjæringstidspunkt(periode4, this)
        }
        perioder(2.S, 2.F, 2.AIG, 2.S) { _, _, _, periode4 ->
            assertFørsteDagErSkjæringstidspunkt(periode4, this)
        }
        perioder(2.S, 2.YF, 2.F, 2.S) { _, _, _, periode4 ->
            assertFørsteDagErSkjæringstidspunkt(periode4, this)
        }
        perioder(2.S, 2.F, 2.YF, 2.S) { _, _, _, periode4 ->
            assertFørsteDagErSkjæringstidspunkt(periode4, this)
        }
        perioder(2.S, 2.YF, 2.S) { _, _, periode3 ->
            assertFørsteDagErSkjæringstidspunkt(periode3, this)
        }
    }

    @Test
    fun `tidslinjer som slutter med ignorerte dager`() {
        perioder(2.S, 2.F) { periode1, _ ->
            assertFørsteDagErSkjæringstidspunkt(periode1, this)
        }
        perioder(2.S, 2.P) { periode1, _ ->
            assertFørsteDagErSkjæringstidspunkt(periode1, this)
        }
        perioder(2.S, 2.YF) { periode1, _ ->
            assertFørsteDagErSkjæringstidspunkt(periode1, this)
        }
    }

    @Test
    fun `bare ferie - tidligere sykdom`() {
        perioder(2.S, 1.A, 14.F) { _, _, _ ->
            assertNull(sisteSkjæringstidspunkt())
        }
        perioder(2.S, 1.AIG, 14.F) { _, _, _ ->
            assertNull(sisteSkjæringstidspunkt())
        }
    }

    @Test
    fun `ferie i framtiden`() {
        perioder(2.S, 2.opphold, 2.F) { _, _, _ ->
            assertNull(sisteSkjæringstidspunkt())
        }
    }

    @Test
    fun `sykedager etterfulgt av arbeidsdager`() {
        perioder(2.S, 2.A) { sykedager, _ ->
            assertFørsteDagErSkjæringstidspunkt(sykedager, this)
        }
    }

    @Test
    fun `sykedager etterfulgt av implisittdager`() {
        perioder(2.S, 2.opphold) { sykedager, _ ->
            assertFørsteDagErSkjæringstidspunkt(sykedager, this)
        }
    }

    @Test
    fun `søknad med arbeidgjenopptatt gir ikke feil`() {
        perioder(2.S, 2.opphold) { sykedager, _ ->
            assertFørsteDagErSkjæringstidspunkt(sykedager, this)
        }
    }

    @Test
    fun `sykeperiode starter på skjæringstidspunkt`() {
        val søknad = søknad(Søknadsperiode.Sykdom(4.februar(2020), 21.februar(2020), 100.prosent))
        val skjæringstidspunkt = 4.februar(2020)
        val inntektsmelding = inntektsmelding(
            listOf(
                Periode(20.januar(2020), 20.januar(2020)),
                Periode(4.februar(2020), 18.februar(2020))
            ), førsteFraværsdag = skjæringstidspunkt
        )
        assertSkjæringstidspunkt(skjæringstidspunkt, søknad, inntektsmelding)
    }

    @Test
    fun `skjæringstidspunkt er riktig selv om første fraværsdag er satt for tidlig`() {
        val søknad = søknad(Søknadsperiode.Sykdom(4.februar(2020), 21.februar(2020), 100.prosent))
        val skjæringstidspunkt = 4.februar(2020)
        val inntektsmelding = inntektsmelding(
            listOf(
                Periode(20.januar(2020), 20.januar(2020)),
                Periode(4.februar(2020), 18.februar(2020))
            ), førsteFraværsdag = 20.januar(2020)
        )
        assertSkjæringstidspunkt(skjæringstidspunkt, søknad, inntektsmelding)
    }


    @Test
    fun `skjæringstidspunkt er riktig selv om første fraværsdag er satt for sent`() {
        val søknad = søknad(Søknadsperiode.Sykdom(4.februar(2020), 21.februar(2020), 100.prosent))
        val skjæringstidspunkt = 4.februar(2020)
        val inntektsmelding = inntektsmelding(
            listOf(
                Periode(20.januar(2020), 20.januar(2020)),
                Periode(4.februar(2020), 18.februar(2020))
            ), førsteFraværsdag = 18.februar(2020)
        )
        assertSkjæringstidspunkt(skjæringstidspunkt, søknad, inntektsmelding)
    }

    @Test
    fun `sykeperioden starter etter skjæringstidspunkt`() {
        val søknad = søknad(Søknadsperiode.Sykdom(29.januar(2020), 16.februar(2020), 100.prosent))
        val skjæringstidspunkt = 20.januar(2020)
        val inntektsmelding = inntektsmelding(
            listOf(
                Periode(13.januar(2020), 17.januar(2020)),
                Periode(20.januar(2020), 30.januar(2020))
            ), førsteFraværsdag = skjæringstidspunkt
        )
        assertSkjæringstidspunkt(skjæringstidspunkt, søknad, inntektsmelding)
    }

    @Test
    fun `arbeidsgiverperiode med enkeltdager før skjæringstidspunkt`() {
        val søknad = søknad(Søknadsperiode.Sykdom(12.februar(2020), 19.februar(2020), 100.prosent))
        val skjæringstidspunkt = 3.februar(2020)
        val inntektsmelding = inntektsmelding(
            listOf(
                Periode(14.januar(2020), 14.januar(2020)),
                Periode(28.januar(2020), 28.januar(2020)),
                Periode(3.februar(2020), 16.februar(2020))
            ), førsteFraværsdag = skjæringstidspunkt
        )
        assertSkjæringstidspunkt(skjæringstidspunkt, søknad, inntektsmelding)
    }

    @Test
    fun `tilstøtende arbeidsgivertidslinjer`() {
        val arbeidsgiver1tidslinje = 14.S
        val arbeidsgiver2tidslinje = 14.S
        assertTrue(arbeidsgiver1tidslinje.sisteDag().erRettFør(arbeidsgiver2tidslinje.førsteDag()))
        assertSkjæringstidspunkt(1.januar, 1.januar til 28.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med helgegap`() {
        val arbeidsgiver1tidslinje = 12.S + 2.opphold
        val arbeidsgiver2tidslinje = 14.S
        assertSkjæringstidspunkt(1.januar, 1.januar til 28.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med arbeidsdager i helg`() {
        val arbeidsgiver1tidslinje = 12.S + 1.A
        val arbeidsgiver2tidslinje = 1.A + 14.S
        assertSkjæringstidspunkt(15.januar, 1. januar til 28.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med arbeidsdager og sykedager i helg`() {
        val arbeidsgiver1tidslinje = 14.S
        resetSeed(10.januar)
        val arbeidsgiver2tidslinje = 2.S + 2.A + 7.S
        assertSkjæringstidspunkt(1.januar, 1.januar til 20.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med helgegap og arbeidsdag på fredag`() {
        val arbeidsgiver1tidslinje = 11.S + 1.A + 2.opphold
        val arbeidsgiver2tidslinje = 14.S
        assertSkjæringstidspunkt(15.januar, 1. januar til 28.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med helgegap og arbeidsdag på mandag`() {
        val arbeidsgiver1tidslinje = 12.S + 2.opphold
        val arbeidsgiver2tidslinje = 1.A + 13.S
        assertSkjæringstidspunkt(16.januar, 1. januar til 28.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer overlappende arbeidsdager og sykedager`() {
        val arbeidsgiver1tidslinje = 6.S + 6.A
        resetSeed()
        val arbeidsgiver2tidslinje = 14.S
        assertSkjæringstidspunkt(1.januar, 1. januar til 14.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med gap blir sammenhengende`() {
        val arbeidsgiver1tidslinje = 7.S + 7.A
        resetSeed()
        val arbeidsgiver2tidslinje = 14.A + 14.S
        resetSeed()
        val arbeidsgiver3tidslinje = 7.A + 7.S
        assertSkjæringstidspunkt(1.januar, 1. januar til 28.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje, arbeidsgiver3tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med ferie som gap blir sammenhengende`() {
        val arbeidsgiver1tidslinje = 7.S + 1.F
        val arbeidsgiver2tidslinje = 1.F + 14.S
        assertSkjæringstidspunkt(1.januar, 1. januar til 23.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med ferie og arbeidsdag på samme dager, forblir sammenhengende`() {
        val arbeidsgiver1tidslinje = 7.S + 2.F + 7.S
        resetSeed(1.januar)
        val arbeidsgiver2tidslinje = 7.S + 2.A + 7.S
        assertSkjæringstidspunkt(1.januar, 1. januar til 16.januar, arbeidsgiver1tidslinje)
        assertSkjæringstidspunkt(10.januar, 1. januar til 16.januar, arbeidsgiver2tidslinje)
        assertSkjæringstidspunkt(1.januar, 1. januar til 16.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med ferie og arbeidsdag på samme dager, forblir sammenhengende - motsatt rekkefølge på tidslinjer`() {
        val arbeidsgiver1tidslinje = 7.S + 2.A + 7.S
        resetSeed(1.januar)
        val arbeidsgiver2tidslinje = 7.S + 2.F + 7.S
        assertSkjæringstidspunkt(10.januar, 1. januar til 16.januar, arbeidsgiver1tidslinje)
        assertSkjæringstidspunkt(1.januar, 1. januar til 16.januar, arbeidsgiver2tidslinje)
        assertSkjæringstidspunkt(1.januar, 1. januar til 16.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med ferie og ukjentdag på samme dager, forblir sammenhengende`() {
        val arbeidsgiver1tidslinje = 7.S + 2.F + 7.S
        resetSeed(1.januar)
        val arbeidsgiver2tidslinje = 7.S + 2.UK + 7.S
        assertSkjæringstidspunkt(1.januar, 1. januar til 16.januar, arbeidsgiver1tidslinje)
        assertSkjæringstidspunkt(10.januar, 1. januar til 16.januar, arbeidsgiver2tidslinje)
        assertSkjæringstidspunkt(1.januar, 1. januar til 16.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med ferie og ukjentdag på samme dager, forblir sammenhengende - motsatt rekkefølge på tidslinjer`() {
        val arbeidsgiver1tidslinje = 7.S + 2.UK + 7.S
        resetSeed(1.januar)
        val arbeidsgiver2tidslinje = 7.S + 2.F + 7.S
        assertSkjæringstidspunkt(10.januar, 1. januar til 16.januar, arbeidsgiver1tidslinje)
        assertSkjæringstidspunkt(1.januar, 1. januar til 16.januar, arbeidsgiver2tidslinje)
        assertSkjæringstidspunkt(1.januar, 1. januar til 16.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `arbeidsgivertidslinjer med ferie i begynnelsen, med arbeidsdag på samme dager, forblir usammenhengende`() {
        val arbeidsgiver1tidslinje = 7.F + 7.S
        resetSeed(1.januar)
        val arbeidsgiver2tidslinje = 7.A + 7.S
        assertSkjæringstidspunkt(8.januar, 1. januar til 14.januar, arbeidsgiver1tidslinje)
        assertSkjæringstidspunkt(8.januar, 1. januar til 14.januar, arbeidsgiver2tidslinje)
        assertSkjæringstidspunkt(8.januar, 1. januar til 14.januar, arbeidsgiver1tidslinje, arbeidsgiver2tidslinje)
    }

    @Test
    fun `gir kun skjæringstidspunkt som er relevant for perioden`() {
        val tidslinje = 7.S + 2.opphold + 3.S + 5.opphold + 3.F
        assertSkjæringstidspunkt(1.januar, 1.januar til 7.januar, tidslinje)
        assertSkjæringstidspunkt(10.januar, 10. januar til 12.januar, tidslinje)
        assertSkjæringstidspunkt(null, 18.januar til 20.januar, tidslinje)
        assertSkjæringstidspunkt(10.januar, 1.januar til 20.januar, tidslinje)
    }

    @Test
    fun `Sykedager Nav`(){
        val tidslinje = 5.N
        assertFørsteDagErSkjæringstidspunkt(tidslinje)
    }

    private fun assertSkjæringstidspunkt(forventetSkjæringstidspunkt: LocalDate?, periode: Periode, vararg tidslinje: Sykdomstidslinje) {
        assertEquals(forventetSkjæringstidspunkt, Sykdomstidslinje.sisteRelevanteSkjæringstidspunktForPerioden(periode, tidslinje.toList()))
    }

    private fun assertSkjæringstidspunkt(
        forventetSkjæringstidspunkt: LocalDate,
        vararg hendelse: SykdomshistorikkHendelse
    ) {
        val a = Sykdomshistorikk()
        hendelse.forEach { a.håndter(it) }

        val tidslinje = a.sykdomstidslinje()

        val skjæringstidspunkt = tidslinje.sisteSkjæringstidspunkt()
        assertEquals(forventetSkjæringstidspunkt, skjæringstidspunkt) {
            "Forventet skjæringstidspunkt $forventetSkjæringstidspunkt. " +
                "Fikk $skjæringstidspunkt\n" +
                "Tidslinje:\n$tidslinje"
        }
    }

    private fun søknad(vararg perioder: Søknadsperiode): Søknad {
        return hendelsefabrikk.lagSøknad(
            perioder = perioder,
            sendtTilNAVEllerArbeidsgiver = Søknadsperiode.søknadsperiode(perioder.toList())!!.endInclusive
        )
    }

    private fun inntektsmelding(
        arbeidsgiverperioder: List<Periode>,
        refusjonBeløp: Inntekt = INNTEKT_PR_MÅNED,
        beregnetInntekt: Inntekt = INNTEKT_PR_MÅNED,
        førsteFraværsdag: LocalDate = 1.januar,
        refusjonOpphørsdato: LocalDate = 31.desember,
        endringerIRefusjon: List<Inntektsmelding.Refusjon.EndringIRefusjon> = emptyList()
    ) = hendelsefabrikk.lagInntektsmelding(
        refusjon = Inntektsmelding.Refusjon(refusjonBeløp, refusjonOpphørsdato, endringerIRefusjon),
        førsteFraværsdag = førsteFraværsdag,
        beregnetInntekt = beregnetInntekt,
        arbeidsgiverperioder = arbeidsgiverperioder,
        arbeidsforholdId = null,
        begrunnelseForReduksjonEllerIkkeUtbetalt = null
    ).dager().bitAvInntektsmelding(arbeidsgiverperioder.plusElement(førsteFraværsdag.somPeriode()).periode()!!)!!

    private companion object {
        private const val UNG_PERSON_FNR_2018 = "12029240045"
        private val UNG_PERSON_FØDSELSDATO = 12.februar(1992)
        private const val AKTØRID = "42"
        private const val ORGNUMMER = "987654321"
        private const val INNTEKT = 31000.00
        private val INNTEKT_PR_MÅNED = INNTEKT.månedlig
        private val hendelsefabrikk = ArbeidsgiverHendelsefabrikk(
            aktørId = AKTØRID,
            personidentifikator = UNG_PERSON_FNR_2018.somPersonidentifikator(),
            organisasjonsnummer = ORGNUMMER
        )

        private fun assertDagenErSkjæringstidspunkt(
            dagen: LocalDate,
            sykdomstidslinje: Sykdomstidslinje
        ) {
            val skjæringstidspunkt = sykdomstidslinje.sisteSkjæringstidspunkt()
            assertEquals(
                dagen,
                skjæringstidspunkt
            ) { "Forventet $dagen, men fikk $skjæringstidspunkt.\nPeriode: ${sykdomstidslinje.periode()}\nTidslinjen:\n$sykdomstidslinje" }
        }

        private fun assertFørsteDagErSkjæringstidspunkt(sykdomstidslinje: Sykdomstidslinje) {
            val førsteDag = sykdomstidslinje.periode()?.start
            assertNotNull(førsteDag)
            val skjæringstidspunkt = sykdomstidslinje.sisteSkjæringstidspunkt()
            assertEquals(førsteDag, skjæringstidspunkt) {
                "Forventet $førsteDag, men fikk $skjæringstidspunkt.\nPeriode: ${sykdomstidslinje.periode()}\nTidslinjen:\n$sykdomstidslinje"
            }
        }

        private fun assertFørsteDagErSkjæringstidspunkt(
            perioden: Sykdomstidslinje,
            sykdomstidslinje: Sykdomstidslinje
        ) {
            val førsteDag = perioden.periode()?.start ?: fail { "Tom periode" }
            assertNotNull(førsteDag)
            assertDagenErSkjæringstidspunkt(førsteDag, sykdomstidslinje)
        }
    }
}
