package no.nav.helse.spleis

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import java.time.LocalDateTime
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.person.aktivitetslogg.AktivitetsloggObserver
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.SpesifikkKontekst
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.aktivitetslogg.Varselkode.Companion.erAvviklet
import org.slf4j.LoggerFactory

internal class DatadelingMediator(
    private val aktivitetslogg: IAktivitetslogg,
    private val meldingsreferanseId: UUID,
    private val fødselsnummer: String,
    private val aktørId: String
): AktivitetsloggObserver {
    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }

    private val aktiviteter = mutableListOf<Map<String, Any>>()
    init {
        aktivitetslogg.register(this)
    }

    private fun aktivitetMap(id: UUID, label: Char, melding: String, kontekster: List<SpesifikkKontekst>, tidsstempel: LocalDateTime) =
        mapOf(
            "id" to id,
            "nivå" to label.toFulltext(),
            "melding" to melding,
            "tidsstempel" to tidsstempel,
            "kontekster" to kontekster.map { it.toMap() }
        )

    override fun aktivitet(id: UUID, label: Char, melding: String, kontekster: List<SpesifikkKontekst>, tidsstempel: LocalDateTime) {
        aktiviteter.add(aktivitetMap(id, label, melding, kontekster, tidsstempel))
    }

    override fun varsel(id: UUID, label: Char, kode: Varselkode?, melding: String, kontekster: List<SpesifikkKontekst>, tidsstempel: LocalDateTime) {
        if (kode?.erAvviklet() == true) {
            sikkerlogg.warn("$kode er ikke avviklet, men i bruk i spleis. Endre?")
        }
        val aktivitetMap = aktivitetMap(id, label, melding, kontekster, tidsstempel).toMutableMap()
        if (kode != null) aktivitetMap["varselkode"] = kode
        aktiviteter.add(aktivitetMap.toMap())
    }

    override fun funksjonellFeil(
        id: UUID,
        label: Char,
        kode: Varselkode,
        melding: String,
        kontekster: List<SpesifikkKontekst>,
        tidsstempel: LocalDateTime
    ) {
        val aktivitetMap = aktivitetMap(id, label, melding, kontekster, tidsstempel).toMutableMap()
        aktivitetMap["varselkode"] = kode
        aktiviteter.add(aktivitetMap.toMap())
    }

    internal fun ferdigstill(context: MessageContext) {
        if (aktiviteter.isEmpty()) return
        sikkerlogg.info(
            "Publiserer aktiviteter som følge av hendelse med {}, {}, {}",
            keyValue("hendelseId", meldingsreferanseId),
            keyValue("fødselsnummer", fødselsnummer),
            keyValue("aktørId", aktørId)
        )
        context.publish(fødselsnummer, aktiviteter.toJson())
    }

    private fun MutableList<Map<String, Any>>.toJson(): String {
        return JsonMessage.newMessage(
            "aktivitetslogg_ny_aktivitet",
            mapOf(
                "fødselsnummer" to fødselsnummer,
                "aktiviteter" to this
            )
        ).toJson()
    }

    private fun Char.toFulltext(): String {
        return when (this) {
            'I' -> "INFO"
            'N' -> "BEHOV"
            'W' -> "VARSEL"
            'E' -> "FUNKSJONELL_FEIL"
            'S' -> "LOGISK_FEIL"
            else -> throw IllegalArgumentException("$this er ikke en støttet aktivitetstype")
        }
    }
}