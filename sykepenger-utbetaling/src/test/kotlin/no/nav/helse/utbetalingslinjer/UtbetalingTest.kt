package no.nav.helse.utbetalingslinjer

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.februar
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Simulering
import no.nav.helse.dto.SimuleringResultatDto
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.hendelser.til
import no.nav.helse.hendelser.utbetaling.AnnullerUtbetaling
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.hendelser.utbetaling.Utbetalingsgodkjenning
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.testhelpers.AP
import no.nav.helse.testhelpers.ARB
import no.nav.helse.testhelpers.FRI
import no.nav.helse.testhelpers.NAP
import no.nav.helse.testhelpers.NAV
import no.nav.helse.testhelpers.UKJ
import no.nav.helse.testhelpers.UTELATE
import no.nav.helse.testhelpers.tidslinjeOf
import no.nav.helse.utbetalingslinjer.Endringskode.ENDR
import no.nav.helse.utbetalingslinjer.Endringskode.NY
import no.nav.helse.utbetalingslinjer.Oppdragstatus.AKSEPTERT
import no.nav.helse.utbetalingslinjer.Oppdragstatus.AVVIST
import no.nav.helse.utbetalingslinjer.Utbetaling.Companion.aktive
import no.nav.helse.utbetalingslinjer.Utbetaling.Companion.tillaterOpprettelseAvUtbetaling
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.ANNULLERT
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.FORKASTET
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.GODKJENT
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.GODKJENT_UTEN_UTBETALING
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.IKKE_UTBETALT
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.OVERFØRT
import no.nav.helse.utbetalingslinjer.Utbetalingstatus.UTBETALT
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class UtbetalingTest {

    private lateinit var aktivitetslogg: Aktivitetslogg

    private companion object {
        private const val UNG_PERSON_FNR_2018 = "12029240045"
        private const val ORGNUMMER = "987654321"
    }

    @BeforeEach
    internal fun initEach() {
        aktivitetslogg = Aktivitetslogg()
    }

    @Test
    fun `forkaster annulleringer som utbetalingen peker på`() {
        val annullering1 = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 1.februar til 15.februar,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.ANNULLERING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248
        ).also { it.opprett(Aktivitetslogg()) }
        val annullering2 = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 3.mars til 31.mars,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.ANNULLERING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248
        ).also { it.opprett(Aktivitetslogg()) }

        val utbetalingen = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 1.januar til 31.mars,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.UTBETALING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248,
            annulleringer = listOf(
                annullering1,
                annullering2
            )
        ).also { it.opprett(Aktivitetslogg()) }

        utbetalingen.forkast(Aktivitetslogg())

        assertEquals(FORKASTET, annullering1.inspektør.tilstand)
        assertEquals(FORKASTET, annullering2.inspektør.tilstand)
        assertEquals(FORKASTET, utbetalingen.inspektør.tilstand)
    }

    @Test
    fun `ny utbetaling erstatter flere uten utbetalinger`() {
        val annullering1 = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 1.februar til 15.februar,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.ANNULLERING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248
        ).also { it.opprett(Aktivitetslogg()) }
        val annullering2 = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 3.mars til 31.mars,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.ANNULLERING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248
        ).also { it.opprett(Aktivitetslogg()) }

        val utbetalingen = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 1.januar til 31.mars,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.UTBETALING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248,
            annulleringer = listOf(
                annullering1,
                annullering2
            )
        ).also { it.opprett(Aktivitetslogg()) }

        val hendelse = godkjenn(utbetalingen)
        assertFalse(hendelse.harBehov(Behovtype.Utbetaling))
        assertEquals(ANNULLERT, annullering1.inspektør.tilstand)
        assertEquals(ANNULLERT, annullering2.inspektør.tilstand)
        assertEquals(GODKJENT_UTEN_UTBETALING, utbetalingen.inspektør.tilstand)
    }

    @Test
    fun `ny utbetaling erstatter flere med utbetalinger`() {
        val annullering1 = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 1.februar til 15.februar,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon, linjer = listOf(
                Utbetalingslinje(1.februar, 15.februar, beløp = 500, grad = 10, delytelseId = 1, endringskode = ENDR, datoStatusFom = 1.februar)
            )),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.ANNULLERING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248
        ).also { it.opprett(Aktivitetslogg()) }
        val annullering2 = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 3.mars til 31.mars,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.ANNULLERING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248
        ).also { it.opprett(Aktivitetslogg()) }

        val utbetalingen = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 1.januar til 31.mars,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.UTBETALING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248,
            annulleringer = listOf(
                annullering1,
                annullering2
            )
        ).also { it.opprett(Aktivitetslogg()) }

        val hendelse = godkjenn(utbetalingen)
        assertEquals(1, hendelse.behov().size)
        hendelse.sisteBehov(Behovtype.Utbetaling).also { behov ->
            val detaljer = behov.detaljer()
            val kontekster = behov.kontekst()

            val oppdragInspektør = annullering1.inspektør.arbeidsgiverOppdrag.inspektør
            assertEquals(oppdragInspektør.mottaker, detaljer.getValue("mottaker"))
            assertEquals(oppdragInspektør.fagsystemId(), detaljer.getValue("fagsystemId"))

            assertEquals(annullering1.inspektør.utbetalingId.toString(), kontekster.getValue("utbetalingId"))
            assertEquals(oppdragInspektør.fagsystemId(), kontekster.getValue("fagsystemId"))
        }

        assertEquals(OVERFØRT, annullering1.inspektør.tilstand)
        assertEquals(ANNULLERT, annullering2.inspektør.tilstand)
        assertEquals(GODKJENT, utbetalingen.inspektør.tilstand)

        kvittèr(annullering1)

        assertEquals(ANNULLERT, annullering1.inspektør.tilstand)
        assertEquals(ANNULLERT, annullering2.inspektør.tilstand)
        assertEquals(GODKJENT, utbetalingen.inspektør.tilstand)

        kvittèr(annullering1, utbetalingmottaker = utbetalingen)

        assertEquals(ANNULLERT, annullering1.inspektør.tilstand)
        assertEquals(ANNULLERT, annullering2.inspektør.tilstand)
        assertEquals(GODKJENT_UTEN_UTBETALING, utbetalingen.inspektør.tilstand)
    }

    @Test
    fun `ny utbetaling erstatter flere med utbetalinger - med egne utbetalinger`() {
        val annullering1 = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 1.februar til 15.februar,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon, linjer = listOf(
                Utbetalingslinje(1.februar, 15.februar, beløp = 500, grad = 10, delytelseId = 1, endringskode = ENDR, datoStatusFom = 1.februar)
            )),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.ANNULLERING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248
        ).also { it.opprett(Aktivitetslogg()) }
        val annullering2 = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 3.mars til 31.mars,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.ANNULLERING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248
        ).also { it.opprett(Aktivitetslogg()) }

        val utbetalingen = Utbetaling(
            korrelerendeUtbetaling = null,
            periode = 1.januar til 31.mars,
            utbetalingstidslinje = Utbetalingstidslinje(),
            arbeidsgiverOppdrag = Oppdrag("orgnr", Fagområde.SykepengerRefusjon, linjer = listOf(
                Utbetalingslinje(1.januar, 31.januar, beløp = 500, grad = 10, delytelseId = 1, endringskode = NY)
            )),
            personOppdrag = Oppdrag("fnr", Fagområde.Sykepenger),
            type = Utbetalingtype.UTBETALING,
            maksdato = 28.desember,
            forbrukteSykedager = 0,
            gjenståendeSykedager = 248,
            annulleringer = listOf(
                annullering1,
                annullering2
            )
        ).also { it.opprett(Aktivitetslogg()) }

        val hendelse = godkjenn(utbetalingen)
        assertEquals(1, hendelse.behov().size)
        hendelse.sisteBehov(Behovtype.Utbetaling).also { behov ->
            val detaljer = behov.detaljer()
            val kontekster = behov.kontekst()

            val oppdragInspektør = annullering1.inspektør.arbeidsgiverOppdrag.inspektør
            assertEquals(oppdragInspektør.mottaker, detaljer.getValue("mottaker"))
            assertEquals(oppdragInspektør.fagsystemId(), detaljer.getValue("fagsystemId"))

            assertEquals(annullering1.inspektør.utbetalingId.toString(), kontekster.getValue("utbetalingId"))
            assertEquals(oppdragInspektør.fagsystemId(), kontekster.getValue("fagsystemId"))
        }

        assertEquals(OVERFØRT, annullering1.inspektør.tilstand)
        assertEquals(ANNULLERT, annullering2.inspektør.tilstand)
        assertEquals(GODKJENT, utbetalingen.inspektør.tilstand)

        kvittèr(annullering1)

        assertEquals(ANNULLERT, annullering1.inspektør.tilstand)
        assertEquals(ANNULLERT, annullering2.inspektør.tilstand)
        assertEquals(GODKJENT, utbetalingen.inspektør.tilstand)

        val kvitteringen = kvittèr(annullering1, utbetalingmottaker = utbetalingen)
        assertEquals(1, kvitteringen.behov().size)
        kvitteringen.sisteBehov(Behovtype.Utbetaling).also { behov ->
            val detaljer = behov.detaljer()
            val kontekster = behov.kontekst()

            val oppdragInspektør = utbetalingen.inspektør.arbeidsgiverOppdrag.inspektør
            assertEquals(oppdragInspektør.mottaker, detaljer.getValue("mottaker"))
            assertEquals(oppdragInspektør.fagsystemId(), detaljer.getValue("fagsystemId"))

            assertEquals(utbetalingen.inspektør.utbetalingId.toString(), kontekster.getValue("utbetalingId"))
            assertEquals(oppdragInspektør.fagsystemId(), kontekster.getValue("fagsystemId"))
        }

        assertEquals(ANNULLERT, annullering1.inspektør.tilstand)
        assertEquals(ANNULLERT, annullering2.inspektør.tilstand)
        assertEquals(OVERFØRT, utbetalingen.inspektør.tilstand)
    }

    @Test
    fun `ny utbetaling starter før`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV, startDato = 17.januar).betale()
        val utbetaling1 = opprettUtbetaling(tidslinje)

        val tidslinjeNy = tidslinjeOf(16.AP, 15.NAV, 16.NAV, startDato = 1.januar).betale()
        val utbetaling2 = opprettUtbetaling(tidslinjeNy, utbetaling1, periode = 31.januar.somPeriode())

        val utbetaling1Inspektør = utbetaling1.inspektør
        val utbetaling2Inspektør = utbetaling2.inspektør

        assertEquals("PPPPP PPPPPPP PPPPNHH NNNNNHH NNNNN", utbetaling1Inspektør.utbetalingstidslinje.toString())
        assertEquals(17.januar til 16.februar, utbetaling1Inspektør.utbetalingstidslinje.periode())
        assertEquals(1, utbetaling1Inspektør.arbeidsgiverOppdrag.size)
        utbetaling1Inspektør.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(2.februar til 16.februar, linje.fom til linje.tom)
            assertEquals(NY, linje.endringskode)
            assertEquals(1, linje.delytelseId)
            assertNull(linje.refDelytelseId)
        }

        assertEquals(utbetaling1Inspektør.korrelasjonsId, utbetaling2Inspektør.korrelasjonsId)
        assertEquals(utbetaling1Inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), utbetaling2Inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNN", utbetaling2Inspektør.utbetalingstidslinje.toString())
        assertEquals(1.januar til 31.januar, utbetaling2Inspektør.utbetalingstidslinje.periode())
        assertEquals(2, utbetaling2Inspektør.arbeidsgiverOppdrag.size)
        utbetaling2Inspektør.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(17.januar til 31.januar, linje.fom til linje.tom)
            assertEquals(NY, linje.endringskode)
            assertEquals(2, linje.delytelseId)
            assertEquals(1, linje.refDelytelseId)
        }
        utbetaling2Inspektør.arbeidsgiverOppdrag[1].inspektør.also { linje ->
            assertEquals(2.februar til 16.februar, linje.fom til linje.tom)
            assertEquals(NY, linje.endringskode)
            assertEquals(3, linje.delytelseId)
            assertEquals(2, linje.refDelytelseId)
        }
    }

    @Test
    fun `ny utbetaling splitter opp tidligere utbetaling før`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV, 1.ARB, 27.NAV, 2.ARB, 29.NAV, startDato = 1.januar).betale()
        val utbetaling1 = opprettUtbetaling(tidslinje)

        val tidslinjeNy = tidslinjeOf(16.AP, 15.NAV, 18.ARB, 16.AP, 25.NAV, startDato = 1.januar).betale()
        val utbetaling2 = opprettUtbetaling(tidslinjeNy, utbetaling1, periode = 31.januar.somPeriode())

        val utbetaling1Inspektør = utbetaling1.inspektør
        val utbetaling2Inspektør = utbetaling2.inspektør

        assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNNANHH NNNNNHH NNNNNHH NNNNNHH NNNAAHH NNNNNHH NNNNNHH NNNNNHH NNNNNH", utbetaling1Inspektør.utbetalingstidslinje.toString())
        assertEquals(1.januar til 31.mars, utbetaling1Inspektør.utbetalingstidslinje.periode())
        assertEquals(3, utbetaling1Inspektør.arbeidsgiverOppdrag.size)
        utbetaling1Inspektør.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(17.januar til 31.januar, linje.fom til linje.tom)
            assertEquals(NY, linje.endringskode)
            assertEquals(1, linje.delytelseId)
            assertNull(linje.refDelytelseId)
        }
        utbetaling1Inspektør.arbeidsgiverOppdrag[1].inspektør.also { linje ->
            assertEquals(2.februar til 28.februar, linje.fom til linje.tom)
            assertEquals(NY, linje.endringskode)
            assertEquals(2, linje.delytelseId)
            assertEquals(1, linje.refDelytelseId)
        }
        utbetaling1Inspektør.arbeidsgiverOppdrag[2].inspektør.also { linje ->
            assertEquals(3.mars til 30.mars, linje.fom til linje.tom)
            assertEquals(NY, linje.endringskode)
            assertEquals(3, linje.delytelseId)
            assertEquals(2, linje.refDelytelseId)
        }

        assertEquals(utbetaling1Inspektør.korrelasjonsId, utbetaling2Inspektør.korrelasjonsId)
        assertEquals(utbetaling1Inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), utbetaling2Inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNNHH NNN", utbetaling2Inspektør.utbetalingstidslinje.toString())
        assertEquals(1.januar til 31.januar, utbetaling2Inspektør.utbetalingstidslinje.periode())
        assertEquals(1, utbetaling2Inspektør.arbeidsgiverOppdrag.size)
        utbetaling2Inspektør.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(17.januar til 31.januar, linje.fom til linje.tom)
            assertEquals(NY, linje.endringskode)
            assertEquals(4, linje.delytelseId)
            assertEquals(3, linje.refDelytelseId)
        }
    }
    @Test
    fun `ny utbetaling slutter på helg`() {
        val tidslinje = tidslinjeOf(16.AP, 5.NAV, startDato = 1.januar).betale()
        val utbetaling1 = opprettUtbetaling(tidslinje)

        val tidslinjeNy = tidslinjeOf(16.AP, 5.NAV, 4.NAV, 1.FRI, 2.NAV, 5.NAV, startDato = 1.januar).betale()
        val utbetaling2 = opprettUtbetaling(tidslinjeNy, utbetaling1, periode = 28.januar.somPeriode())

        val utbetaling1Inspektør = utbetaling1.inspektør
        val utbetaling2Inspektør = utbetaling2.inspektør

        assertEquals("PPPPPPP PPPPPPP PPNNNHH", utbetaling1Inspektør.utbetalingstidslinje.toString())
        assertEquals(1.januar til 21.januar, utbetaling1Inspektør.utbetalingstidslinje.periode())
        assertEquals(1, utbetaling1Inspektør.arbeidsgiverOppdrag.size)
        utbetaling1Inspektør.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(17.januar til 19.januar, linje.fom til linje.tom)
            assertEquals(NY, linje.endringskode)
            assertEquals(1, linje.delytelseId)
            assertNull(linje.refDelytelseId)
        }

        assertEquals(utbetaling1Inspektør.korrelasjonsId, utbetaling2Inspektør.korrelasjonsId)
        assertEquals(utbetaling1Inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), utbetaling2Inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals("PPPPPPP PPPPPPP PPNNNHH NNNNFHH", utbetaling2Inspektør.utbetalingstidslinje.toString())
        assertEquals(1.januar til 28.januar, utbetaling2Inspektør.utbetalingstidslinje.periode())
        assertEquals(1, utbetaling2Inspektør.arbeidsgiverOppdrag.size)
        utbetaling2Inspektør.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(17.januar til 25.januar, linje.fom til linje.tom)
            assertEquals(ENDR, linje.endringskode)
            assertEquals(1, linje.delytelseId)
            assertNull(linje.refDelytelseId)
        }
    }

    @Test
    fun `utbetalinger med ulik korrelasjonsId kan ikke overlappe`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettUtbetaling(tidslinje)
        val utbetaling2 = opprettUtbetaling(tidslinje)
        assertFalse(listOf(utbetaling).tillaterOpprettelseAvUtbetaling(utbetaling2))
    }

    @Test
    fun `utbetalinger med ulik korrelasjonsId kan overlappe hvis det er annullering`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettUtbetaling(tidslinje)
        val utbetaling2 = annuller(opprettUtbetaling(tidslinje))
        assertTrue(listOf(utbetaling).tillaterOpprettelseAvUtbetaling(utbetaling2!!))
    }

    @Test
    fun `utbetalinger med samme korrelasjonsId kan overlappe`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettUtbetaling(tidslinje)
        val tidslinje2 = tidslinjeOf(16.AP, 30.NAV).betale()
        val utbetaling2 = opprettUtbetaling(tidslinje2, tidligere = utbetaling)
        assertTrue(listOf(utbetaling).tillaterOpprettelseAvUtbetaling(utbetaling2))
    }

    @Test
    fun `tom utbetaling slutter før tidligere`() {
        // periode med 1 SykNav + 1 AGP dag
        val tidslinje1 = tidslinjeOf(31.UTELATE, 1.NAP, 1.AP).betale()
        val utbetaling1 = opprettUtbetaling(tidslinje1, periode = 1.februar til 2.februar)
        // perioden strekkes tilbake med 31 Foreldrepengedager, og 2 AGP dager etter
        val tidslinje2 = tidslinjeOf(31.FRI, 2.AP).betale()
        val utbetaling2 = opprettUtbetaling(tidslinje2, tidligere = utbetaling1, periode = 1.januar til 2.februar)

        val utbetaling1Inspektør = utbetaling1.inspektør
        val utbetaling2Inspektør = utbetaling2.inspektør

        assertEquals("?P", utbetaling1Inspektør.utbetalingstidslinje.toString())
        assertEquals("FFFFFFF FFFFFFF FFFFFFF FFFFFFF FFFPP", utbetaling2Inspektør.utbetalingstidslinje.toString())
        assertEquals(1.februar til 2.februar, utbetaling1Inspektør.utbetalingstidslinje.periode())
        assertEquals(1.januar til 2.februar, utbetaling2Inspektør.utbetalingstidslinje.periode())
        assertEquals(utbetaling2Inspektør.korrelasjonsId, utbetaling1Inspektør.korrelasjonsId)

        assertEquals(1, utbetaling1Inspektør.arbeidsgiverOppdrag.size)
        utbetaling1Inspektør.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(1.februar til 1.februar, linje.fom til linje.tom)
            assertEquals(NY, linje.endringskode)
            assertEquals(1, linje.delytelseId)
            assertNull(linje.refDelytelseId)
        }
        assertEquals(1, utbetaling2Inspektør.arbeidsgiverOppdrag.size)
        utbetaling2Inspektør.arbeidsgiverOppdrag[0].inspektør.also { linje ->
            assertEquals(1.februar til 1.februar, linje.fom til linje.tom)
            assertEquals(ENDR, linje.endringskode)
            assertEquals("OPPH", linje.statuskode)
            assertEquals(1.februar, linje.datoStatusFom)
            assertEquals(1, linje.delytelseId)
            assertNull(linje.refDelytelseId)
        }
    }

    @Test
    fun `utbetaling starter i Ny`() {
        val tidslinje = tidslinjeOf(16.AP, 17.NAV).betale()

        val sisteDato = 21.januar
        val utbetaling = Utbetaling.lagUtbetaling(
            emptyList(),
            UNG_PERSON_FNR_2018,
            ORGNUMMER,
            tidslinje,
            sisteDato.somPeriode(),
            aktivitetslogg,
            LocalDate.MAX,
            100,
            148
        ).first
        assertEquals(Utbetalingstatus.NY, utbetaling.inspektør.tilstand)
        utbetaling.opprett(aktivitetslogg)
        assertEquals(IKKE_UTBETALT, utbetaling.inspektør.tilstand)
    }

    @Test
    fun `utbetalinger inkluderer ikke dager etter siste dato`() {
        val tidslinje = tidslinjeOf(16.AP, 17.NAV).betale()

        val sisteDato = 21.januar
        val utbetaling = Utbetaling.lagUtbetaling(
            emptyList(),
            UNG_PERSON_FNR_2018,
            ORGNUMMER,
            tidslinje,
            sisteDato.somPeriode(),
            aktivitetslogg,
            LocalDate.MAX,
            100,
            148
        ).first.also { it.opprett(aktivitetslogg) }
        assertEquals(1.januar til sisteDato, utbetaling.inspektør.utbetalingstidslinje.periode())
        assertEquals(1.januar til sisteDato, utbetaling.inspektør.periode)
    }

    @Test
    fun `periode for annullering`() {
        val tidslinje = tidslinjeOf(16.AP, 5.NAV, 1.FRI, 6.NAV, 1.FRI, 4.NAV).betale()
        val første = opprettUtbetaling(tidslinje, periode = 21.januar.somPeriode())
        val andre = opprettUtbetaling(tidslinje, første, 28.januar.somPeriode())
        val tredje = opprettUtbetaling(tidslinje, andre, 2.februar.somPeriode())
        val annullering = annuller(tredje) ?: fail { "forventet utbetaling" }
        val utbetalingInspektør = annullering.inspektør
        assertEquals(første.inspektør.korrelasjonsId, utbetalingInspektør.korrelasjonsId)
        assertEquals(1.januar til 2.februar, utbetalingInspektør.periode)
        assertEquals(17.januar, utbetalingInspektør.arbeidsgiverOppdrag.inspektør.periode?.start)
        assertEquals(30.januar, utbetalingInspektør.arbeidsgiverOppdrag.first().inspektør.fom)
        assertEquals(17.januar, utbetalingInspektør.arbeidsgiverOppdrag.first().inspektør.datoStatusFom)
        assertEquals(2.februar, utbetalingInspektør.arbeidsgiverOppdrag.last().inspektør.tom)
    }

    @Test
    fun `forlenger seg ikke på en annullering`() {
        val tidslinje = tidslinjeOf(16.AP, 10.NAV).betale()
        val første = opprettUtbetaling(tidslinje)
        val annullering = annuller(første) ?: fail { "forventet utbetaling" }
        val andre = opprettUtbetaling(tidslinje, annullering)
        assertNotEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
        assertNotEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
    }

    @Test
    fun `omgjøre en revurdert periode med opphør`() {
        val tidslinje = tidslinjeOf(16.AP, 10.NAV)
        val ferietidslinje = tidslinjeOf(16.AP, 10.FRI)
        val første = opprettUtbetaling(tidslinje.betale())
        val andre = opprettUtbetaling(ferietidslinje.betale(), første)
        val tredje = opprettUtbetaling(tidslinje.betale(), andre)
        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
        assertTrue(andre.inspektør.arbeidsgiverOppdrag[0].erOpphør())
        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), tredje.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(første.inspektør.korrelasjonsId, tredje.inspektør.korrelasjonsId)
        assertEquals(17.januar, tredje.inspektør.arbeidsgiverOppdrag.first().inspektør.fom)
        assertEquals(26.januar, tredje.inspektør.arbeidsgiverOppdrag.last().inspektør.tom)
        assertEquals(17.januar, tredje.inspektør.arbeidsgiverOppdrag.inspektør.periode?.start)
    }

    @Test
    fun `forlenge en utbetaling uten utbetaling`() {
        val tidslinje = tidslinjeOf(16.AP, 10.NAV).betale()
        val første = opprettUtbetaling(tidslinje.kutt(16.januar))
        val andre = opprettUtbetaling(tidslinje, første)
        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
        assertTrue(første.inspektør.arbeidsgiverOppdrag.isEmpty())
        assertTrue(andre.inspektør.arbeidsgiverOppdrag.isNotEmpty())
        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
        assertEquals(NY, andre.inspektør.arbeidsgiverOppdrag.inspektør.endringskode)
        assertEquals(NY, andre.inspektør.arbeidsgiverOppdrag[0].inspektør.endringskode)
        assertEquals(17.januar, andre.inspektør.arbeidsgiverOppdrag[0].inspektør.fom)
        assertEquals(26.januar, andre.inspektør.arbeidsgiverOppdrag[0].inspektør.tom)
    }

    @Test
    fun `forlenger seg på en revurdert periode med opphør`() {
        val tidslinje = tidslinjeOf(16.AP, 10.NAV).betale()
        val ferietidslinje = tidslinjeOf(16.AP, 10.FRI)
        val første = opprettUtbetaling(tidslinje)
        val andre = opprettUtbetaling(ferietidslinje.betale(), første)
        val tredje = opprettUtbetaling(beregnUtbetalinger(ferietidslinje + tidslinjeOf(10.NAV, startDato = 27.januar)), andre)
        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
        assertTrue(andre.inspektør.arbeidsgiverOppdrag[0].erOpphør())
        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), tredje.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(første.inspektør.korrelasjonsId, tredje.inspektør.korrelasjonsId)
        assertEquals(27.januar, tredje.inspektør.arbeidsgiverOppdrag.first().inspektør.fom)
        assertNull(tredje.inspektør.arbeidsgiverOppdrag.first().inspektør.datoStatusFom)
        assertEquals(5.februar, tredje.inspektør.arbeidsgiverOppdrag.last().inspektør.tom)
        assertEquals(27.januar, tredje.inspektør.arbeidsgiverOppdrag.inspektør.periode?.start)
    }

    @Test
    fun nettoBeløp() {
        val tidslinje = tidslinjeOf(11.NAV).betale()
        val første = opprettUtbetaling(tidslinje, periode = 7.januar.somPeriode())
        val andre = opprettUtbetaling(tidslinje, første)

        assertEquals(6000, første.inspektør.arbeidsgiverOppdrag.inspektør.nettoBeløp)
        assertEquals(4800, andre.inspektør.arbeidsgiverOppdrag.inspektør.nettoBeløp)
    }

    @Test
    fun `sorterer etter når fagsystemIDen ble oppretta`() {
        val tidslinje = tidslinjeOf(
            16.AP, 3.NAV, 5.NAV(1200, 50.0), 16.ARB, 16.AP, 1.NAV,
            startDato = 1.januar(2020)
        ).betale()

        val første = opprettUtbetaling(tidslinje.kutt(19.januar(2020)), periode = 19.januar(2020).somPeriode())
        val andre = opprettUtbetaling(tidslinje.kutt(24.januar(2020)), tidligere = første, periode = 24.januar(2020).somPeriode())
        val tredje = opprettUtbetaling(tidslinje.kutt(18.februar(2020)), tidligere = andre, periode = 18.februar(2020).somPeriode())
        val andreAnnullert = annuller(andre) ?: fail { "forventet utbetaling" }
        godkjenn(andreAnnullert)
        assertEquals(listOf(andre, tredje), listOf(tredje, andre, første).aktive())
        assertEquals(listOf(tredje), listOf(andreAnnullert, tredje, andre, første).aktive())
    }

    @Test
    fun `setter refFagsystemId og refDelytelseId når en linje peker på en annen`() {
        val tidslinje = tidslinjeOf(
            16.AP, 3.NAV, 5.NAV(1200, 50.0),
            startDato = 1.januar(2020)
        ).betale()

        val tidligere = opprettUtbetaling(tidslinje.kutt(19.januar(2020)))
        val utbetaling = opprettUtbetaling(tidslinje.kutt(24.januar(2020)), tidligere = tidligere)

        assertEquals(tidligere.inspektør.korrelasjonsId, utbetaling.inspektør.korrelasjonsId)
        utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.also { inspektør ->
            assertEquals(2, inspektør.antallLinjer())
            assertNull(inspektør.refDelytelseId(0))
            assertNull(inspektør.refFagsystemId(0))
            assertNotNull(inspektør.refDelytelseId(1))
            assertNotNull(inspektør.refFagsystemId(1))
        }
    }

    @Test
    fun `separate utbetalinger`() {
        val tidslinje = tidslinjeOf(
            16.AP,
            9.NAV,
            16.ARB,
            5.AP,
            30.NAV,
            startDato = 1.januar(2020)
        ).betale()

        val første = opprettUtbetaling(tidslinje.kutt(19.januar(2020)), periode = 19.januar(2020).somPeriode())
        val andre = opprettUtbetaling(tidslinje, tidligere = første, periode = tidslinje.periode().endInclusive.somPeriode())

        val inspektør1 = første.inspektør.arbeidsgiverOppdrag.inspektør
        val inspektør2 = andre.inspektør.arbeidsgiverOppdrag.inspektør
        assertNotEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
        assertEquals(1, inspektør1.antallLinjer())
        assertEquals(1, inspektør2.antallLinjer())
        assertNull(inspektør1.refDelytelseId(0))
        assertNull(inspektør1.refFagsystemId(0))
        assertNull(inspektør2.refDelytelseId(0))
        assertNull(inspektør2.refFagsystemId(0))
        assertNotEquals(inspektør1.fagsystemId(), inspektør2.fagsystemId())
    }

    @Test
    fun `kan forkaste uten utbetalinger`() {
        assertTrue(Utbetaling.kanForkastes(emptyList(), emptyList()))
    }


    @Test
    fun `kan forkaste ubetalt utbetaling`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettUbetaltUtbetaling(tidslinje)
        assertTrue(Utbetaling.kanForkastes(listOf(utbetaling), listOf(utbetaling)))
    }

    @Test
    fun `kan forkaste underkjent utbetaling`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettUbetaltUtbetaling(tidslinje)
        godkjenn(utbetaling, false)
        assertTrue(Utbetaling.kanForkastes(listOf(utbetaling), listOf(utbetaling)))
    }

    @Test
    fun `kan forkaste forkastet utbetaling`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettUbetaltUtbetaling(tidslinje)
        utbetaling.forkast(Aktivitetslogg())
        assertTrue(Utbetaling.kanForkastes(listOf(utbetaling), listOf(utbetaling)))
    }

    @Test
    fun `kan ikke forkaste utbetaling i spill`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettGodkjentUtbetaling(tidslinje)
        assertFalse(Utbetaling.kanForkastes(listOf(utbetaling), listOf(utbetaling)))
        assertFalse(Utbetaling.kanForkastes(listOf(utbetaling), listOf(utbetaling)))
        kvittèr(utbetaling, utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertFalse(Utbetaling.kanForkastes(listOf(utbetaling), listOf(utbetaling)))
    }

    @Test
    fun `kan ikke forkaste feilet utbetaling`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettGodkjentUtbetaling(tidslinje)
        kvittèr(utbetaling, utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), status = AVVIST)
        assertFalse(Utbetaling.kanForkastes(listOf(utbetaling), listOf(utbetaling)))
    }

    @Test
    fun `kan forkaste annullert utbetaling`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV).betale()
        val utbetaling = opprettUtbetaling(tidslinje)
        val annullering = annuller(utbetaling) ?: fail { "Kunne ikke annullere" }
        assertTrue(Utbetaling.kanForkastes(listOf(utbetaling), listOf(annullering)))
    }

    @Test
    fun `kan forkaste utbetalt utbetaling dersom den er annullert`() {
        val tidslinje = tidslinjeOf(16.AP, 32.NAV).betale()
        val utbetaling = opprettUtbetaling(tidslinje.kutt(31.januar))
        val annullert = opprettUtbetaling(tidslinje.kutt(17.februar), tidligere = utbetaling).let {
            annuller(it)
        } ?: fail { "Kunne ikke annullere" }
        assertTrue(Utbetaling.kanForkastes(listOf(utbetaling), listOf(annullert)))
    }

    @Test
    fun `går ikke videre når ett av to oppdrag er akseptert`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val utbetaling = opprettGodkjentUtbetaling(tidslinje)
        kvittèr(utbetaling)
        assertEquals(OVERFØRT, utbetaling.inspektør.tilstand)
    }

    @Test
    fun `går videre når begge oppdragene er akseptert`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val utbetaling = opprettGodkjentUtbetaling(tidslinje)
        kvittèr(utbetaling)
        kvittèr(utbetaling, utbetaling.inspektør.personOppdrag.fagsystemId())
        assertEquals(UTBETALT, utbetaling.inspektør.tilstand)
    }

    @Test
    fun `utbetalingen er feilet dersom én av oppdragene er avvist 1`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val utbetaling = opprettGodkjentUtbetaling(tidslinje)
        kvittèr(utbetaling, utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), AVVIST)
        assertEquals(OVERFØRT, utbetaling.inspektør.tilstand)
    }

    @Test
    fun `tar imot kvittering på det andre oppdraget selv om utbetalingen har feilet`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val utbetaling = opprettGodkjentUtbetaling(tidslinje)
        kvittèr(utbetaling, utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), AVVIST)
        kvittèr(utbetaling, utbetaling.inspektør.personOppdrag.fagsystemId(), AKSEPTERT)
        assertEquals(AVVIST, utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.status())
        assertEquals(AKSEPTERT, utbetaling.inspektør.personOppdrag.inspektør.status())
        assertEquals(OVERFØRT, utbetaling.inspektør.tilstand)
    }

    @Test
    fun `utbetalingen er feilet dersom én av oppdragene er avvist 2`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val utbetaling = opprettGodkjentUtbetaling(tidslinje)
        kvittèr(utbetaling, utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), AKSEPTERT)
        kvittèr(utbetaling, utbetaling.inspektør.personOppdrag.fagsystemId(), AVVIST)
        assertEquals(OVERFØRT, utbetaling.inspektør.tilstand)
    }

    @Test
    fun `delvis refusjon`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val utbetaling = opprettUtbetaling(tidslinje)
        assertTrue(utbetaling.harDelvisRefusjon())
    }

    @Test
    fun `annullere delvis refusjon`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val utbetaling = opprettUtbetaling(tidslinje)
        val annullering = annuller(utbetaling) ?: fail { "forventet utbetaling" }
        assertTrue(annullering.inspektør.arbeidsgiverOppdrag.last().erOpphør())
        assertTrue(annullering.inspektør.personOppdrag.last().erOpphør())
    }

    @Test
    fun `annullere utbetaling med full refusjon, så null refusjon`() {
        val tidslinje = tidslinjeOf(16.AP, 5.NAV, 10.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val første = opprettUtbetaling(tidslinje.kutt(21.januar))
        val andre = opprettUtbetaling(tidslinje, første)
        val annullering = annuller(andre) ?: fail { "forventet utbetaling" }
        assertTrue(annullering.inspektør.arbeidsgiverOppdrag.last().erOpphør())
        assertTrue(annullering.inspektør.personOppdrag.last().erOpphør())
    }

    @Test
    fun `null refusjon`() {
        val tidslinje = tidslinjeOf(16.AP, 30.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 0)).betale()
        val første = opprettUtbetaling(tidslinje.kutt(31.januar))
        val andre = opprettUtbetaling(tidslinje, første)
        assertFalse(første.harDelvisRefusjon())
        assertTrue(første.harUtbetalinger())
        assertEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
    }

    @Test
    fun `korrelasjonsId er lik på brukerutbetalinger direkte fra Infotrygd`() {
        val tidslinje =
            beregnUtbetalinger(tidslinjeOf(5.UKJ, 26.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 0)))
        val første = opprettUtbetaling(tidslinje.kutt(21.januar))
        val andre = opprettUtbetaling(tidslinje, første)
        assertEquals(første.inspektør.personOppdrag.fagsystemId(), andre.inspektør.personOppdrag.fagsystemId())
        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
    }

    @Test
    fun `korrelasjonsId er lik på brukerutbetalinger fra Infotrygd`() {
        val tidslinje = beregnUtbetalinger(
            tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 0), 5.UKJ, 23.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 0))
        )
        val første = opprettUtbetaling(tidslinje.kutt(31.januar), periode = 31.januar.somPeriode())
        val andre = opprettUtbetaling(tidslinje.kutt(20.februar), første, periode = 20.februar.somPeriode())
        val tredje = opprettUtbetaling(tidslinje, andre)
        assertNotEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
        assertEquals(andre.inspektør.personOppdrag.fagsystemId(), tredje.inspektør.personOppdrag.fagsystemId())
        assertEquals(andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), tredje.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(andre.inspektør.korrelasjonsId, tredje.inspektør.korrelasjonsId)
    }

    @Test
    fun `korrelasjonsId er lik på arbeidsgiveroppdrag direkte fra Infotrygd`() {
        val tidslinje = beregnUtbetalinger(tidslinjeOf(5.UKJ, 26.NAV))
        val første = opprettUtbetaling(tidslinje.kutt(21.januar))
        val andre = opprettUtbetaling(tidslinje, første)
        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(første.inspektør.personOppdrag.fagsystemId(), andre.inspektør.personOppdrag.fagsystemId())
        assertEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
    }

    @Test
    fun `korrelasjonsId er lik på arbeidsgiveroppdrag fra Infotrygd`() {
        val tidslinje = beregnUtbetalinger(tidslinjeOf(16.AP, 15.NAV, 5.UKJ, 23.NAV))
        val første = opprettUtbetaling(tidslinje.kutt(31.januar), periode = 31.januar.somPeriode())
        val andre = opprettUtbetaling(tidslinje.kutt(20.februar), første, periode = 20.februar.somPeriode())
        val tredje = opprettUtbetaling(tidslinje, andre)
        assertNotEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
        assertEquals(andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), tredje.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(andre.inspektør.personOppdrag.fagsystemId(), tredje.inspektør.personOppdrag.fagsystemId())
        assertEquals(andre.inspektør.korrelasjonsId, tredje.inspektør.korrelasjonsId)
    }

    @Test
    fun `overføre utbetaling med delvis refusjon`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 600)).betale()
        val utbetaling = opprettUbetaltUtbetaling(tidslinje)
        val hendelselogg = godkjenn(utbetaling)
        val utbetalingsbehov = hendelselogg.behov().filter { it.type == Behovtype.Utbetaling }
        assertEquals(2, utbetalingsbehov.size) { "Forventer to utbetalingsbehov" }
        val fagområder = utbetalingsbehov.map { it.detaljer().getValue("fagområde") as String }
        assertTrue(Fagområde.Sykepenger.verdi in fagområder)
        assertTrue(Fagområde.SykepengerRefusjon.verdi in fagområder)
    }

    @Test
    fun `overføre utbetaling med null refusjon`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 0)).betale()
        val utbetaling = opprettUbetaltUtbetaling(tidslinje)
        val hendelselogg = godkjenn(utbetaling)
        val utbetalingsbehov = hendelselogg.behov().filter { it.type == Behovtype.Utbetaling }
        assertEquals(1, utbetalingsbehov.size) { "Forventer bare ett utbetalingsbehov" }
        assertEquals(Fagområde.Sykepenger.verdi, utbetalingsbehov.first().detaljer().getValue("fagområde"))
    }

    @Test
    fun `tre utbetalinger`() {
        val tidslinje = tidslinjeOf(
            16.AP, 3.NAV, 5.NAV(1200, 50.0), 7.NAV,
            startDato = 1.januar(2020)
        ).betale()

        val første = opprettUtbetaling(tidslinje.kutt(19.januar(2020)))
        val andre = opprettUtbetaling(tidslinje.kutt(24.januar(2020)), tidligere = første)
        val tredje = opprettUtbetaling(tidslinje.kutt(31.januar(2020)), tidligere = andre)

        val inspektør = tredje.inspektør.arbeidsgiverOppdrag.inspektør
        assertEquals(3, inspektør.antallLinjer())
        assertNull(inspektør.refDelytelseId(0))
        assertNull(inspektør.refFagsystemId(0))

        assertEquals(1, inspektør.delytelseId(0))
        assertEquals(2, inspektør.delytelseId(1))
        assertEquals(3, inspektør.delytelseId(2))

        assertEquals(inspektør.delytelseId(0), inspektør.refDelytelseId(1))
        assertEquals(inspektør.delytelseId(1), inspektør.refDelytelseId(2))

        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), inspektør.refFagsystemId(1))
        assertEquals(andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), inspektør.refFagsystemId(2))
    }

    @Test
    fun `overgang fra full til null refusjon`() {
        val tidslinje = tidslinjeOf(
            16.AP,
            17.NAV,
            5.NAV(1200, refusjonsbeløp = 0.0),
            9.NAV,
            1.ARB,
            4.NAV(1200, refusjonsbeløp = 0.0)
        ).betale()

        val første = opprettUtbetaling(tidslinje.kutt(26.januar))
        val andre = opprettUtbetaling(tidslinje.kutt(31.januar), tidligere = første)
        val tredje = opprettUtbetaling(tidslinje.kutt(7.februar), tidligere = andre)
        val fjerde = opprettUtbetaling(tidslinje.kutt(14.februar), tidligere = tredje)
        val femte = opprettUtbetaling(tidslinje.kutt(21.februar), tidligere = fjerde)

        assertEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(første.inspektør.korrelasjonsId, andre.inspektør.korrelasjonsId)
        assertEquals(andre.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), tredje.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(andre.inspektør.korrelasjonsId, tredje.inspektør.korrelasjonsId)
        assertEquals(tredje.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), fjerde.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(tredje.inspektør.korrelasjonsId, fjerde.inspektør.korrelasjonsId)
        assertEquals(fjerde.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), femte.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId())
        assertEquals(fjerde.inspektør.korrelasjonsId, femte.inspektør.korrelasjonsId)

        assertNotEquals(første.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), første.inspektør.personOppdrag.fagsystemId())

        assertEquals(første.inspektør.personOppdrag.fagsystemId(), andre.inspektør.personOppdrag.fagsystemId())
        assertEquals(andre.inspektør.personOppdrag.fagsystemId(), tredje.inspektør.personOppdrag.fagsystemId())
        assertEquals(tredje.inspektør.personOppdrag.fagsystemId(), fjerde.inspektør.personOppdrag.fagsystemId())
        assertEquals(fjerde.inspektør.personOppdrag.fagsystemId(), femte.inspektør.personOppdrag.fagsystemId())

        assertEquals(0, første.inspektør.personOppdrag.inspektør.antallLinjer())
        assertEquals(0, andre.inspektør.personOppdrag.inspektør.antallLinjer())
        assertEquals(1, tredje.inspektør.personOppdrag.inspektør.antallLinjer())
        assertEquals(1, fjerde.inspektør.personOppdrag.inspektør.antallLinjer())
        assertEquals(2, femte.inspektør.personOppdrag.inspektør.antallLinjer())
    }

    @Test
    fun `utbetalingHendelse som treffer på brukeroppdraget`() {
        val utbetaling = opprettGodkjentUtbetaling()
        kvittèr(utbetaling, utbetaling.inspektør.personOppdrag.fagsystemId())
        assertEquals(UTBETALT, utbetaling.inspektør.tilstand)
    }

    @Test
    fun `serialiserer avstemmingsnøkkel som null når den ikke er satt`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000)).betale()
        val utbetaling = opprettUbetaltUtbetaling(tidslinje)
        assertNull(utbetaling.inspektør.avstemmingsnøkkel)
    }

    @Test
    fun `simulering som er relevant for personoppdrag`() {
        val utbetalingId = UUID.randomUUID()
        val simulering = opprettSimulering("1", Fagområde.Sykepenger, utbetalingId)
        assertTrue(simulering.erRelevantForUtbetaling(utbetalingId))
        assertFalse(simulering.erRelevantForUtbetaling(UUID.randomUUID()))
        assertTrue(simulering.erSimulert(Fagområde.Sykepenger, "1"))
        assertFalse(simulering.erSimulert(Fagområde.Sykepenger, "2"))
        assertFalse(simulering.erSimulert(Fagområde.SykepengerRefusjon, "1"))
    }

    @Test
    fun `simulering som er relevant for arbeidsgiveroppdrag`() {
        val utbetalingId = UUID.randomUUID()
        val simulering = opprettSimulering("1", Fagområde.SykepengerRefusjon, utbetalingId)
        assertTrue(simulering.erRelevantForUtbetaling(utbetalingId))
        assertFalse(simulering.erRelevantForUtbetaling(UUID.randomUUID()))
        assertTrue(simulering.erSimulert(Fagområde.SykepengerRefusjon, "1"))
        assertFalse(simulering.erSimulert(Fagområde.SykepengerRefusjon, "2"))
        assertFalse(simulering.erSimulert(Fagområde.Sykepenger, "1"))
    }

    @Test
    fun `simulerer ingen refusjon`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 0)).betale()
        val utbetaling = opprettUbetaltUtbetaling(tidslinje)
        val simulering = opprettSimulering(
            utbetaling.inspektør.personOppdrag.fagsystemId(), Fagområde.Sykepenger, utbetaling.inspektør.utbetalingId, SimuleringResultatDto(
                totalbeløp = 1000,
                perioder = emptyList()
            )
        )
        utbetaling.håndter(simulering)
        assertNotNull(utbetaling.inspektør.personOppdrag.inspektør.simuleringsResultat())
        assertNull(utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.simuleringsResultat())
    }

    @Test
    fun `simulerer full refusjon`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 1000)).betale()
        val utbetaling = opprettUbetaltUtbetaling(tidslinje)
        val simulering = opprettSimulering(
            utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), Fagområde.SykepengerRefusjon, utbetaling.inspektør.utbetalingId, SimuleringResultatDto(
                totalbeløp = 1000,
                perioder = emptyList()
            )
        )
        utbetaling.håndter(simulering)
        assertNotNull(utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.simuleringsResultat())
        assertNull(utbetaling.inspektør.personOppdrag.inspektør.simuleringsResultat())
    }

    @Test
    fun `simulerer delvis refusjon`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV(dekningsgrunnlag = 1000, refusjonsbeløp = 500)).betale()
        val utbetaling = opprettUbetaltUtbetaling(tidslinje)

        val simuleringArbeidsgiver = opprettSimulering(
            utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(), Fagområde.SykepengerRefusjon, utbetaling.inspektør.utbetalingId, SimuleringResultatDto(
                totalbeløp = 500,
                perioder = emptyList()
            )
        )
        val simuleringPerson = opprettSimulering(
            utbetaling.inspektør.personOppdrag.fagsystemId(), Fagområde.Sykepenger, utbetaling.inspektør.utbetalingId, SimuleringResultatDto(
                totalbeløp = 500,
                perioder = emptyList()
            )
        )
        utbetaling.håndter(simuleringArbeidsgiver)
        utbetaling.håndter(simuleringPerson)

        assertNotNull(utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.simuleringsResultat())
        assertNotNull(utbetaling.inspektør.personOppdrag.inspektør.simuleringsResultat())
    }

    private fun opprettSimulering(fagsystemId: String, fagområde: Fagområde, utbetalingId: UUID, simuleringResultat: SimuleringResultatDto? = null) =
        Simulering(
            meldingsreferanseId = UUID.randomUUID(),
            vedtaksperiodeId = "1",
            aktørId = "aktørId",
            fødselsnummer = "fødselsnummer",
            orgnummer = "orgnummer",
            fagsystemId = fagsystemId,
            fagområde = fagområde.verdi,
            simuleringOK = true,
            melding = "melding",
            simuleringResultat = simuleringResultat,
            utbetalingId = utbetalingId
        )

    private fun Utbetalingstidslinje.betale() = beregnUtbetalinger(this)
    private fun beregnUtbetalinger(tidslinje: Utbetalingstidslinje) = Utbetalingstidslinje.betale(listOf(tidslinje)).single()

    private fun opprettGodkjentUtbetaling(
        tidslinje: Utbetalingstidslinje,
        periode: Periode = tidslinje.periode(),
        fødselsnummer: String = UNG_PERSON_FNR_2018,
        orgnummer: String = ORGNUMMER,
        aktivitetslogg: Aktivitetslogg = this.aktivitetslogg
    ) = opprettUbetaltUtbetaling(tidslinje, null, periode, fødselsnummer, orgnummer, aktivitetslogg)
        .also { godkjenn(it) }
    private fun opprettGodkjentUtbetaling() =
        opprettGodkjentUtbetaling(beregnUtbetalinger(tidslinjeOf(16.AP, 5.NAV(3000))))

    private fun opprettUbetaltUtbetaling(
        tidslinje: Utbetalingstidslinje,
        tidligere: Utbetaling? = null,
        periode: Periode = tidslinje.periode(),
        fødselsnummer: String = UNG_PERSON_FNR_2018,
        orgnummer: String = ORGNUMMER,
        aktivitetslogg: Aktivitetslogg = this.aktivitetslogg
    ) = Utbetaling.lagUtbetaling(
        tidligere?.let { listOf(tidligere) } ?: emptyList(),
        fødselsnummer,
        orgnummer,
        tidslinje,
        periode,
        aktivitetslogg,
        LocalDate.MAX,
        100,
        148
    ).first.also { it.opprett(aktivitetslogg) }

    private fun opprettUtbetaling(
        tidslinje: Utbetalingstidslinje,
        tidligere: Utbetaling? = null,
        periode: Periode = tidslinje.periode(),
        fødselsnummer: String = UNG_PERSON_FNR_2018,
        orgnummer: String = ORGNUMMER,
        aktivitetslogg: Aktivitetslogg = this.aktivitetslogg
    ) = opprettUbetaltUtbetaling(tidslinje, tidligere, periode, fødselsnummer, orgnummer, aktivitetslogg).also { utbetaling ->
        godkjenn(utbetaling)
        listOf(utbetaling.inspektør.arbeidsgiverOppdrag, utbetaling.inspektør.personOppdrag)
            .filter { it.harUtbetalinger() }
            .map { it.fagsystemId() }
            .onEach { kvittèr(utbetaling, it) }
    }

    private fun kvittèr(
        utbetaling: Utbetaling,
        fagsystemId: String = utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(),
        status: Oppdragstatus = AKSEPTERT,
        utbetalingmottaker: Utbetaling = utbetaling
    ): UtbetalingHendelse {
        val hendelsen = UtbetalingHendelse(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = "ignore",
            fødselsnummer = UNG_PERSON_FNR_2018,
            orgnummer = ORGNUMMER,
            fagsystemId = fagsystemId,
            utbetalingId = "${utbetaling.inspektør.utbetalingId}",
            status = status,
            melding = "hei",
            avstemmingsnøkkel = 123456L,
            overføringstidspunkt = LocalDateTime.now()
        )
        utbetalingmottaker.håndter(hendelsen)
        return hendelsen
    }

    private fun godkjenn(utbetaling: Utbetaling, utbetalingGodkjent: Boolean = true) =
        Utbetalingsgodkjenning(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = "ignore",
            fødselsnummer = "ignore",
            organisasjonsnummer = "ignore",
            utbetalingId = utbetaling.inspektør.utbetalingId,
            vedtaksperiodeId = "ignore",
            saksbehandler = "Z999999",
            saksbehandlerEpost = "mille.mellomleder@nav.no",
            utbetalingGodkjent = utbetalingGodkjent,
            godkjenttidspunkt = LocalDateTime.now(),
            automatiskBehandling = false,
        ).also {
            utbetaling.håndter(it)
        }

    private fun annuller(utbetaling: Utbetaling, utbetalingId: UUID = utbetaling.inspektør.utbetalingId) =
        utbetaling.annuller(
            hendelse = AnnullerUtbetaling(UUID.randomUUID(), "aktør", "fnr", "orgnr", null, utbetalingId, "Z123456", "tbd@nav.no", LocalDateTime.now()),
            alleUtbetalinger = listOf(utbetaling)
        )?.also {
            it.opprett(aktivitetslogg)
        }

    private fun IAktivitetslogg.sisteBehov(type: Behovtype) =
        behov().last { it.type == type }

    private fun IAktivitetslogg.harBehov(behov: Behovtype) =
        this.behov().any { it.type == behov }
}

private val frø = LocalDate.of(2018, 1, 1)
val Int.januar get() = frø.withMonth(1).withDayOfMonth(this)
val Int.februar get() = frø.withMonth(2).withDayOfMonth(this)
val Int.mars get() = frø.withMonth(3).withDayOfMonth(this)
val Int.desember get() = frø.withMonth(12).withDayOfMonth(this)