package no.nav.helse.spleis.db

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.Personidentifikator
import no.nav.helse.serde.migration.Json
import no.nav.helse.serde.migration.Navn
import no.nav.helse.spleis.PostgresProbe
import no.nav.helse.spleis.db.HendelseRepository.Meldingstype.*
import no.nav.helse.spleis.meldinger.model.AnmodningOmForkastingMessage
import no.nav.helse.spleis.meldinger.model.AnnulleringMessage
import no.nav.helse.spleis.meldinger.model.AvbruttSøknadMessage
import no.nav.helse.spleis.meldinger.model.AvstemmingMessage
import no.nav.helse.spleis.meldinger.model.DødsmeldingMessage
import no.nav.helse.spleis.meldinger.model.GrunnbeløpsreguleringMessage
import no.nav.helse.spleis.meldinger.model.ForkastSykmeldingsperioderMessage
import no.nav.helse.spleis.meldinger.model.GjenopplivVilkårsgrunnlagMessage
import no.nav.helse.spleis.meldinger.model.HendelseMessage
import no.nav.helse.spleis.meldinger.model.IdentOpphørtMessage
import no.nav.helse.spleis.meldinger.model.InfotrygdendringMessage
import no.nav.helse.spleis.meldinger.model.InntektsmeldingMessage
import no.nav.helse.spleis.meldinger.model.InntektsmeldingReplayUtførtMessage
import no.nav.helse.spleis.meldinger.model.InntektsmeldingerReplayMessage
import no.nav.helse.spleis.meldinger.model.MigrateMessage
import no.nav.helse.spleis.meldinger.model.NyArbeidsledigSøknadMessage
import no.nav.helse.spleis.meldinger.model.NyFrilansSøknadMessage
import no.nav.helse.spleis.meldinger.model.NySelvstendigSøknadMessage
import no.nav.helse.spleis.meldinger.model.NySøknadMessage
import no.nav.helse.spleis.meldinger.model.OverstyrArbeidsforholdMessage
import no.nav.helse.spleis.meldinger.model.OverstyrArbeidsgiveropplysningerMessage
import no.nav.helse.spleis.meldinger.model.OverstyrTidslinjeMessage
import no.nav.helse.spleis.meldinger.model.PersonPåminnelseMessage
import no.nav.helse.spleis.meldinger.model.PåminnelseMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadArbeidsgiverMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadArbeidsledigMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadFrilansMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadNavMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadSelvstendigMessage
import no.nav.helse.spleis.meldinger.model.SimuleringMessage
import no.nav.helse.spleis.meldinger.model.SkjønnsmessigFastsettelseMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingpåminnelseMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingsgodkjenningMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingshistorikkEtterInfotrygdendringMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingshistorikkForFeriepengerMessage
import no.nav.helse.spleis.meldinger.model.UtbetalingshistorikkMessage
import no.nav.helse.spleis.meldinger.model.VilkårsgrunnlagMessage
import no.nav.helse.spleis.meldinger.model.YtelserMessage
import org.slf4j.LoggerFactory

internal class HendelseRepository(private val dataSource: DataSource) {

    private companion object {
        private val log = LoggerFactory.getLogger(HendelseRepository::class.java)
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    fun lagreMelding(melding: HendelseMessage) {
        melding.lagreMelding(this)
    }

    internal fun finnInntektsmeldinger(fnr: Personidentifikator): List<JsonNode> =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "SELECT data FROM melding WHERE fnr = ? AND melding_type = 'INNTEKTSMELDING' ORDER BY lest_dato ASC",
                    fnr.toLong()
                )
                    .map { objectMapper.readTree(it.string("data")) }
                    .asList
            )
        }

    internal fun lagreMelding(melding: HendelseMessage, personidentifikator: Personidentifikator, meldingId: UUID, json: String) {
        val meldingtype = meldingstype(melding) ?: return
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "INSERT INTO melding (fnr, melding_id, melding_type, data) VALUES (?, ?, ?, (to_json(?::json))) ON CONFLICT(melding_id) DO NOTHING",
                    personidentifikator.toLong(),
                    meldingId.toString(),
                    meldingtype.name,
                    json
                ).asExecute
            )
        }.also {
            PostgresProbe.hendelseSkrevetTilDb()
        }
    }

    fun markerSomBehandlet(meldingId: UUID) = sessionOf(dataSource).use { session ->
        session.run(queryOf("UPDATE melding SET behandlet_tidspunkt=now() WHERE melding_id = ? AND behandlet_tidspunkt IS NULL",
            meldingId.toString()
        ).asUpdate)
    }

    fun erBehandlet(meldingId: UUID) = sessionOf(dataSource).use { session ->
        session.run(
            queryOf("SELECT behandlet_tidspunkt FROM melding WHERE melding_id = ?", meldingId.toString())
                .map { it.localDateTimeOrNull("behandlet_tidspunkt") }.asSingle
        ) != null
    }

    private fun meldingstype(melding: HendelseMessage) = when (melding) {
        is NySøknadMessage -> NY_SØKNAD
        is NyFrilansSøknadMessage -> NY_SØKNAD_FRILANS
        is NySelvstendigSøknadMessage -> NY_SØKNAD_SELVSTENDIG
        is NyArbeidsledigSøknadMessage -> NY_SØKNAD_ARBEIDSLEDIG
        is SendtSøknadArbeidsgiverMessage -> SENDT_SØKNAD_ARBEIDSGIVER
        is SendtSøknadNavMessage -> SENDT_SØKNAD_NAV
        is SendtSøknadFrilansMessage -> SENDT_SØKNAD_FRILANS
        is SendtSøknadSelvstendigMessage -> SENDT_SØKNAD_SELVSTENDIG
        is SendtSøknadArbeidsledigMessage -> SENDT_SØKNAD_ARBEIDSLEDIG
        is InntektsmeldingMessage -> INNTEKTSMELDING
        is UtbetalingpåminnelseMessage -> UTBETALINGPÅMINNELSE
        is YtelserMessage -> YTELSER
        is VilkårsgrunnlagMessage -> VILKÅRSGRUNNLAG
        is SimuleringMessage -> SIMULERING
        is UtbetalingsgodkjenningMessage -> UTBETALINGSGODKJENNING
        is UtbetalingMessage -> UTBETALING
        is AnnulleringMessage -> KANSELLER_UTBETALING
        is GrunnbeløpsreguleringMessage -> GRUNNBELØPSREGULERING
        is OverstyrTidslinjeMessage -> OVERSTYRTIDSLINJE
        is OverstyrArbeidsforholdMessage -> OVERSTYRARBEIDSFORHOLD
        is OverstyrArbeidsgiveropplysningerMessage -> OVERSTYRARBEIDSGIVEROPPLYSNINGER
        is UtbetalingshistorikkForFeriepengerMessage -> UTBETALINGSHISTORIKK_FOR_FERIEPENGER
        is UtbetalingshistorikkEtterInfotrygdendringMessage -> UTBETALINGSHISTORIKK_ETTER_IT_ENDRING
        is DødsmeldingMessage -> DØDSMELDING
        is ForkastSykmeldingsperioderMessage -> FORKAST_SYKMELDINGSPERIODER
        is AnmodningOmForkastingMessage -> ANMODNING_OM_FORKASTING
        is GjenopplivVilkårsgrunnlagMessage -> GJENOPPLIV_VILKÅRSGRUNNLAG
        is IdentOpphørtMessage -> IDENT_OPPHØRT
        is SkjønnsmessigFastsettelseMessage -> SKJØNNSMESSIG_FASTSETTELSE
        is AvbruttSøknadMessage -> AVBRUTT_SØKNAD
        is InntektsmeldingerReplayMessage -> INNTEKTSMELDINGER_REPLAY
        is MigrateMessage,
        is AvstemmingMessage,
        is PersonPåminnelseMessage,
        is PåminnelseMessage,
        is UtbetalingshistorikkMessage,
        is InfotrygdendringMessage,
        is InntektsmeldingReplayUtførtMessage -> null // Disse trenger vi ikke å lagre
        else -> null.also { log.warn("ukjent meldingstype ${melding::class.simpleName}: melding lagres ikke") }
    }

    internal fun hentAlleHendelser(personidentifikator: Personidentifikator): Map<UUID, Pair<Navn, Json>> {
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "SELECT melding_id, melding_type, data FROM melding WHERE fnr = ? AND (melding_type = ? OR melding_type = ? OR melding_type = ? OR melding_type = ? OR melding_type = ? OR melding_type = ? OR melding_type = ?)",
                    personidentifikator.toLong(), NY_SØKNAD.name, SENDT_SØKNAD_ARBEIDSGIVER.name, SENDT_SØKNAD_NAV.name, INNTEKTSMELDING.name, OVERSTYRTIDSLINJE.name,
                    OVERSTYRINNTEKT.name, VILKÅRSGRUNNLAG.name
                ).map {
                    UUID.fromString(it.string("melding_id")) to Pair<Navn, Json>(
                        it.string("melding_type"),
                        it.string("data")
                    )
                }.asList).toMap()
        }
    }

    private enum class Meldingstype {
        NY_SØKNAD,
        NY_SØKNAD_FRILANS,
        NY_SØKNAD_SELVSTENDIG,
        NY_SØKNAD_ARBEIDSLEDIG,
        SENDT_SØKNAD_ARBEIDSGIVER,
        SENDT_SØKNAD_NAV,
        SENDT_SØKNAD_FRILANS,
        SENDT_SØKNAD_SELVSTENDIG,
        SENDT_SØKNAD_ARBEIDSLEDIG,
        INNTEKTSMELDING,
        PÅMINNELSE,
        PERSONPÅMINNELSE,
        OVERSTYRTIDSLINJE,
        OVERSTYRINNTEKT,
        OVERSTYRARBEIDSFORHOLD,
        UTBETALINGPÅMINNELSE,
        YTELSER,
        UTBETALINGSGRUNNLAG,
        VILKÅRSGRUNNLAG,
        UTBETALINGSGODKJENNING,
        UTBETALING,
        SIMULERING,
        KANSELLER_UTBETALING,
        GRUNNBELØPSREGULERING,
        UTBETALINGSHISTORIKK_FOR_FERIEPENGER,
        UTBETALINGSHISTORIKK_ETTER_IT_ENDRING,
        DØDSMELDING,
        OVERSTYRARBEIDSGIVEROPPLYSNINGER,
        FORKAST_SYKMELDINGSPERIODER,
        ANMODNING_OM_FORKASTING,
        GJENOPPLIV_VILKÅRSGRUNNLAG,
        IDENT_OPPHØRT,
        SKJØNNSMESSIG_FASTSETTELSE,
        AVBRUTT_SØKNAD,
        INNTEKTSMELDINGER_REPLAY
    }
}
