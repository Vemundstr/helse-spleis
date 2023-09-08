package no.nav.helse.spleis.mediator

import no.nav.helse.Personidentifikator
import no.nav.helse.hendelser.AnmodningOmForkasting
import no.nav.helse.hendelser.Avstemming
import no.nav.helse.hendelser.Dødsmelding
import no.nav.helse.hendelser.ForkastSykmeldingsperioder
import no.nav.helse.hendelser.GjenopplivVilkårsgrunnlag
import no.nav.helse.hendelser.Grunnbeløpsregulering
import no.nav.helse.hendelser.IdentOpphørt
import no.nav.helse.hendelser.Infotrygdendring
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.InntektsmeldingReplay
import no.nav.helse.hendelser.InntektsmeldingReplayUtført
import no.nav.helse.hendelser.Migrate
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.OverstyrTidslinje
import no.nav.helse.hendelser.PersonPåminnelse
import no.nav.helse.hendelser.Påminnelse
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.SkjønnsmessigFastsettelse
import no.nav.helse.hendelser.Sykmelding
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.hendelser.UtbetalingshistorikkEtterInfotrygdendring
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.Ytelser
import no.nav.helse.hendelser.utbetaling.AnnullerUtbetaling
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.hendelser.utbetaling.Utbetalingpåminnelse
import no.nav.helse.hendelser.utbetaling.Utbetalingsgodkjenning
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.spleis.IHendelseMediator
import no.nav.helse.spleis.Personopplysninger
import no.nav.helse.spleis.meldinger.model.AnmodningOmForkastingMessage
import no.nav.helse.spleis.meldinger.model.AnnulleringMessage
import no.nav.helse.spleis.meldinger.model.AvstemmingMessage
import no.nav.helse.spleis.meldinger.model.DødsmeldingMessage
import no.nav.helse.spleis.meldinger.model.GrunnbeløpsreguleringMessage
import no.nav.helse.spleis.meldinger.model.ForkastSykmeldingsperioderMessage
import no.nav.helse.spleis.meldinger.model.GjenopplivVilkårsgrunnlagMessage
import no.nav.helse.spleis.meldinger.model.HendelseMessage
import no.nav.helse.spleis.meldinger.model.IdentOpphørtMessage
import no.nav.helse.spleis.meldinger.model.InfotrygdendringMessage
import no.nav.helse.spleis.meldinger.model.InntektsmeldingMessage
import no.nav.helse.spleis.meldinger.model.InntektsmeldingReplayMessage
import no.nav.helse.spleis.meldinger.model.InntektsmeldingReplayUtførtMessage
import no.nav.helse.spleis.meldinger.model.MigrateMessage
import no.nav.helse.spleis.meldinger.model.NyFrilansSøknadMessage
import no.nav.helse.spleis.meldinger.model.NySøknadMessage
import no.nav.helse.spleis.meldinger.model.OverstyrArbeidsforholdMessage
import no.nav.helse.spleis.meldinger.model.OverstyrArbeidsgiveropplysningerMessage
import no.nav.helse.spleis.meldinger.model.OverstyrTidslinjeMessage
import no.nav.helse.spleis.meldinger.model.PersonPåminnelseMessage
import no.nav.helse.spleis.meldinger.model.PåminnelseMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadArbeidsgiverMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadFrilansMessage
import no.nav.helse.spleis.meldinger.model.SendtSøknadNavMessage
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

internal class TestHendelseMediator : IHendelseMediator {

    internal var lestNySøknad = false
        private set
    internal var lestNySøknadFrilans = false
        private set
    internal var lestSendtSøknadArbeidsgiver = false
        private set
    internal var lestSendtSøknad = false
        private set
    internal var lestSendtSøknadFrilans = false
        private set
    internal var lestInntektsmelding = false
        private set
    internal var lestInntektsmeldingReplay = false
        private set
    internal var lestDødsmelding = false
        private set
    internal var lestPåminnelse = false
        private set
    internal var lestPersonpåminnelse = false
        private set
    internal var lestAnmodningOmForkasting = false
        private set
    internal var lestutbetalingpåminnelse = false
        private set
    internal var lestUtbetalingshistorikk = false
        private set
    internal var lestUtbetalingshistorikkForFeriepenger = false
        private set
    internal var lestYtelser = false
        private set
    internal var lestVilkårsgrunnlag = false
        private set
    internal var lestUtbetalingsgrunnlag = false
        private set
    internal var lestSimulering = false
        private set
    internal var lestUtbetalingsgodkjenning = false
        private set
    internal var lestUtbetaling = false
        private set
    internal var lestAnnullerUtbetaling = false
        private set
    internal var lestAvstemming = false
        private set
    internal var lestMigrate = false
        private set
    internal var lestOverstyrTidslinje = false
        private set
    internal var lestOverstyrInntekt = false
        private set
    internal var lestOverstyrArbeidsgiveropplysningser = false
        private set
    internal var lestOverstyrArbeidsforhold = false
        private set
    internal var lestReplayHendelser = false
        private set
    internal var lestGrunnbeløpsregulering = false
        private set
    internal var lestInfotrygdendring = false
        private set
    internal var utbetalingshistorikkEtterInfotrygdendringMessage = false
        private set
    internal var lestForkastSykmeldingsperioderMessage = false
        private set

    fun reset() {
        lestNySøknad = false
        lestNySøknadFrilans = false
        lestSendtSøknadArbeidsgiver = false
        lestSendtSøknad = false
        lestSendtSøknadFrilans = true
        lestInntektsmelding = false
        lestPåminnelse = false
        lestPersonpåminnelse = false
        lestutbetalingpåminnelse = false
        lestUtbetalingshistorikk = false
        lestYtelser = false
        lestVilkårsgrunnlag = false
        lestUtbetalingsgrunnlag = false
        lestSimulering = false
        lestUtbetalingsgodkjenning = false
        lestUtbetaling = false
        lestAnnullerUtbetaling = false
        lestAvstemming = false
        lestOverstyrTidslinje = false
        lestGrunnbeløpsregulering = false
        lestInfotrygdendring = false
        utbetalingshistorikkEtterInfotrygdendringMessage = false
        lestAnmodningOmForkasting = false
    }

    override fun behandle(message: HendelseMessage, context: MessageContext) {
        message.behandle(this, context)
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: NySøknadMessage,
        sykmelding: Sykmelding,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        lestNySøknad = true
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: NyFrilansSøknadMessage,
        sykmelding: Sykmelding,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        lestNySøknadFrilans = true
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadFrilansMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        lestSendtSøknadFrilans = true
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadArbeidsgiverMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        lestSendtSøknadArbeidsgiver = true
    }

    override fun behandle(
        personopplysninger: Personopplysninger,
        message: SendtSøknadNavMessage,
        søknad: Søknad,
        context: MessageContext,
        historiskeFolkeregisteridenter: Set<Personidentifikator>
    ) {
        lestSendtSøknad = true
    }

    override fun behandle(personopplysninger: Personopplysninger, message: InntektsmeldingMessage, inntektsmelding: Inntektsmelding, context: MessageContext) {
        lestInntektsmelding = true
    }

    override fun behandle(message: InntektsmeldingReplayMessage, inntektsmelding: InntektsmeldingReplay, context: MessageContext) {
        lestInntektsmeldingReplay = true
    }

    override fun behandle(message: DødsmeldingMessage, dødsmelding: Dødsmelding, context: MessageContext) {
        lestDødsmelding = true
    }

    override fun behandle(
        nyPersonidentifikator: Personidentifikator,
        message: IdentOpphørtMessage,
        identOpphørt: IdentOpphørt,
        nyAktørId: String,
        gamleIdenter: Set<Personidentifikator>,
        context: MessageContext
    ) {}

    override fun behandle(
        message: InntektsmeldingReplayUtførtMessage,
        replayUtført: InntektsmeldingReplayUtført,
        context: MessageContext
    ) {}

    override fun behandle(message: UtbetalingpåminnelseMessage, påminnelse: Utbetalingpåminnelse, context: MessageContext) {
        lestutbetalingpåminnelse = true
    }

    override fun behandle(message: PåminnelseMessage, påminnelse: Påminnelse, context: MessageContext) {
        lestPåminnelse = true
    }

    override fun behandle(message: PersonPåminnelseMessage, påminnelse: PersonPåminnelse, context: MessageContext) {
        lestPersonpåminnelse = true
    }

    override fun behandle(
        message: AnmodningOmForkastingMessage,
        anmodning: AnmodningOmForkasting,
        context: MessageContext
    ) {
        lestAnmodningOmForkasting = true
    }

    override fun behandle(message: UtbetalingshistorikkMessage, utbetalingshistorikk: Utbetalingshistorikk, context: MessageContext) {
        lestUtbetalingshistorikk = true
    }

    override fun behandle(message: UtbetalingshistorikkForFeriepengerMessage, utbetalingshistorikkForFeriepenger: UtbetalingshistorikkForFeriepenger, context: MessageContext) {
        lestUtbetalingshistorikkForFeriepenger = true
    }

    override fun behandle(message: YtelserMessage, ytelser: Ytelser, context: MessageContext) {
        lestYtelser = true
    }

    override fun behandle(message: VilkårsgrunnlagMessage, vilkårsgrunnlag: Vilkårsgrunnlag, context: MessageContext) {
        lestVilkårsgrunnlag = true
    }

    override fun behandle(message: UtbetalingsgodkjenningMessage, utbetalingsgodkjenning: Utbetalingsgodkjenning, context: MessageContext) {
        lestUtbetalingsgodkjenning = true
    }

    override fun behandle(message: UtbetalingMessage, utbetaling: UtbetalingHendelse, context: MessageContext) {
        lestUtbetaling = true
    }

    override fun behandle(message: SimuleringMessage, simulering: Simulering, context: MessageContext) {
        lestSimulering = true
    }

    override fun behandle(message: AnnulleringMessage, annullerUtbetaling: AnnullerUtbetaling, context: MessageContext) {
        lestAnnullerUtbetaling = true
    }

    override fun behandle(message: AvstemmingMessage, avstemming: Avstemming, context: MessageContext) {
        lestAvstemming = true
    }

    override fun behandle(message: MigrateMessage, migrate: Migrate, context: MessageContext) {
        lestMigrate = true
    }

    override fun behandle(message: OverstyrTidslinjeMessage, overstyrTidslinje: OverstyrTidslinje, context: MessageContext) {
        lestOverstyrTidslinje = true
    }

    override fun behandle(message: OverstyrArbeidsgiveropplysningerMessage, overstyrArbeidsgiveropplysninger: OverstyrArbeidsgiveropplysninger, context: MessageContext) {
        lestOverstyrArbeidsgiveropplysningser = true
    }

    override fun behandle(message: OverstyrArbeidsforholdMessage, overstyrArbeidsforhold: OverstyrArbeidsforhold, context: MessageContext) {
        lestOverstyrArbeidsforhold = true
    }

    override fun behandle(message: GrunnbeløpsreguleringMessage, grunnbeløpsregulering: Grunnbeløpsregulering, context: MessageContext) {
        lestGrunnbeløpsregulering = true
    }

    override fun behandle(
        message: InfotrygdendringMessage,
        infotrygdEndring: Infotrygdendring,
        context: MessageContext
    ) {
        lestInfotrygdendring = true
    }

    override fun behandle(
        message: UtbetalingshistorikkEtterInfotrygdendringMessage,
        utbetalingshistorikkEtterInfotrygdendring: UtbetalingshistorikkEtterInfotrygdendring,
        context: MessageContext
    ) {
        utbetalingshistorikkEtterInfotrygdendringMessage = true
    }

    override fun behandle(
        message: ForkastSykmeldingsperioderMessage,
        forkastSykmeldingsperioder: ForkastSykmeldingsperioder,
        context: MessageContext
    ) {
        lestForkastSykmeldingsperioderMessage = true
    }

    override fun behandle(
        message: GjenopplivVilkårsgrunnlagMessage,
        gjenopplivVilkårsgrunnlag: GjenopplivVilkårsgrunnlag,
        context: MessageContext
    ) {}

    override fun behandle(
        message: SkjønnsmessigFastsettelseMessage,
        skjønnsmessigFastsettelse: SkjønnsmessigFastsettelse,
        context: MessageContext
    ) {}
}
