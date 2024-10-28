package no.nav.helse.spleis

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import no.nav.helse.hendelser.PersonHendelse
import no.nav.helse.person.aktivitetslogg.Aktivitet
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import org.slf4j.Logger

internal class BehovMediator(private val sikkerLogg: Logger) {
    internal fun håndter(context: MessageContext, hendelse: PersonHendelse, aktivitetslogg: IAktivitetslogg) {
        aktivitetslogg.kontekster().forEach {
            if (!it.harFunksjonelleFeilEllerVerre()) {
                håndter(context, hendelse, it.behov())
            }
        }
    }

    private fun håndter(context: MessageContext, hendelse: PersonHendelse, behov: List<Aktivitet.Behov>) {
        behov
            .groupBy { it.kontekst() }
            .grupperBehovTilDetaljer()
            .forEach { (kontekst, behovMap) ->
                mutableMapOf<String, Any>()
                    .apply {
                        putAll(kontekst)
                        putAll(behovMap)
                    }
                    .let { JsonMessage.newNeed(behovMap.keys, it) }
                    .also {
                        sikkerLogg.info("sender behov for {}:\n{}", behovMap.keys, it.toJson())
                        context.publish(hendelse.behandlingsporing.fødselsnummer, it.toJson())
                    }
            }
    }

    private fun Map<Map<String, String>, List<Aktivitet.Behov>>.grupperBehovTilDetaljer() =
        mapValues { (kontekst, behovliste) ->
            behovliste
                .groupBy({ it.type.name }, { it.detaljer() })
                .ikkeTillatUnikeDetaljerPåSammeBehov(kontekst, behovliste)
        }

    private fun <K: Any> Map<K, List<Map<String, Any?>>>.ikkeTillatUnikeDetaljerPåSammeBehov(kontekst: Map<String, String>, behovliste: List<Aktivitet.Behov>) =
        mapValues { (_, detaljerList) ->
            // tillater duplikate detaljer-maps, så lenge de er like
            detaljerList
                .distinct()
                .also { detaljer ->
                    require(detaljer.size == 1) {
                        sikkerLogg.error("Forsøkte å sende duplikate behov på kontekst ${kontekst.entries.joinToString { "${it.key}=${it.value}" }}")
                        "Kan ikke produsere samme behov på samme kontekst med ulike detaljer. Forsøkte å be om ${behovliste.joinToString { it.type.name }}"
                    }
                }
                .single()
        }
}
