package no.nav.helse.spleis.meldinger.model

import no.nav.helse.hendelser.utbetaling.Grunnbeløpsregulering
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.Aktivitet.Behov.Behovtype.Sykepengehistorikk
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.spleis.IHendelseMediator

internal class EtterbetalingMessage(val packet: JsonMessage) : HendelseMessage(packet) {

    private val aktørId = packet["aktørId"].asText()
    override val fødselsnummer: String = packet["fødselsnummer"].asText()
    private val organisasjonsnummer = packet["organisasjonsnummer"].asText()
    private val fagsystemId = packet["fagsystemId"].asText().trim()
    private val gyldighetsdato = packet["gyldighetsdato"].asLocalDate()
    private val utbetalingshistorikkMessage: UtbetalingshistorikkMessage? = packet["@løsning.${Sykepengehistorikk.name}"].takeIf { it.isArray }?.let {
        UtbetalingshistorikkMessage(packet)
    }

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        val aktivitetslogg = Aktivitetslogg()
        mediator.behandle(
            this,
            Grunnbeløpsregulering(
                id,
                aktørId,
                fødselsnummer,
                organisasjonsnummer,
                gyldighetsdato,
                fagsystemId,
                aktivitetslogg
            ),
            context
        )
    }
}
