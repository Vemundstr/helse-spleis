package no.nav.helse.utbetalingslinjer

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.Alder.Companion.alder
import no.nav.helse.dsl.ArbeidsgiverHendelsefabrikk
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.februar
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.hendelser.til
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.hendelser.utbetaling.Utbetalingsgodkjenning
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.Dokumentsporing
import no.nav.helse.person.Person
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.Yrkesaktivitet.Arbeidstaker
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.somPersonidentifikator
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.SykdomshistorikkHendelse.Hendelseskilde
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.testhelpers.AP
import no.nav.helse.testhelpers.ARB
import no.nav.helse.testhelpers.FRI
import no.nav.helse.testhelpers.NAV
import no.nav.helse.testhelpers.tidslinjeOf
import no.nav.helse.utbetalingstidslinje.MaksimumUtbetalingFilter
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import no.nav.helse.økonomi.Økonomi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class LagUtbetalingForRevurderingTest {

    private lateinit var maskinellJurist: MaskinellJurist
    private lateinit var aktivitetslogg: Aktivitetslogg

    private companion object {
        private const val AKTØRID = "42"
        private val FNR = "12029240045".somPersonidentifikator()
        private val fødselsdato = 12.februar(1992)
        private const val ORGNUMMER = "123456789"
        private val SØKNAD = UUID.randomUUID()
        private val hendelsefabrikk = ArbeidsgiverHendelsefabrikk(
            aktørId = AKTØRID,
            personidentifikator = FNR,
            organisasjonsnummer = ORGNUMMER
        )
    }

    @BeforeEach
    fun beforeEach() {
        maskinellJurist = MaskinellJurist()
        aktivitetslogg = Aktivitetslogg()
    }

    @Test
    fun `enkel periode med én utbetaling`() {
        val perioder = mutableListOf<Pair<Utbetaling, Vedtaksperiode>>()
        val utbetaling1 = utbetaling(tidslinjeOf(16.AP, 15.NAV), utbetalt = true) to vedtaksperiode()
        perioder.add(utbetaling1)
        assertEquals(1, perioder.sistePeriodeForUtbetalinger().size)
        assertEquals(utbetaling1.second, perioder.sistePeriodeForUtbetalinger().first())
    }

    @Test
    fun `to perioder med forlengelse`() {
        val perioder = mutableListOf<Pair<Utbetaling, Vedtaksperiode>>()
        val utbetaling1 = utbetaling(tidslinjeOf(16.AP, 15.NAV), utbetalt = true) to vedtaksperiode()
        perioder.add(utbetaling1)
        val utbetaling2 =
            utbetaling(tidslinjeOf(16.AP, 15.NAV, 28.NAV), tidligere = utbetaling1.first) to vedtaksperiode(februar)
        perioder.add(utbetaling2)
        assertEquals(1, perioder.sistePeriodeForUtbetalinger().size)
        assertEquals(utbetaling2.second, perioder.sistePeriodeForUtbetalinger().first())
    }

    @Test
    fun `to perioder med agp-gap`() {
        val perioder = mutableListOf<Pair<Utbetaling, Vedtaksperiode>>()
        val utbetaling1 = utbetaling(tidslinjeOf(16.AP, 15.NAV), utbetalt = true) to vedtaksperiode()
        perioder.add(utbetaling1)
        val utbetaling2 =
            utbetaling(tidslinjeOf(16.AP, 15.NAV, 28.ARB, 16.AP, 15.NAV), tidligere = utbetaling1.first) to vedtaksperiode(mars)
        perioder.add(utbetaling2)
        assertEquals(2, perioder.sistePeriodeForUtbetalinger().size)
        assertEquals(utbetaling1.second, perioder.sistePeriodeForUtbetalinger()[0])
        assertEquals(utbetaling2.second, perioder.sistePeriodeForUtbetalinger()[1])
    }

    //usikkert om sistePeriodeForUtbetalinger bør ta høyde for flere skjæringstidspunkter innenfor samme agp
    @Test
    fun `to perioder med mindre enn 16 dager gap`() {
        val perioder = mutableListOf<Pair<Utbetaling, Vedtaksperiode>>()
        val utbetaling1 = utbetaling(tidslinjeOf(16.AP, 15.NAV), utbetalt = true) to vedtaksperiode()
        perioder.add(utbetaling1)
        val utbetaling2 =
            utbetaling(tidslinjeOf(16.AP, 15.NAV, 1.ARB, 27.NAV), tidligere = utbetaling1.first) to vedtaksperiode(2.februar til 28.februar)
        perioder.add(utbetaling2)
        assertEquals(1, perioder.sistePeriodeForUtbetalinger().size)
        assertEquals(utbetaling2.second, perioder.sistePeriodeForUtbetalinger()[0])
    }

    @Test
    fun `to forskjellige utbetalinger med samme skjæringstidspunkt (ping-pong)`() {
        val perioder = mutableListOf<Pair<Utbetaling, Vedtaksperiode>>()
        val utbetaling1 = utbetaling(tidslinjeOf(16.AP, 15.NAV), utbetalt = true) to vedtaksperiode()
        val utbetaling2 = utbetaling(tidslinjeOf(16.AP, 15.NAV), utbetalt = true) to vedtaksperiode()
        perioder.add(utbetaling1)
        perioder.add(utbetaling2)
        assertEquals(2, perioder.sistePeriodeForUtbetalinger().size)
        assertEquals(januar, utbetaling1.first.inspektør.periode)
        assertEquals(januar, utbetaling2.first.inspektør.periode)
        assertEquals(utbetaling1.second, perioder.sistePeriodeForUtbetalinger()[0])
        assertEquals(utbetaling2.second, perioder.sistePeriodeForUtbetalinger()[1])
    }

    @Test
    fun `lag revurdering`() {
        val utbetaling = utbetaling(tidslinjeOf(16.AP, 15.NAV), utbetalt = true)
        val revurdering = revurdering(tidslinjeOf(16.AP, 1.FRI, 14.NAV), utbetaling, 31.januar)
        assertEquals(utbetaling.inspektør.korrelasjonsId, revurdering.inspektør.korrelasjonsId)
        assertEquals(utbetaling.inspektør.arbeidsgiverOppdrag.fagsystemId(), revurdering.inspektør.arbeidsgiverOppdrag.fagsystemId())
        revurdering.inspektør.arbeidsgiverOppdrag.also {
            assertEquals(2, it.size)
            assertEquals(17.januar, it[0].inspektør.datoStatusFom)
            assertEquals(1, it[0].inspektør.delytelseId)
            assertEquals(null, it[0].inspektør.refDelytelseId)
            assertEquals(null, it[0].inspektør.refFagsystemId)
            assertEquals(Endringskode.ENDR, it[0].inspektør.endringskode)
            assertEquals(17.januar til 31.januar, it[0].periode)

            assertEquals(null, it[1].inspektør.datoStatusFom)
            assertEquals(2, it[1].inspektør.delytelseId)
            assertEquals(utbetaling.inspektør.arbeidsgiverOppdrag[0].inspektør.delytelseId, it[1].inspektør.refDelytelseId)
            assertEquals(it.fagsystemId(), it[1].inspektør.refFagsystemId)
            assertEquals(Endringskode.NY, it[1].inspektør.endringskode)
            assertEquals(18.januar til 31.januar, it[1].periode)
        }
        revurdering.assertDiff(-1200)
    }

    @Test
    fun `lag revurdering begrenset til kuttdato`() {
        val utbetaling = utbetaling(tidslinjeOf(16.AP, 15.NAV, 16.NAV), utbetalt = true)
        val revurdering = revurdering(tidslinjeOf(16.AP, 1.FRI, 14.NAV, 1.FRI, 15.NAV), utbetaling, 31.januar)
        assertEquals(utbetaling.inspektør.korrelasjonsId, revurdering.inspektør.korrelasjonsId)
        revurdering.assertDiff(-1200) // Trekker tilbake 1 dag
        revurdering.inspektør.utbetalingstidslinje.inspektør.also {
            assertEquals(1, it.fridagTeller)
            assertEquals(10, it.navDagTeller)
        }

        revurdering.inspektør.arbeidsgiverOppdrag.also {
            assertEquals(2, it.size)
            assertEquals(17.januar, it[0].inspektør.datoStatusFom)
            assertEquals(1, it[0].inspektør.delytelseId)
            assertEquals(null, it[0].inspektør.refDelytelseId)
            assertEquals(null, it[0].inspektør.refFagsystemId)
            assertEquals(Endringskode.ENDR, it[0].inspektør.endringskode)
            assertEquals(17.januar til 16.februar, it[0].periode)

            assertEquals(null, it[1].inspektør.datoStatusFom)
            assertEquals(2, it[1].inspektør.delytelseId)
            assertEquals(it[0].inspektør.delytelseId, it[1].inspektør.refDelytelseId)
            assertEquals(it.fagsystemId(), it[1].inspektør.refFagsystemId)
            assertEquals(Endringskode.NY, it[1].inspektør.endringskode)
            assertEquals(18.januar til 16.februar, it[1].periode)
        }
    }

    @Test
    fun `lag revurdering begrenset til kuttdato og deretter ny revurdering`() {
        val utbetaling = utbetaling(tidslinjeOf(16.AP, 15.NAV, 16.NAV), utbetalt = true)
        val revurdering = revurdering(
            tidslinjeOf(16.AP, 1.FRI, 14.NAV, 1.FRI, 15.NAV),
            utbetaling,
            31.januar,
            utbetalt = true
        )
        val revurdering2 = revurdering(tidslinjeOf(16.AP, 1.FRI, 14.NAV, 1.ARB, 15.NAV), revurdering, 16.februar)
        assertEquals(utbetaling.inspektør.korrelasjonsId, revurdering.inspektør.korrelasjonsId)
        assertEquals(revurdering.inspektør.korrelasjonsId, revurdering2.inspektør.korrelasjonsId)
        revurdering2.assertDiff(-1200)

        revurdering.inspektør.utbetalingstidslinje.inspektør.also {
            assertEquals(1, it.fridagTeller)
            assertEquals(0, it.arbeidsdagTeller)
            assertEquals(10, it.navDagTeller)
        }
        revurdering2.inspektør.utbetalingstidslinje.inspektør.also {
            assertEquals(1, it.fridagTeller)
            assertEquals(1, it.arbeidsdagTeller)
            assertEquals(21, it.navDagTeller)
        }

        revurdering2.inspektør.arbeidsgiverOppdrag.also {
            assertEquals(2, it.size)
            assertEquals(null, it[0].inspektør.datoStatusFom)
            assertEquals(2, it[0].inspektør.delytelseId)
            assertEquals(null, it[0].inspektør.refDelytelseId)
            assertEquals(null, it[0].inspektør.refFagsystemId)
            assertEquals(Endringskode.ENDR, it[0].inspektør.endringskode)
            assertEquals(18.januar til 31.januar, it[0].periode)

            assertEquals(null, it[1].inspektør.datoStatusFom)
            assertEquals(3, it[1].inspektør.delytelseId)
            assertEquals(it[0].inspektør.delytelseId, it[1].inspektør.refDelytelseId)
            assertEquals(it.fagsystemId(), it[1].inspektør.refFagsystemId)
            assertEquals(Endringskode.NY, it[1].inspektør.endringskode)
            assertEquals(2.februar til 16.februar, it[1].periode)
        }
    }

    @Test
    fun `revurderingen kan strekkes forbi utbetalingen`() {
        val utbetaling = utbetaling(tidslinjeOf(16.AP, 15.NAV), utbetalt = true)
        val revurdering = revurdering(tidslinjeOf(16.AP, 20.NAV), utbetaling, 5.februar)
        revurdering.inspektør.utbetalingstidslinje.inspektør.apply {
            assertEquals(1.januar, førstedag.dato)
            assertEquals(5.februar, sistedag.dato)
        }

        revurdering.inspektør.arbeidsgiverOppdrag.also {
            assertEquals(1, it.size)
            assertEquals(null, it[0].inspektør.datoStatusFom)
            assertEquals(1, it[0].inspektør.delytelseId)
            assertEquals(null, it[0].inspektør.refDelytelseId)
            assertEquals(null, it[0].inspektør.refFagsystemId)
            assertEquals(Endringskode.ENDR, it[0].inspektør.endringskode)
            assertEquals(17.januar til 5.februar, it[0].periode)
        }
    }

    @Test
    fun `revurderingen strekkes forbi utbetalingen, men kuttes`() {
        val utbetaling = utbetaling(tidslinjeOf(16.AP, 15.NAV), utbetalt = true)
        val revurdering = revurdering(tidslinjeOf(16.AP, 20.NAV), utbetaling, 31.januar)
        revurdering.inspektør.utbetalingstidslinje.inspektør.apply {
            assertEquals(1.januar, førstedag.dato)
            assertEquals(31.januar, sistedag.dato)
        }
    }

    private fun utbetaling(
        tidslinje: Utbetalingstidslinje,
        tidligere: Utbetaling? = null,
        sisteDato: LocalDate = tidslinje.periode().endInclusive,
        fødselsnummer: String = FNR.toString(),
        orgnummer: String = ORGNUMMER,
        aktivitetslogg: Aktivitetslogg = this.aktivitetslogg,
        utbetalt: Boolean = false,
        type: Utbetalingtype = Utbetalingtype.UTBETALING,
    ): Utbetaling {
        val betaltTidslinje = beregnUtbetalinger(tidslinje)
        return Utbetaling.lagUtbetaling(
            tidligere?.let { listOf(tidligere) } ?: emptyList(),
            fødselsnummer,
            orgnummer,
            betaltTidslinje,
            sisteDato.somPeriode(),
            arbeidsgiverperiode = emptyList(),
            aktivitetslogg,
            LocalDate.MAX,
            100,
            148,
            type
        ).first.also { utbetaling ->
            utbetaling.opprett(aktivitetslogg)
            if (utbetalt) {
                godkjenn(utbetaling)
                listOf(utbetaling.inspektør.arbeidsgiverOppdrag, utbetaling.inspektør.personOppdrag)
                    .filter { it.harUtbetalinger() }
                    .map { it.fagsystemId() }
                    .onEach { kvittèr(utbetaling, it) }
            }
        }
    }

    private fun revurdering(
        tidslinje: Utbetalingstidslinje,
        tidligere: Utbetaling? = null,
        sisteDato: LocalDate,
        fødselsnummer: String = FNR.toString(),
        orgnummer: String = ORGNUMMER,
        aktivitetslogg: Aktivitetslogg = this.aktivitetslogg,
        utbetalt: Boolean = false,
    ) = utbetaling(tidslinje, tidligere, sisteDato, fødselsnummer, orgnummer, aktivitetslogg, utbetalt, Utbetalingtype.REVURDERING)

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

    private fun kvittèr(
        utbetaling: Utbetaling,
        fagsystemId: String = utbetaling.inspektør.arbeidsgiverOppdrag.inspektør.fagsystemId(),
        status: Oppdragstatus = Oppdragstatus.AKSEPTERT
    ) {
        utbetaling.håndter(
            UtbetalingHendelse(
                meldingsreferanseId = UUID.randomUUID(),
                aktørId = "ignore",
                fødselsnummer = FNR.toString(),
                orgnummer = ORGNUMMER,
                fagsystemId = fagsystemId,
                utbetalingId = "${utbetaling.inspektør.utbetalingId}",
                status = status,
                melding = "hei",
                avstemmingsnøkkel = 123456L,
                overføringstidspunkt = LocalDateTime.now()
            )
        )
    }

    private fun beregnUtbetalinger(tidslinje: Utbetalingstidslinje) =
        MaksimumUtbetalingFilter().betal(listOf(tidslinje), tidslinje.periode(), aktivitetslogg, MaskinellJurist()).single()

    private fun vedtaksperiode(periode: Periode = januar, organisasjonsnummer: String = ORGNUMMER): Vedtaksperiode {
        val søknad = søknad(periode)
        val sykdomstidslinje = periode.associateWith {
            Dag.Sykedag(
                it,
                økonomi = Økonomi.sykdomsgrad(100.prosent),
                kilde = Hendelseskilde("Søknad", SØKNAD, LocalDateTime.now())
            )
        }
        return Vedtaksperiode(
            person = person(),
            arbeidsgiver = arbeidsgiver(organisasjonsnummer = organisasjonsnummer),
            søknad = søknad,
            aktørId = AKTØRID,
            fødselsnummer = FNR.toString(),
            organisasjonsnummer = organisasjonsnummer,
            sykdomstidslinje = Sykdomstidslinje(sykdomstidslinje),
            dokumentsporing = Dokumentsporing.søknad(SØKNAD),
            sykmeldingsperiode = periode,
            jurist = maskinellJurist
        )
    }

    private fun person() = Person(AKTØRID, FNR, fødselsdato.alder, maskinellJurist)
    private fun arbeidsgiver(organisasjonsnummer: String) = Arbeidsgiver(person(), Arbeidstaker(organisasjonsnummer), maskinellJurist)
    private fun søknad(periode: Periode): Søknad {
        val søknadsperiode = Søknad.Søknadsperiode.Sykdom(periode.start, periode.endInclusive, 100.prosent)
        return hendelsefabrikk.lagSøknad(
            id = SØKNAD,
            perioder = arrayOf(søknadsperiode)
        )
    }

    private fun Utbetaling.assertDiff(diff: Int) {
        assertEquals(diff, inspektør.nettobeløp)
    }
}

private fun List<Pair<Utbetaling, Vedtaksperiode>>.sistePeriodeForUtbetalinger(): List<Vedtaksperiode> {
    return fold(mutableMapOf<UUID, MutableList<Vedtaksperiode>>()) { acc, pair ->
        val (utbetaling, vedtaksperiode) = pair
        acc.getOrPut(utbetaling.inspektør.korrelasjonsId) { mutableListOf() }.add(vedtaksperiode)
        acc
    }.map { it.value.maxOf { periode -> periode } }
}