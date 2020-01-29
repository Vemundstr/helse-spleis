package no.nav.helse.person

import no.nav.helse.behov.Behov
import no.nav.helse.behov.Behovstype
import no.nav.helse.hendelser.*
import no.nav.helse.sykdomstidslinje.CompositeSykdomstidslinje
import no.nav.helse.testhelpers.februar
import no.nav.helse.testhelpers.januar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.*

internal class YtelserHendelseTest {
    companion object {
        private const val UNG_PERSON_FNR_2018 = "12020052345"
        private const val ORGNR = "12345"
        private val førsteSykedag = 1.januar
        private val sisteSykedag = 31.januar
    }

    private lateinit var person: Person
    private lateinit var personObserver: TestPersonObserver
    private val inspektør get() = TestPersonInspektør(person)
    private lateinit var aktivitetslogger: Aktivitetslogger

    @BeforeEach
    internal fun opprettPerson() {
        personObserver = TestPersonObserver()
        person = Person("12345", UNG_PERSON_FNR_2018)
        person.addObserver(personObserver)
    }

    @Test
    internal fun `ytelser på feil tidspunkt`() {
        person.håndter(ytelser(vedtaksperiodeId = UUID.randomUUID()))
        assertEquals(0, inspektør.vedtaksperiodeTeller)

        person.håndter(nySøknad())
        person.håndter(ytelser())
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertTilstand(TilstandType.MOTTATT_NY_SØKNAD)

        person.håndter(sendtSøknad())
        person.håndter(ytelser())
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertTilstand(TilstandType.MOTTATT_SENDT_SØKNAD)

        person.håndter(inntektsmelding())
        person.håndter(ytelser())
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertTilstand(TilstandType.VILKÅRSPRØVING)
    }

    @Test
    fun `historie eldre enn 6 måneder`() {
        val sisteHistoriskeSykedag = førsteSykedag.minusDays(181)
        håndterYtelser(utbetalinger = listOf(
            ModelSykepengehistorikk.Periode.RefusjonTilArbeidsgiver(sisteHistoriskeSykedag.minusDays(14), sisteHistoriskeSykedag, 1000)
        ))

        assertTilstand(TilstandType.TIL_GODKJENNING)
    }

    @Test
    fun `historie nyere enn 6 måneder`() {
        val sisteHistoriskeSykedag = førsteSykedag.minusDays(180)
        håndterYtelser(utbetalinger = listOf(
            ModelSykepengehistorikk.Periode.RefusjonTilArbeidsgiver(sisteHistoriskeSykedag.minusDays(14), sisteHistoriskeSykedag, 1000)
        ))

        assertTilstand(TilstandType.TIL_INFOTRYGD)
    }

    @Test
    fun `historie nyere enn perioden`() {
        val sisteHistoriskeSykedag = førsteSykedag.plusMonths(2)
        håndterYtelser(utbetalinger = listOf(
            ModelSykepengehistorikk.Periode.RefusjonTilArbeidsgiver(sisteHistoriskeSykedag.minusDays(14), sisteHistoriskeSykedag, 1000)
        ))

        assertTilstand(TilstandType.TIL_INFOTRYGD)
    }

    private fun assertTilstand(expectedTilstand: TilstandType) {
        assertEquals(expectedTilstand, inspektør.tilstand(0)) { "Forventet tilstand $expectedTilstand: $aktivitetslogger" }
    }

    private fun håndterYtelser(utbetalinger: List<ModelSykepengehistorikk.Periode> = emptyList()) {
        person.håndter(nySøknad())
        person.håndter(sendtSøknad())
        person.håndter(inntektsmelding())
        person.håndter(vilkårsgrunnlag())
        person.håndter(ytelser(utbetalinger = utbetalinger))
    }

    private fun ytelser(
        vedtaksperiodeId: UUID = personObserver.vedtaksperiodeId(0),
        utbetalinger: List<ModelSykepengehistorikk.Periode> = emptyList()
    ) = ModelYtelser(
        hendelseId = UUID.randomUUID(),
        aktørId = "aktørId",
        fødselsnummer = UNG_PERSON_FNR_2018,
        organisasjonsnummer = ORGNR,
        vedtaksperiodeId = vedtaksperiodeId.toString(),
        sykepengehistorikk = ModelSykepengehistorikk(
            utbetalinger = utbetalinger,
            inntektshistorikk = emptyList(),
            aktivitetslogger = Aktivitetslogger()
        ),
        foreldrepenger = ModelForeldrepenger(
            foreldrepengeytelse = null,
            svangerskapsytelse = null,
            aktivitetslogger = Aktivitetslogger()
        ),
        rapportertdato = LocalDateTime.now(),
        aktivitetslogger = Aktivitetslogger()
    )

    private fun nySøknad() =
        ModelNySøknad(
            hendelseId = UUID.randomUUID(),
            fnr = UNG_PERSON_FNR_2018,
            aktørId = "aktørId",
            orgnummer = ORGNR,
            rapportertdato = LocalDateTime.now(),
            sykeperioder = listOf(Triple(førsteSykedag, sisteSykedag, 100)),
            originalJson = "{}",
            aktivitetslogger = Aktivitetslogger()
        )

    private fun sendtSøknad() =
        ModelSendtSøknad(
            hendelseId = UUID.randomUUID(),
            fnr = UNG_PERSON_FNR_2018,
            aktørId = "aktørId",
            orgnummer = ORGNR,
            rapportertdato = LocalDateTime.now(),
            perioder = listOf(ModelSendtSøknad.Periode.Sykdom(førsteSykedag, sisteSykedag, 100)),
            originalJson = "{}",
            aktivitetslogger = Aktivitetslogger()
        )

    private fun inntektsmelding() =
        ModelInntektsmelding(
            hendelseId = UUID.randomUUID(),
            refusjon = ModelInntektsmelding.Refusjon(null, 1000.0, emptyList()),
            orgnummer = ORGNR,
            fødselsnummer = UNG_PERSON_FNR_2018,
            aktørId = "aktørId",
            mottattDato = 1.februar.atStartOfDay(),
            førsteFraværsdag = førsteSykedag,
            beregnetInntekt = 1000.0,
            originalJson = "{}",
            arbeidsgiverperioder = listOf(Periode(førsteSykedag, førsteSykedag.plusDays(16))),
            ferieperioder = emptyList(),
            aktivitetslogger = Aktivitetslogger()
        )

    private fun vilkårsgrunnlag() =
        ModelVilkårsgrunnlag(
            hendelseId = UUID.randomUUID(),
            vedtaksperiodeId = personObserver.vedtaksperiodeId(0).toString(),
            aktørId = "aktørId",
            fødselsnummer = UNG_PERSON_FNR_2018,
            orgnummer = ORGNR,
            rapportertDato = LocalDateTime.now(),
            inntektsmåneder = (1..12).map {
                ModelVilkårsgrunnlag.Måned(YearMonth.of(2018, it), listOf(
                    ModelVilkårsgrunnlag.Inntekt(1000.0)
                ))
            },
            erEgenAnsatt = false,
            aktivitetslogger = Aktivitetslogger()
        )

    private inner class TestPersonInspektør(person: Person) : PersonVisitor {
        private var vedtaksperiodeindeks: Int = -1
        private val tilstander = mutableMapOf<Int, TilstandType>()
        private val sykdomstidslinjer = mutableMapOf<Int, CompositeSykdomstidslinje>()

        init {
            person.accept(this)
        }

        override fun preVisitVedtaksperiode(vedtaksperiode: Vedtaksperiode) {
            vedtaksperiodeindeks += 1
            tilstander[vedtaksperiodeindeks] = TilstandType.START
        }

        override fun visitTilstand(tilstand: Vedtaksperiode.Vedtaksperiodetilstand) {
            tilstander[vedtaksperiodeindeks] = tilstand.type
        }

        override fun preVisitComposite(compositeSykdomstidslinje: CompositeSykdomstidslinje) {
            sykdomstidslinjer[vedtaksperiodeindeks] = compositeSykdomstidslinje
        }

        override fun visitPersonAktivitetslogger(aktivitetslogger: Aktivitetslogger) {
            this@YtelserHendelseTest.aktivitetslogger = aktivitetslogger
        }

        internal val vedtaksperiodeTeller get() = tilstander.size

        internal fun tilstand(indeks: Int) = tilstander[indeks]

        internal fun sykdomstidslinje(indeks: Int) = sykdomstidslinjer[indeks] ?:
        throw IllegalAccessException()
    }

    private inner class TestPersonObserver : PersonObserver {
        val vedtaksperiodeIder = mutableSetOf<UUID>()
        private val etterspurteBehov = mutableMapOf<UUID, MutableList<Behov>>()

        fun vedtaksperiodeId(vedtaksperiodeindeks: Int) = vedtaksperiodeIder.elementAt(vedtaksperiodeindeks)

        fun etterspurteBehov(vedtaksperiodeindeks: Int) = etterspurteBehov.getValue(vedtaksperiodeId(vedtaksperiodeindeks)).toList()

        fun <T> etterspurtBehov(vedtaksperiodeindeks: Int, behov: Behovstype, felt: String): T? {
            return personObserver.etterspurteBehov(vedtaksperiodeindeks)
                .first { behov.name in it.behovType() }[felt]
        }

        override fun vedtaksperiodeEndret(event: VedtaksperiodeObserver.StateChangeEvent) {
            vedtaksperiodeIder.add(event.id)
        }

        override fun vedtaksperiodeTrengerLøsning(event: Behov) {
            etterspurteBehov.computeIfAbsent(UUID.fromString(event.vedtaksperiodeId())) { mutableListOf() }
                .add(event)
        }
    }
}
