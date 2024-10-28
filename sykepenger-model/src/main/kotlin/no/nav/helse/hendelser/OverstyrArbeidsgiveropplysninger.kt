package no.nav.helse.hendelser

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.nesteDag
import no.nav.helse.person.Person
import no.nav.helse.person.PersonObserver
import no.nav.helse.person.PersonObserver.Inntektsopplysningstype.SAKSBEHANDLER
import no.nav.helse.person.beløp.Beløpstidslinje
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.Inntektsgrunnlag
import no.nav.helse.person.refusjon.Refusjonsservitør

class OverstyrArbeidsgiveropplysninger(
    meldingsreferanseId: UUID,
    fødselsnummer: String,
    aktørId: String,
    internal val skjæringstidspunkt: LocalDate,
    private val arbeidsgiveropplysninger: List<ArbeidsgiverInntektsopplysning>,
    private val opprettet: LocalDateTime,
    private val refusjonstidslinjer: Map<String, Pair<Beløpstidslinje, Boolean>>
) : PersonHendelse(meldingsreferanseId, fødselsnummer, aktørId), OverstyrInntektsgrunnlag {
    override fun erRelevant(skjæringstidspunkt: LocalDate) = this.skjæringstidspunkt == skjæringstidspunkt

    override fun innsendt() = opprettet
    override fun avsender() = Avsender.SAKSBEHANDLER

    internal fun overstyr(builder: Inntektsgrunnlag.ArbeidsgiverInntektsopplysningerOverstyringer) {
        arbeidsgiveropplysninger.forEach { builder.leggTilInntekt(it) }
    }

    internal fun arbeidsgiveropplysningerKorrigert(person: Person, orgnummer: String, hendelseId: UUID) {
        if (arbeidsgiveropplysninger.any { it.gjelder(orgnummer) }) {
            person.arbeidsgiveropplysningerKorrigert(
                PersonObserver.ArbeidsgiveropplysningerKorrigertEvent(
                    korrigertInntektsmeldingId = hendelseId,
                    korrigerendeInntektektsopplysningstype = SAKSBEHANDLER,
                    korrigerendeInntektsopplysningId = meldingsreferanseId
                )
            )
        }
    }

    internal fun refusjonsservitør(
        startdatoer: Collection<LocalDate>,
        orgnummer: String,
        eksisterendeRefusjonstidslinje: Beløpstidslinje
    ): Refusjonsservitør? {
        val (refusjonstidslinjeFraOverstyring, strekkbar) = refusjonstidslinjer[orgnummer] ?: return null
        if (refusjonstidslinjeFraOverstyring.isEmpty()) return null
        val refusjonstidslinje =
            if (strekkbar) refusjonstidslinjeFraOverstyring
            else refusjonstidslinjeFraOverstyring + eksisterendeRefusjonstidslinje.fraOgMed(refusjonstidslinjeFraOverstyring.last().dato.nesteDag)
        return Refusjonsservitør.fra(startdatoer = startdatoer, refusjonstidslinje = refusjonstidslinje)
    }
}
