package no.nav.helse.spleis.e2e

import no.nav.helse.person.PersonObserver
import no.nav.helse.person.TilstandType
import java.util.*

internal class TestObservatør : PersonObserver {
    internal val tilstander = mutableMapOf<UUID, MutableList<TilstandType>>()
    val utbetalteVedtaksperioder = mutableListOf<UUID>()
    val manglendeInntektsmeldingVedtaksperioder = mutableListOf<UUID>()

    override fun vedtaksperiodeEndret(event: PersonObserver.VedtaksperiodeEndretTilstandEvent) {
        tilstander.getOrPut(event.vedtaksperiodeId) { mutableListOf(TilstandType.START) }.add(event.gjeldendeTilstand)
        if(event.gjeldendeTilstand == TilstandType.AVSLUTTET) utbetalteVedtaksperioder.add(event.vedtaksperiodeId)
    }

    override fun manglerInntektsmelding(event: PersonObserver.ManglendeInntektsmeldingEvent) {
        manglendeInntektsmeldingVedtaksperioder.add(event.vedtaksperiodeId)
    }
}
