package no.nav.helse.hendelser

import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.dto.AvsenderDto

enum class Avsender {
    SYKMELDT, ARBEIDSGIVER, SAKSBEHANDLER, SYSTEM;

    fun dto() = when (this) {
        SYKMELDT -> AvsenderDto.SYKMELDT
        ARBEIDSGIVER -> AvsenderDto.ARBEIDSGIVER
        SAKSBEHANDLER -> AvsenderDto.SAKSBEHANDLER
        SYSTEM -> AvsenderDto.SYSTEM
    }
    companion object {
        fun gjenopprett(dto: AvsenderDto): Avsender {
            return when (dto) {
                AvsenderDto.ARBEIDSGIVER -> ARBEIDSGIVER
                AvsenderDto.SAKSBEHANDLER -> SAKSBEHANDLER
                AvsenderDto.SYKMELDT -> SYKMELDT
                AvsenderDto.SYSTEM -> SYSTEM
            }
        }
    }
}

sealed interface Hendelse {
    val behandlingsporing: Behandlingsporing
    val metadata: HendelseMetadata

    fun navn(): String
    fun venter(block: () -> Unit) { block() }
}

sealed interface Behandlingsporing {
    val fødselsnummer: String
    val aktørId: String

    data class Person(override val fødselsnummer: String, override val aktørId: String) : Behandlingsporing
    data class Arbeidsgiver(override val fødselsnummer: String, override val aktørId: String, val organisasjonsnummer: String) : Behandlingsporing
}

data class HendelseMetadata(
    val meldingsreferanseId: UUID,
    val avsender: Avsender,

    // tidspunktet meldingen ble registrert (lest inn) av fagsystemet
    val registrert: LocalDateTime,

    // tidspunktet for når meldingen ble sendt inn av avsender.
    // kan være når bruker sendte søknaden sin, eller arbeidsgiver sendte inntektsmelding.
    val innsendt: LocalDateTime,

    // sann hvis et system har sendt meldingen på eget initiativ
    val automatiskBehandling: Boolean
)