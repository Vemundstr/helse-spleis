package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

internal class V36BonkersNavnPåForkastedePerioder : JsonMigration(version = 36) {
    override val description: String = "Retter serialisering av et pair"

    override fun doMigration(jsonNode: ObjectNode) {
        for (arbeidsgiver in jsonNode["arbeidsgivere"]) {
            endreNavnPåKeys(arbeidsgiver["forkastede"])
        }
    }

    private fun endreNavnPåKeys(perioder: JsonNode) =
        perioder.map { periode ->
            (periode as ObjectNode).apply {
                replace("vedtaksperiode", periode["first"])
                remove("first")
                replace("årsak", periode["second"])
                remove("second")
            }
        }
}
