package no.nav.helse.spleis.meldinger

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.*
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.spleis.IMessageMediator
import no.nav.helse.spleis.meldinger.model.YtelserMessage

internal class YtelserRiver(
    rapidsConnection: RapidsConnection,
    messageMediator: IMessageMediator
) : ArbeidsgiverBehovRiver(rapidsConnection, messageMediator) {
    override val behov = listOf(
        Foreldrepenger,
        Pleiepenger,
        Omsorgspenger,
        Opplæringspenger,
        Institusjonsopphold,
        Arbeidsavklaringspenger,
        Dagpenger
    )
    override val riverName = "Ytelser"

    override fun validate(message: JsonMessage) {
        message.requireKey("vedtaksperiodeId", "tilstand")
        message.requireKey("@løsning.${Foreldrepenger.name}")
        message.requireKey("@løsning.${Pleiepenger.name}")
        message.requireKey("@løsning.${Omsorgspenger.name}")
        message.requireKey("@løsning.${Opplæringspenger.name}")
        message.requireKey("@løsning.${Institusjonsopphold.name}")
        message.interestedIn("@løsning.${Foreldrepenger.name}.Foreldrepengeytelse")
        message.interestedIn("@løsning.${Foreldrepenger.name}.Svangerskapsytelse")
        message.requireArray("@løsning.${Pleiepenger.name}") {
            interestedIn("fom") { it.asLocalDate() }
            interestedIn("tom") { it.asLocalDate() }
            interestedIn("grad") { it.asInt() }
        }
        message.requireArray("@løsning.${Omsorgspenger.name}") {
            interestedIn("fom") { it.asLocalDate() }
            interestedIn("tom") { it.asLocalDate() }
            interestedIn("grad") { it.asInt() }
        }
        message.requireArray("@løsning.${Opplæringspenger.name}") {
            interestedIn("fom") { it.asLocalDate() }
            interestedIn("tom") { it.asLocalDate() }
            interestedIn("grad") { it.asInt() }
        }
        message.requireArray("@løsning.${Institusjonsopphold.name}") {
            interestedIn("startdato") { it.asLocalDate() }
            interestedIn("faktiskSluttdato") { it.asLocalDate() }
        }
        message.requireArray("@løsning.${Arbeidsavklaringspenger.name}.meldekortperioder") {
            require("fom", JsonNode::asLocalDate)
            require("tom", JsonNode::asLocalDate)
        }
        message.requireArray("@løsning.${Dagpenger.name}.meldekortperioder") {
            require("fom", JsonNode::asLocalDate)
            require("tom", JsonNode::asLocalDate)
        }
    }

    override fun createMessage(packet: JsonMessage) = YtelserMessage(packet)
}
