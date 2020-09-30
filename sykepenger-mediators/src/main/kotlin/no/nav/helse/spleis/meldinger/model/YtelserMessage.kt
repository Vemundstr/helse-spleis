package no.nav.helse.spleis.meldinger.model

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.hendelser.Foreldrepermisjon
import no.nav.helse.hendelser.Institusjonsopphold
import no.nav.helse.hendelser.Pleiepenger
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Behovtype.Foreldrepenger
import no.nav.helse.spleis.IHendelseMediator
import no.nav.helse.spleis.MessageDelegate

// Understands a JSON message representing an Ytelserbehov
internal class YtelserMessage(packet: MessageDelegate) : BehovMessage(packet) {

    private val vedtaksperiodeId = packet["vedtaksperiodeId"].asText()
    private val organisasjonsnummer = packet["organisasjonsnummer"].asText()
    private val aktørId = packet["aktørId"].asText()

    private val aktivitetslogg = Aktivitetslogg()
    private val utbetalingshistorikk = UtbetalingshistorikkMessage(packet)
        .utbetalingshistorikk(aktivitetslogg)

    private val foreldrepenger = packet["@løsning.${Foreldrepenger.name}.Foreldrepengeytelse"]
        .takeIf(JsonNode::isObject)?.let(::asPeriode)
    private val svangerskapsytelse = packet["@løsning.${Foreldrepenger.name}.Svangerskapsytelse"]
        .takeIf(JsonNode::isObject)?.let(::asPeriode)

    private val foreldrepermisjon = Foreldrepermisjon(
        foreldrepengeytelse = foreldrepenger,
        svangerskapsytelse = svangerskapsytelse,
        aktivitetslogg = aktivitetslogg
    )

    private val pleiepenger =
        Pleiepenger(packet["@løsning.${Behovtype.Pleiepenger.name}"].map(::asPeriode), aktivitetslogg)

    private val institusjonsopphold = Institusjonsopphold(emptyList(), aktivitetslogg)

    private val ytelser
        get() = Ytelser(
            meldingsreferanseId = this.id,
            aktørId = aktørId,
            fødselsnummer = fødselsnummer,
            organisasjonsnummer = organisasjonsnummer,
            vedtaksperiodeId = vedtaksperiodeId,
            utbetalingshistorikk = utbetalingshistorikk,
            foreldrepermisjon = foreldrepermisjon,
            pleiepenger = pleiepenger,
            institusjonsopphold = institusjonsopphold,
            aktivitetslogg = aktivitetslogg
        )

    override fun behandle(mediator: IHendelseMediator) {
        mediator.behandle(this, ytelser)
    }
}
