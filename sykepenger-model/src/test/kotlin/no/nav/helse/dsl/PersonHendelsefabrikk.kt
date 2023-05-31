package no.nav.helse.dsl

import java.time.LocalDate
import java.time.Year
import java.util.UUID
import no.nav.helse.Personidentifikator
import no.nav.helse.dsl.OverstyrtArbeidsgiveropplysning.Companion.tilOverstyrt
import no.nav.helse.dsl.OverstyrtArbeidsgiveropplysning.Companion.tilSkjønnsmessigFastsatt
import no.nav.helse.hendelser.Dødsmelding
import no.nav.helse.hendelser.OverstyrArbeidsforhold
import no.nav.helse.hendelser.OverstyrArbeidsgiveropplysninger
import no.nav.helse.hendelser.PersonPåminnelse
import no.nav.helse.hendelser.SkjønnsmessigFastsettelse
import no.nav.helse.hendelser.UtbetalingshistorikkForFeriepenger

internal class PersonHendelsefabrikk(
    private val aktørId: String,
    private val personidentifikator: Personidentifikator
) {
    internal fun lagDødsmelding(dødsdato: LocalDate) =
        Dødsmelding(
            meldingsreferanseId = UUID.randomUUID(),
            fødselsnummer = personidentifikator.toString(),
            aktørId = aktørId,
            dødsdato = dødsdato
        )
    internal fun lagOverstyrArbeidsforhold(skjæringstidspunkt: LocalDate, vararg overstyrteArbeidsforhold: OverstyrArbeidsforhold.ArbeidsforholdOverstyrt) =
        OverstyrArbeidsforhold(
            meldingsreferanseId = UUID.randomUUID(),
            fødselsnummer = personidentifikator.toString(),
            aktørId = aktørId,
            skjæringstidspunkt = skjæringstidspunkt,
            overstyrteArbeidsforhold = overstyrteArbeidsforhold.toList()
        )
    internal fun lagPåminnelse() =
        PersonPåminnelse(
            meldingsreferanseId = UUID.randomUUID(),
            fødselsnummer = personidentifikator.toString(),
            aktørId = aktørId
        )

    internal fun lagSkjønnsmessigFastsettelse(skjæringstidspunkt: LocalDate, arbeidsgiveropplysninger: List<OverstyrtArbeidsgiveropplysning>, meldingsreferanseId: UUID) =
        SkjønnsmessigFastsettelse(
            meldingsreferanseId = meldingsreferanseId,
            fødselsnummer = personidentifikator.toString(),
            aktørId = aktørId,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiveropplysninger = arbeidsgiveropplysninger.tilSkjønnsmessigFastsatt(meldingsreferanseId, skjæringstidspunkt)
        )

    internal fun lagOverstyrArbeidsgiveropplysninger(skjæringstidspunkt: LocalDate, arbeidsgiveropplysninger: List<OverstyrtArbeidsgiveropplysning>, meldingsreferanseId: UUID) =
        OverstyrArbeidsgiveropplysninger(
            meldingsreferanseId = meldingsreferanseId,
            fødselsnummer = personidentifikator.toString(),
            aktørId = aktørId,
            skjæringstidspunkt = skjæringstidspunkt,
            arbeidsgiveropplysninger = arbeidsgiveropplysninger.tilOverstyrt(meldingsreferanseId, skjæringstidspunkt)
        )

    internal fun lagUtbetalingshistorikkForFeriepenger(opptjeningsår: Year) =
        UtbetalingshistorikkForFeriepenger(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = aktørId,
            fødselsnummer = personidentifikator.toString(),
            utbetalinger = emptyList(),
            feriepengehistorikk = emptyList(),
            arbeidskategorikoder = UtbetalingshistorikkForFeriepenger.Arbeidskategorikoder(emptyList()),
            opptjeningsår = opptjeningsår,
            skalBeregnesManuelt = false
        )
}