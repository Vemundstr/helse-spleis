package no.nav.helse.person.refusjon

import java.util.UUID
import no.nav.helse.hendelser.Avsender

data class Kilde(
    val meldingsreferanseId: UUID,
    val avsender: Avsender
)