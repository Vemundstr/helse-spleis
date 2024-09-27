package no.nav.helse.spleis.mediator.etterlevelse

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import no.nav.helse.januar
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.etterlevelse.Tidslinjedag
import no.nav.helse.spleis.SubsumsjonMediator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.UUID
import no.nav.helse.etterlevelse.`§ 8-17 ledd 2`
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.spleis.meldinger.model.MigrateMessage

internal class SubsumsjonsmeldingTest {
    private val fnr = "12029240045"
    private val versjonAvKode = "1.0.0"

    private lateinit var subsumsjonMediator: SubsumsjonMediator
    private lateinit var testRapid: TestRapid

    private lateinit var jurist: MaskinellJurist

    @BeforeEach
    fun beforeEach() {
        jurist = MaskinellJurist()
            .medFødselsnummer(fnr)
            .medOrganisasjonsnummer("123456789")
            .medVedtaksperiode(UUID.randomUUID(), emptyList())
        val eksempelmelding = MigrateMessage(JsonMessage.newMessage("testevent", mapOf(
            "aktørId" to "1",
            "fødselsnummer" to fnr,
        )).also {
            it.requireKey("@event_name")
            it.requireKey("aktørId")
            it.requireKey("fødselsnummer")
        })
        subsumsjonMediator = SubsumsjonMediator(jurist, fnr, eksempelmelding, versjonAvKode)
        testRapid = TestRapid()
    }

    @Test
    fun `en melding på gyldig format`() {
        jurist.logg(`§ 8-17 ledd 2`(listOf(1.januar(2018).somPeriode()), MutableList(31) { Tidslinjedag((it + 1).januar, "NAVDAG", 100) }))
        subsumsjonMediator.ferdigstill(testRapid)
        assertSubsumsjonsmelding(testRapid.inspektør.message(0)["subsumsjon"])
    }

    private val schema by lazy {
        JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V7)
            .getSchema(URI("https://raw.githubusercontent.com/navikt/helse/c53bc453251b7878135f31d5d1070e5406ae4af1/subsumsjon/json-schema-1.0.0.json"))
    }

    private fun assertSubsumsjonsmelding(melding: JsonNode) {
        try {
            assertEquals(emptySet<ValidationMessage>(), schema.validate(melding))
        } catch (_: Exception) {
            LoggerFactory.getLogger(SubsumsjonsmeldingTest::class.java).warn("Kunne ikke kjøre kontrakttest for subsumsjoner. Mangler du internett?")
        }
    }
}
