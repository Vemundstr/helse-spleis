package no.nav.helse.serde.api.builders

import no.nav.helse.Toggle
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.ForkastetVedtaksperiode
import no.nav.helse.person.Vedtaksperiode
import no.nav.helse.person.VilkårsgrunnlagHistorikk
import no.nav.helse.serde.api.ArbeidsgiverDTO
import no.nav.helse.serde.api.GhostPeriodeDTO
import no.nav.helse.serde.api.v2.HendelseDTO
import no.nav.helse.serde.api.v2.buildere.GenerasjonerBuilder
import no.nav.helse.serde.api.v2.buildere.IVilkårsgrunnlagHistorikk
import no.nav.helse.somFødselsnummer
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.utbetalingslinjer.Utbetaling
import java.time.LocalDateTime
import java.util.*

internal class ArbeidsgiverBuilder(
    private val arbeidsgiver: Arbeidsgiver,
    vilkårsgrunnlagHistorikk: VilkårsgrunnlagHistorikk,
    private val id: UUID,
    private val organisasjonsnummer: String,
    fødselsnummer: String,
    inntektshistorikkBuilder: InntektshistorikkBuilder
) : BuilderState() {
    private val utbetalingshistorikkBuilder = UtbetalingshistorikkBuilder()
    private val utbetalinger = mutableListOf<Utbetaling>()

    private val gruppeIder = mutableMapOf<Vedtaksperiode, UUID>()
    private val perioderBuilder = VedtaksperioderBuilder(
        arbeidsgiver = arbeidsgiver,
        fødselsnummer = fødselsnummer,
        inntektshistorikkBuilder = inntektshistorikkBuilder,
        gruppeIder = gruppeIder,
        vilkårsgrunnlagHistorikk = vilkårsgrunnlagHistorikk
    )
    private val forkastetPerioderBuilder = VedtaksperioderBuilder(
        arbeidsgiver = arbeidsgiver,
        fødselsnummer = fødselsnummer,
        inntektshistorikkBuilder = inntektshistorikkBuilder,
        gruppeIder = gruppeIder,
        vilkårsgrunnlagHistorikk = vilkårsgrunnlagHistorikk,
        byggerForkastedePerioder = true
    )

    internal fun build(hendelser: List<HendelseDTO>, fødselsnummer: String, vilkårsgrunnlagHistorikk: IVilkårsgrunnlagHistorikk): ArbeidsgiverDTO {
        val utbetalingshistorikk = utbetalingshistorikkBuilder.build()
        val ghostPerioder = arbeidsgiver.ghostPerioder()
        return ArbeidsgiverDTO(
            organisasjonsnummer = organisasjonsnummer,
            id = id,
            vedtaksperioder = perioderBuilder.build(hendelser, utbetalingshistorikk) + forkastetPerioderBuilder.build(hendelser, utbetalingshistorikk).filter { it.tilstand.visesNårForkastet() },
            utbetalingshistorikk = utbetalingshistorikk,
            generasjoner = if (Toggle.SpeilApiV2.enabled) GenerasjonerBuilder(hendelser, fødselsnummer.somFødselsnummer(), vilkårsgrunnlagHistorikk, arbeidsgiver).build() else null,
            ghostPerioder = ghostPerioder?.ghostPerioder?.map {
                GhostPeriodeDTO(
                    fom = it.fom.coerceAtLeast(it.skjæringstidspunkt),
                    tom = it.tom,
                    skjæringstidspunkt = it.skjæringstidspunkt,
                    vilkårsgrunnlagHistorikkInnslagId = ghostPerioder.historikkInnslagId
                )
            } ?: emptyList()
        )
    }

    override fun preVisitPerioder(vedtaksperioder: List<Vedtaksperiode>) {
        pushState(perioderBuilder)
    }

    override fun preVisitForkastedePerioder(vedtaksperioder: List<ForkastetVedtaksperiode>) {
        pushState(forkastetPerioderBuilder)
    }

    override fun preVisitUtbetalinger(utbetalinger: List<Utbetaling>) {
        this.utbetalinger.addAll(utbetalinger)
        pushState(utbetalingshistorikkBuilder)
    }

    override fun preVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) {
        pushState(utbetalingshistorikkBuilder)
    }

    override fun preVisitUtbetalingstidslinjeberegning(
        id: UUID,
        tidsstempel: LocalDateTime,
        organisasjonsnummer: String,
        sykdomshistorikkElementId: UUID,
        inntektshistorikkInnslagId: UUID,
        vilkårsgrunnlagHistorikkInnslagId: UUID
    ) {
        utbetalingshistorikkBuilder.preVisitUtbetalingstidslinjeberegning(
            id,
            tidsstempel,
            organisasjonsnummer,
            sykdomshistorikkElementId,
            inntektshistorikkInnslagId,
            vilkårsgrunnlagHistorikkInnslagId
        )
    }

    override fun postVisitArbeidsgiver(
        arbeidsgiver: Arbeidsgiver,
        id: UUID,
        organisasjonsnummer: String
    ) {
        popState()
    }
}
