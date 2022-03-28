package no.nav.helse.spleis.meldinger.model

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import no.nav.helse.hendelser.Arbeidsavklaringspenger
import no.nav.helse.hendelser.Dagpenger
import no.nav.helse.hendelser.Dødsinfo
import no.nav.helse.hendelser.Foreldrepermisjon
import no.nav.helse.hendelser.Institusjonsopphold
import no.nav.helse.hendelser.Institusjonsopphold.Institusjonsoppholdsperiode
import no.nav.helse.hendelser.Omsorgspenger
import no.nav.helse.hendelser.Opplæringspenger
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Pleiepenger
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Behovtype.Foreldrepenger
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Behovtype.Sykepengehistorikk
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asOptionalLocalDate
import no.nav.helse.spleis.IHendelseMediator

// Understands a JSON message representing an Ytelserbehov
internal class YtelserMessage(packet: JsonMessage) : BehovMessage(packet) {

    private val vedtaksperiodeId = packet["vedtaksperiodeId"].asText()
    private val organisasjonsnummer = packet["organisasjonsnummer"].asText()
    private val aktørId = packet["aktørId"].asText()
    private val arbeidsavklaringspenger: List<Pair<LocalDate, LocalDate>>
    private val ugyldigeArbeidsavklaringspengeperioder: List<Pair<LocalDate, LocalDate>>
    private val dagpenger: List<Pair<LocalDate, LocalDate>>
    private val ugyldigeDagpengeperioder: List<Pair<LocalDate, LocalDate>>

    private val aktivitetslogg = Aktivitetslogg()
    private val utbetalingshistorikk = packet["@løsning.${Sykepengehistorikk.name}"].takeIf(JsonNode::isArray)?.let {
        UtbetalingshistorikkMessage(packet)
            .utbetalingshistorikk(aktivitetslogg)
    }

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

    private val omsorgspenger =
        Omsorgspenger(packet["@løsning.${Behovtype.Omsorgspenger.name}"].map(::asPeriode), aktivitetslogg)

    private val opplæringspenger =
        Opplæringspenger(packet["@løsning.${Behovtype.Opplæringspenger.name}"].map(::asPeriode), aktivitetslogg)

    private val institusjonsopphold = Institusjonsopphold(packet["@løsning.${Behovtype.Institusjonsopphold.name}"].map {
        Institusjonsoppholdsperiode(
            it.path("startdato").asLocalDate(),
            it.path("faktiskSluttdato").asOptionalLocalDate()
        )
    }, aktivitetslogg)

    private val dødsinfo =
        Dødsinfo(packet["@løsning.${Behovtype.Dødsinfo.name}"].path("dødsdato").asOptionalLocalDate())

    init {
        packet["@løsning.${Behovtype.Arbeidsavklaringspenger.name}.meldekortperioder"].map(::asDatePair)
            .partition { it.first <= it.second }
            .also {
                arbeidsavklaringspenger = it.first
                ugyldigeArbeidsavklaringspengeperioder = it.second
            }
        packet["@løsning.${Behovtype.Dagpenger.name}.meldekortperioder"]
            .map(::asDatePair)
            .partition { it.first <= it.second }
            .also {
                dagpenger = it.first
                ugyldigeDagpengeperioder = it.second
            }
    }


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
            omsorgspenger = omsorgspenger,
            opplæringspenger = opplæringspenger,
            institusjonsopphold = institusjonsopphold,
            dødsinfo = dødsinfo,
            arbeidsavklaringspenger = Arbeidsavklaringspenger(arbeidsavklaringspenger.map {
                Periode(
                    it.first,
                    it.second
                )
            }),
            dagpenger = Dagpenger(dagpenger.map {
                Periode(
                    it.first,
                    it.second
                )
            }),
            aktivitetslogg = aktivitetslogg
        ).also {
            if (ugyldigeArbeidsavklaringspengeperioder.isNotEmpty()) it.warn("Arena inneholdt en eller flere AAP-perioder med ugyldig fom/tom")
            if (ugyldigeDagpengeperioder.isNotEmpty()) it.warn("Arena inneholdt en eller flere Dagpengeperioder med ugyldig fom/tom")
        }

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        mediator.behandle(this, ytelser, context)
    }

    private fun asDatePair(jsonNode: JsonNode) =
        jsonNode.path("fom").asLocalDate() to jsonNode.path("tom").asLocalDate()
}
