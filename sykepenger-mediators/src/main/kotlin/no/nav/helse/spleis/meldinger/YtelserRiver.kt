package no.nav.helse.spleis.meldinger

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Behovtype.*
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.spleis.IMessageMediator
import no.nav.helse.spleis.JsonMessageDelegate
import no.nav.helse.spleis.meldinger.model.YtelserMessage

internal class YtelserRiver(
    rapidsConnection: RapidsConnection,
    messageMediator: IMessageMediator
) : BehovRiver(rapidsConnection, messageMediator) {
    override val behov = listOf(Sykepengehistorikk, Foreldrepenger, Pleiepenger)
    override val riverName = "Ytelser"

    override fun validate(packet: JsonMessage) {
        packet.requireKey("@løsning.${Foreldrepenger.name}")
        packet.requireKey("@løsning.${Sykepengehistorikk.name}")
        packet.requireKey("@løsning.${Pleiepenger.name}")
        packet.interestedIn("@løsning.${Foreldrepenger.name}.Foreldrepengeytelse")
        packet.interestedIn("@løsning.${Foreldrepenger.name}.Svangerskapsytelse")
        packet.requireArray("@løsning.${Sykepengehistorikk.name}") {
            requireArray("inntektsopplysninger") {
                require("sykepengerFom", JsonNode::asLocalDate)
                requireKey("inntekt", "orgnummer", "refusjonTilArbeidsgiver")
            }
            requireArray("utbetalteSykeperioder") {
                interestedIn("fom") { it.asLocalDate() }
                interestedIn("tom") { it.asLocalDate() }
                requireAny("typeKode", listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "O", "S", ""))
            }
        }
        packet.requireArray("@løsning.${Pleiepenger.name}") {
            interestedIn("fom") { it.asLocalDate() }
            interestedIn("tom") { it.asLocalDate() }
            interestedIn("grad") { it.asInt() }
        }
    }

    override fun createMessage(packet: JsonMessage) = YtelserMessage(JsonMessageDelegate(packet))
}
