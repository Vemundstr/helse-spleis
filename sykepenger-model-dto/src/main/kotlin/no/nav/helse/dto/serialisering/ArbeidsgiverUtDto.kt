package no.nav.helse.dto.serialisering

import java.util.UUID
import no.nav.helse.dto.InntektshistorikkDto
import no.nav.helse.dto.RefusjonshistorikkDto
import no.nav.helse.dto.SykdomshistorikkDto
import no.nav.helse.dto.SykmeldingsperioderDto

data class ArbeidsgiverUtDto(
    val id: UUID,
    val organisasjonsnummer: String,
    val inntektshistorikk: InntektshistorikkDto,
    val sykdomshistorikk: SykdomshistorikkDto,
    val sykmeldingsperioder: SykmeldingsperioderDto,
    val vedtaksperioder: List<VedtaksperiodeUtDto>,
    val forkastede: List<ForkastetVedtaksperiodeUtDto>,
    val utbetalinger: List<UtbetalingUtDto>,
    val feriepengeutbetalinger: List<FeriepengeUtDto>,
    val refusjonshistorikk: RefusjonshistorikkDto
)