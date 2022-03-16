package no.nav.helse.spleis.meldinger

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDateTime
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Behovtype.Sykepengehistorikk
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.spleis.IMessageMediator
import no.nav.helse.spleis.meldinger.model.UtbetalingshistorikkMessage

internal class UtbetalingshistorikkRiver(
    rapidsConnection: RapidsConnection,
    messageMediator: IMessageMediator
) : ArbeidsgiverBehovRiver(rapidsConnection, messageMediator) {
    override val behov = listOf(Sykepengehistorikk)
    override val riverName = "Utbetalingshistorikk"

    override fun validate(message: JsonMessage) {
        message.demand("@behov") { require(it.size() == behov.size) }
        message.demand("@besvart") { require(it.asLocalDateTime() > LocalDateTime.now().minusHours(1)) }
        message.requireKey("vedtaksperiodeId", "tilstand")
        message.rejectKey("fagsystemId")
        validerSykepengehistorikk(message)
    }

    override fun createMessage(packet: JsonMessage) = UtbetalingshistorikkMessage(packet)

    internal companion object {
        fun validerSykepengehistorikk(message: JsonMessage) {
            message.requireArray("@løsning.${Sykepengehistorikk.name}") {
                requireKey("statslønn")
                requireArray("inntektsopplysninger") {
                    require("sykepengerFom", JsonNode::asLocalDate)
                    requireKey("inntekt", "orgnummer", "refusjonTilArbeidsgiver")
                }
                requireArray("utbetalteSykeperioder") {
                    interestedIn("fom", JsonNode::asLocalDate)
                    interestedIn("tom", JsonNode::asLocalDate)
                    requireKey("dagsats", "utbetalingsGrad", "orgnummer")
                    requireAny("typeKode", listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "O", "S", ""))
                }
                requireKey("arbeidsKategoriKode")
            }
        }
    }
}
