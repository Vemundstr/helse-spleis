package no.nav.helse.dsl

import java.util.UUID
import no.nav.helse.hendelser.Periode.Companion.overlapper
import no.nav.helse.inspectors.inspektør
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.Person
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.TilstandType
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.REVURDERING_FEILET
import no.nav.helse.person.arbeidsgiver
import no.nav.helse.person.inntekt.Sykepengegrunnlag.AvventerFastsettelseEtterSkjønn
import no.nav.helse.person.inntekt.Sykepengegrunnlag.FastsattEtterSkjønn

internal class UgyldigeSituasjonerObservatør(private val person: Person): PersonObserver {

    private val arbeidsgivereMap = mutableMapOf<String, Arbeidsgiver>()
    private val gjeldendeTilstander = mutableMapOf<UUID, TilstandType>()
    private val arbeidsgivere get() = arbeidsgivereMap.values
    private val IM = Inntektsmeldinger()

    init {
        person.addObserver(this)
    }

    override fun vedtaksperiodeEndret(
        event: PersonObserver.VedtaksperiodeEndretEvent
    ) {
        arbeidsgivereMap.getOrPut(event.organisasjonsnummer) { person.arbeidsgiver(event.organisasjonsnummer) }
        gjeldendeTilstander[event.vedtaksperiodeId] = event.gjeldendeTilstand
    }

    override fun behandlingUtført() {
        bekreftIngenUgyldigeSituasjoner()
        IM.behandlingUtført()
    }

    override fun vedtaksperiodeVenter(event: PersonObserver.VedtaksperiodeVenterEvent) {
        if (event.venterPå.venteårsak.hva != "HJELP") return // Om vi venter på noe annet enn hjelp er det OK 👍
        if (event.revurderingFeilet()) return // For tester som ender opp i revurdering feilet er det riktig at vi trenger hjelp 🛟
        if (event.auuVilUtbetales()) return // For tester som ikke lar en AUU gå videre i livet 🛟
        """
        Har du endret/opprettet en vedtaksperiodetilstand uten å vurdre konsekvensene av 'venteårsak'? 
        Eller har du klart å skriv en test vi ikke støtter? 
        ${event.tilstander()}
        $event
        """.let { throw IllegalStateException(it) }
    }

    override fun inntektsmeldingHåndtert(inntektsmeldingId: UUID, vedtaksperiodeId: UUID, organisasjonsnummer: String) = IM.håndtert(inntektsmeldingId, vedtaksperiodeId)
    override fun inntektsmeldingIkkeHåndtert(inntektsmeldingId: UUID, organisasjonsnummer: String) = IM.ikkeHåndtert(inntektsmeldingId)
    override fun inntektsmeldingFørSøknad(event: PersonObserver.InntektsmeldingFørSøknadEvent) = IM.førSøknad(event.inntektsmeldingId)
    override fun vedtaksperiodeForkastet(event: PersonObserver.VedtaksperiodeForkastetEvent) = IM.vedtaksperiodeForkastet(event.vedtaksperiodeId)

    private fun PersonObserver.VedtaksperiodeVenterEvent.revurderingFeilet() = gjeldendeTilstander[venterPå.vedtaksperiodeId] == REVURDERING_FEILET
    private fun PersonObserver.VedtaksperiodeVenterEvent.auuVilUtbetales() =
        vedtaksperiodeId == venterPå.vedtaksperiodeId && gjeldendeTilstander[venterPå.vedtaksperiodeId] == AVSLUTTET_UTEN_UTBETALING && venterPå.venteårsak.hvorfor == "VIL_UTBETALES"
    private fun PersonObserver.VedtaksperiodeVenterEvent.tilstander() = when (vedtaksperiodeId == venterPå.vedtaksperiodeId) {
        true -> "En vedtaksperiode i ${gjeldendeTilstander[vedtaksperiodeId]} trenger hjelp! 😱"
        false -> "En vedtaksperiode i ${gjeldendeTilstander[vedtaksperiodeId]} venter på en annen vedtaksperiode i ${gjeldendeTilstander[venterPå.vedtaksperiodeId]} som trenger hjelp! 😱"
    }

    internal fun bekreftIngenUgyldigeSituasjoner() {
        bekreftIngenOverlappende()
        validerSykdomshistorikk()
        bekreftSykepengegrunnlagtilstand()
        IM.bekreftEntydighåndtering()
    }

    private fun bekreftSykepengegrunnlagtilstand() {
        person.inspektør.vilkårsgrunnlagHistorikk.inspektør.grunnlagsdata().forEach {
            val tilstand = it.inspektør.sykepengegrunnlag.inspektør.tilstand
            val avvik = it.inspektør.sykepengegrunnlag.inspektør.nøyaktigAvviksprosent
            if (avvik > 25) check(tilstand in listOf(AvventerFastsettelseEtterSkjønn, FastsattEtterSkjønn)) {
                "Forventer ikke at sykepengegrunnlaget har tilstand ${tilstand::class.java.simpleName} med $avvik% avvik"
            }
        }
    }

    private fun validerSykdomshistorikk() {
        arbeidsgivere.forEach { arbeidsgiver ->
            val perioderPerHendelse = arbeidsgiver.inspektør.sykdomshistorikk.inspektør.perioderPerHendelse()
            perioderPerHendelse.forEach { (hendelseId, perioder) ->
                check(!perioder.overlapper()) {
                    "Sykdomshistorikk inneholder overlappende perioder fra hendelse $hendelseId"
                }
            }
        }
    }

    private fun bekreftIngenOverlappende() {
        person.inspektør.vedtaksperioder()
            .filterValues { it.size > 1 }
            .forEach { (orgnr, perioder) ->
                var nåværende = perioder.first().inspektør
                perioder.subList(1, perioder.size).forEach { periode ->
                    val inspektør = periode.inspektør
                    check(!inspektør.periode.overlapperMed(nåværende.periode)) {
                        "For Arbeidsgiver $orgnr overlapper Vedtaksperiode ${inspektør.id} (${inspektør.periode}) og Vedtaksperiode ${nåværende.id} (${nåværende.periode}) med hverandre!"
                    }
                    nåværende = inspektør
                }
            }
    }

    private class Inntektsmeldinger {
        private val signaler = mutableListOf<Signal>()

        fun håndtert(inntektmeldingId: UUID, vedtaksperiodeId: UUID) { signaler.add(Signal(inntektmeldingId, Signaltype.HÅNDTERT, vedtaksperiodeId)) }
        fun ikkeHåndtert(inntektmeldingId: UUID) { signaler.add(Signal(inntektmeldingId, Signaltype.IKKE_HÅNDTERT)) }
        fun førSøknad(inntektmeldingId: UUID) { signaler.add(Signal(inntektmeldingId, Signaltype.FØR_SØKNAD)) }
        fun vedtaksperiodeForkastet(vedtaksperiodeId: UUID) { signaler.removeIf { it.vedtaksperiodeId == vedtaksperiodeId } }
        fun behandlingUtført() = signaler.clear()
        fun bekreftEntydighåndtering() {
            val inntektsmeldingerMedFlerSignaler = signaler
                .groupBy { it.inntektsmeldingId }
                .mapValues { (_, values) -> values.toSet() }
                .filterValues { it.size > 1 }
            check(inntektsmeldingerMedFlerSignaler.isEmpty()) {
                "Sendt ut tvetydige signaler for inntektsmeldinger.\n" +
                inntektsmeldingerMedFlerSignaler.map { (key, value) -> " - $key: ${value.map { it.type }}" }.joinToString("\n")
            }
        }

        private data class Signal(
            val inntektsmeldingId: UUID,
            val type: Signaltype,
            val vedtaksperiodeId: UUID? = null) {
            override fun equals(other: Any?): Boolean {
                if (other !is Signal) return false
                return inntektsmeldingId == other.inntektsmeldingId && type == other.type
            }
            override fun hashCode() = inntektsmeldingId.hashCode() + type.hashCode()
        }

        private enum class Signaltype {
            HÅNDTERT,
            IKKE_HÅNDTERT,
            FØR_SØKNAD
        }
    }
}