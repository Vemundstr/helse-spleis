package no.nav.helse.hendelser

import java.time.LocalDate
import java.time.YearMonth
import no.nav.helse.hendelser.ArbeidsgiverInntekt.Companion.antallMåneder
import no.nav.helse.hendelser.ArbeidsgiverInntekt.Companion.harInntektFor
import no.nav.helse.person.IAktivitetslogg
import no.nav.helse.person.Person
import no.nav.helse.person.PersonHendelse
import no.nav.helse.person.Varselkode.RV_IV_3
import org.slf4j.LoggerFactory

class InntektForSykepengegrunnlag(
    private val inntekter: List<ArbeidsgiverInntekt>,
    private val arbeidsforhold: List<Arbeidsforhold>
) {
    private companion object {
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
    }

    internal fun valider(
        aktivitetslogg: IAktivitetslogg,
    ): IAktivitetslogg {
        if (inntekter.antallMåneder() > 3L) aktivitetslogg.funksjonellFeil("Forventer maks 3 inntektsmåneder")
        if (finnerFrilansinntektDeSiste3Månedene())
            aktivitetslogg.funksjonellFeil(RV_IV_3)
        return aktivitetslogg
    }

    private fun finnerFrilansinntektDeSiste3Månedene() =
        arbeidsforhold.any { arbeidsforhold ->
            arbeidsforhold.månedligeArbeidsforhold.any {
                it.erFrilanser && inntekter.harInntektFor(arbeidsforhold.orgnummer, it.yearMonth)
            }
        }

    internal fun loggInteressantFrilanserInformasjon(skjæringstidspunkt: LocalDate) {
        if (finnerFrilansinntektDenSisteMåneden(skjæringstidspunkt)) sikkerLogg.info("Fant frilanserinntekt på en arbeidsgiver den siste måneden")
    }

    internal fun finnerFrilansinntektDenSisteMåneden(skjæringstidspunkt: LocalDate): Boolean {
        val månedFørSkjæringstidspunkt = YearMonth.from(skjæringstidspunkt.minusMonths(1))
        return arbeidsforhold.any { arbeidsforhold ->
            arbeidsforhold.månedligeArbeidsforhold.any {
                it.erFrilanser && inntekter.harInntektFor(arbeidsforhold.orgnummer, månedFørSkjæringstidspunkt)
            }
        }
    }

    internal fun lagreOmregnetÅrsinntekter(person: Person, skjæringstidspunkt: LocalDate, hendelse: PersonHendelse) =
        ArbeidsgiverInntekt.lagreOmregnetÅrsinntekter(inntekter, person, skjæringstidspunkt, hendelse)

    class Arbeidsforhold(
        val orgnummer: String,
        val månedligeArbeidsforhold: List<MånedligArbeidsforhold>
    )

    class MånedligArbeidsforhold(
        val yearMonth: YearMonth,
        val erFrilanser: Boolean
    )
}



