package no.nav.helse.spleis.meldinger.model

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import no.nav.helse.hendelser.Inntektsmelding
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.asOptionalLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import no.nav.helse.spleis.IHendelseMediator
import no.nav.helse.spleis.Meldingsporing
import no.nav.helse.spleis.Personopplysninger
import no.nav.helse.økonomi.Inntekt.Companion.månedlig

// Understands a JSON message representing an Inntektsmelding
internal open class InntektsmeldingMessage(
    packet: JsonMessage,
    val personopplysninger: Personopplysninger,
    override val meldingsporing: Meldingsporing
) : HendelseMessage(packet) {
    private val refusjon = Inntektsmelding.Refusjon(
        beløp = packet["refusjon.beloepPrMnd"].takeUnless(JsonNode::isMissingOrNull)?.asDouble()?.månedlig,
        opphørsdato = packet["refusjon.opphoersdato"].asOptionalLocalDate(),
        endringerIRefusjon = packet["endringIRefusjoner"].map {
            Inntektsmelding.Refusjon.EndringIRefusjon(
                it.path("beloep").asDouble().månedlig,
                it.path("endringsdato").asLocalDate()
            )
        }
    )
    private val arbeidsforholdId = packet["arbeidsforholdId"].takeIf(JsonNode::isTextual)?.asText()
    private val vedtaksperiodeId = packet["vedtaksperiodeId"].takeIf(JsonNode::isTextual)?.asText()?.let { UUID.fromString(it) }
    private val orgnummer = packet["virksomhetsnummer"].asText()

    protected val mottatt = packet["mottattDato"].asLocalDateTime()
    private val førsteFraværsdag = packet["foersteFravaersdag"].asOptionalLocalDate()
    private val beregnetInntekt = packet["beregnetInntekt"].asDouble()
    private val arbeidsgiverperioder = packet["arbeidsgiverperioder"].map(::asPeriode)
    private val begrunnelseForReduksjonEllerIkkeUtbetalt =
        packet["begrunnelseForReduksjonEllerIkkeUtbetalt"].takeIf(JsonNode::isTextual)?.asText()
    private val harOpphørAvNaturalytelser = packet["opphoerAvNaturalytelser"].size() > 0
    private val harFlereInntektsmeldinger = packet["harFlereInntektsmeldinger"].asBoolean(false)
    private val avsendersystem = packet["avsenderSystem"].tilAvsendersystem()
    private val inntektsdato = packet["inntektsdato"].asOptionalLocalDate()

    protected val inntektsmelding
        get() = Inntektsmelding(
            meldingsreferanseId = meldingsporing.id,
            refusjon = refusjon,
            orgnummer = orgnummer,
            førsteFraværsdag = førsteFraværsdag,
            inntektsdato = inntektsdato,
            beregnetInntekt = beregnetInntekt.månedlig,
            arbeidsgiverperioder = arbeidsgiverperioder,
            arbeidsforholdId = arbeidsforholdId,
            begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
            harOpphørAvNaturalytelser = harOpphørAvNaturalytelser,
            harFlereInntektsmeldinger = harFlereInntektsmeldinger,
            avsendersystem = avsendersystem,
            vedtaksperiodeId = vedtaksperiodeId,
            mottatt = mottatt
        )

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        mediator.behandle(personopplysninger, this, inntektsmelding, context)
    }

    internal companion object {
        internal fun JsonNode.tilAvsendersystem(): Inntektsmelding.Avsendersystem? {
            val navn = path("navn").takeUnless { it.isMissingOrNull() }?.asText() ?: return null
            return when (navn) {
                "NAV_NO" -> Inntektsmelding.Avsendersystem.NAV_NO
                "NAV_NO_SELVBESTEMT" -> Inntektsmelding.Avsendersystem.NAV_NO_SELVBESTEMT
                "AltinnPortal" -> Inntektsmelding.Avsendersystem.ALTINN
                else -> Inntektsmelding.Avsendersystem.LPS
            }
        }
    }
}

