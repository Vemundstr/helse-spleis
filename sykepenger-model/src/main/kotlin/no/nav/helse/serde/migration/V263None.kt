package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory

internal class V263None : JsonMigration(version = 263) {
    override val description = "[none]"

    override fun doMigration(jsonNode: ObjectNode, meldingerSupplier: MeldingerSupplier) {}
}