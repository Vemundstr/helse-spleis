package no.nav.helse.spleis.mediator.db

import com.github.navikt.tbd_libs.test_support.TestDataSource
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.LocalDateTime
import java.util.UUID
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import no.nav.helse.Personidentifikator
import no.nav.helse.spleis.Meldingsporing
import no.nav.helse.spleis.db.HendelseRepository
import no.nav.helse.spleis.mediator.databaseContainer
import no.nav.helse.spleis.meldinger.model.HendelseMessage
import no.nav.helse.spleis.meldinger.model.NySøknadMessage
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val fnr = Personidentifikator("01011012345")
internal class HendelseRepositoryTest {
    private lateinit var dataSource: TestDataSource

    @BeforeEach
    internal fun setup() {
        dataSource = databaseContainer.nyTilkobling()
    }
    @AfterEach
    internal fun tearDown() {
        databaseContainer.droppTilkobling(dataSource)
    }

    @Test
    fun `skal klare å hente ny søknad-hendelse fra db`() {
        val hendelseId = UUID.randomUUID()
        val (id, navn) = lagre(TestMessages.nySøknad(hendelseId))
        assertEquals(hendelseId, id)
        assertEquals("NY_SØKNAD", navn)
    }

    private fun lagre(hendeleMessage: HendelseMessage): Pair<UUID, String> {
        val repo = HendelseRepository(dataSource.ds)
        val ingenHendelser = repo.hentAlleHendelser(fnr)
        assertEquals(0, ingenHendelser.size)
        repo.lagreMelding(hendeleMessage)
        val hendelser = repo.hentAlleHendelser(fnr)
        assertEquals(1, hendelser.size)
        val (id, info) = hendelser.entries.first().toPair()
        val (navn, _) = info
        return id to navn
    }
}

private object TestMessages {
    private fun String.somPacket(validate: (packet: JsonMessage) -> Unit) =
        JsonMessage(this, MessageProblems(this), SimpleMeterRegistry()).also { packet ->
            validate(packet)
        }

    fun nySøknad(id: UUID): NySøknadMessage {
        val now = LocalDateTime.now()

        @Language("JSON")
        val json = """
        {
            "@id": "foo",
            "@event_name": "ny_soknad",
            "@opprettet": "$now",
            "fnr": "bar",
            "sykmeldingSkrevet": "$now",
            "fom": "2020-01-01",
            "tom": "2020-01-31",
            "arbeidsgiver": {
                "orgnummer": "orgnummer"
            },
            "soknadsperioder": []
        }
        """

        val packet = json.somPacket { packet ->
            packet.requireKey("@id")
            packet.requireKey("@event_name")
            packet.requireKey("@opprettet")
            packet.requireKey("sykmeldingSkrevet")
            packet.requireKey("fom")
            packet.requireKey("tom")
            packet.requireKey("fnr")
            packet.requireKey("arbeidsgiver")
            packet.requireKey("arbeidsgiver.orgnummer")
            packet.requireKey("soknadsperioder")
        }

        return NySøknadMessage(packet, Meldingsporing(id, fnr.toString()))
    }
}