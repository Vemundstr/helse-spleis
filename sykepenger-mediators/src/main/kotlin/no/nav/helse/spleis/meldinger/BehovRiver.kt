
package no.nav.helse.spleis.meldinger

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.spleis.IMessageMediator

internal abstract class BehovRiver(
    rapidsConnection: RapidsConnection,
    messageMediator: IMessageMediator
) : HendelseRiver(rapidsConnection, messageMediator) {
    override val eventName = "behov"
    protected abstract val behov: List<Aktivitet.Behov.Behovtype>

    init {
        river.validate(::validateBehov)
    }

    private fun validateBehov(packet: JsonMessage) {
        packet.demandValue("@final", true)
        packet.demandAll("@behov", behov.map(Enum<*>::name))
        packet.requireKey("@løsning", "aktørId", "fødselsnummer")
        packet.require("@besvart", JsonNode::asLocalDateTime)
    }
}
