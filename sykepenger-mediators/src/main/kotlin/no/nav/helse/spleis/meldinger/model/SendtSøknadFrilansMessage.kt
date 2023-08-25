package no.nav.helse.spleis.meldinger.model

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.person.Person
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.somPersonidentifikator
import no.nav.helse.spleis.IHendelseMediator
import no.nav.helse.spleis.Personopplysninger

internal class SendtSøknadFrilansMessage(private val packet: JsonMessage, private val builder: SendtSøknadBuilder = SendtSøknadBuilder()) : SøknadMessage(packet, builder, Person.Frilans) {
    override fun _behandle(mediator: IHendelseMediator, personopplysninger: Personopplysninger, packet: JsonMessage, context: MessageContext) {
        builder.sendt(packet["sendtNav"].asLocalDateTime())
        SendtSøknadNavMessage.byggSendtSøknad(builder, packet)
        mediator.behandle(personopplysninger, this, builder.build(), context, packet["historiskeFolkeregisteridenter"].map(JsonNode::asText).map(String::somPersonidentifikator).toSet())
    }
}
