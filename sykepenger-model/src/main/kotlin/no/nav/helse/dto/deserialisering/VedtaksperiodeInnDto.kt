package no.nav.helse.dto.deserialisering

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.dto.GenerasjonerDto
import no.nav.helse.dto.VedtaksperiodetilstandDto

data class VedtaksperiodeInnDto(
    val id: UUID,
    var tilstand: VedtaksperiodetilstandDto,
    val generasjoner: GenerasjonerDto,
    val opprettet: LocalDateTime,
    var oppdatert: LocalDateTime
)