package no.nav.helse.spleis.meldinger.model

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.util.UUID
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.asOptionalLocalDate
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.somPersonidentifikator
import no.nav.helse.spleis.IHendelseMediator
import no.nav.helse.spleis.Personopplysninger
import no.nav.helse.spleis.meldinger.model.InntektsmeldingMessage.Fødselsnummer.Companion.tilFødselsnummer
import no.nav.helse.økonomi.Inntekt.Companion.månedlig

// Understands a JSON message representing an Inntektsmelding
internal open class InntektsmeldingMessage(packet: JsonMessage) : HendelseMessage(packet) {
    override val fødselsnummer = packet["arbeidstakerFnr"].asText()
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
    private val aktørId = packet["arbeidstakerAktorId"].asText()
    private val fødselsdato = packet["fødselsdato"].asOptionalLocalDate() ?: tilFødselsnummer(fødselsnummer).fødselsdato
    private val dødsdato = packet["dødsdato"].asOptionalLocalDate()
    protected val mottatt = packet["mottattDato"].asLocalDateTime()
    private val førsteFraværsdag = packet["foersteFravaersdag"].asOptionalLocalDate()
    private val beregnetInntekt = packet["beregnetInntekt"].asDouble()
    private val arbeidsgiverperioder = packet["arbeidsgiverperioder"].map(::asPeriode)
    private val begrunnelseForReduksjonEllerIkkeUtbetalt =
        packet["begrunnelseForReduksjonEllerIkkeUtbetalt"].takeIf(JsonNode::isTextual)?.asText()
    private val harOpphørAvNaturalytelser = packet["opphoerAvNaturalytelser"].size() > 0
    private val harFlereInntektsmeldinger = packet["harFlereInntektsmeldinger"].asBoolean(false)
    private val personopplysninger = Personopplysninger(fødselsnummer.somPersonidentifikator(), aktørId, fødselsdato, dødsdato)
    private val avsendersystem = packet["avsenderSystem"].tilAvsendersystem()
    private val inntektsdato = packet["inntektsdato"].asOptionalLocalDate()

    protected val inntektsmelding
        get() = Inntektsmelding(
            meldingsreferanseId = this.id,
            refusjon = refusjon,
            orgnummer = orgnummer,
            fødselsnummer = fødselsnummer,
            aktørId = aktørId,
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
                "AltinnPortal" -> Inntektsmelding.Avsendersystem.ALTINN
                else -> Inntektsmelding.Avsendersystem.LPS
            }
        }
    }

    @Deprecated("Denne skal fjernes så fort vi ikke trenger å gjøre replay av inntektsmeldinger uten fødselsdato. 23.November 2022 skal vi ikke lengre trenge å gjøre replay av så gamle inntektsmeldinger")
    private class Fødselsnummer private constructor(private val value: String) {
        private val individnummer = value.substring(6, 9).toInt()
        val fødselsdato: LocalDate = LocalDate.of(
            value.substring(4, 6).toInt().toYear(individnummer),
            value.substring(2, 4).toInt(),
            value.substring(0, 2).toInt().toDay()
        )
        override fun toString() = value
        override fun hashCode(): Int = value.hashCode()
        override fun equals(other: Any?) = other is Fødselsnummer && this.value == other.value

        private fun Int.toDay() = if (this > 40) this - 40 else this
        private fun Int.toYear(individnummer: Int): Int {
            return this + when {
                this in (54..99) && individnummer in (500..749) -> 1800
                this in (0..99) && individnummer in (0..499) -> 1900
                this in (40..99) && individnummer in (900..999) -> 1900
                else -> 2000
            }
        }

        companion object {
            fun tilFødselsnummer(fnr: String): Fødselsnummer {
                if (fnr.length == 11 && alleTegnErSiffer(fnr)) return Fødselsnummer(fnr)
                else throw RuntimeException("$fnr er ikke et gyldig fødselsnummer")
            }
            private fun alleTegnErSiffer(string: String) = string.matches(Regex("\\d*"))
        }
    }
}

