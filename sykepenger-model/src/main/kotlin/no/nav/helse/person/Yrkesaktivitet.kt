package no.nav.helse.person

import java.time.LocalDate
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.hendelser.Sykmelding
import no.nav.helse.hendelser.til
import no.nav.helse.person.Vedtaksperiode.Companion.tilkomneInntekter
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.person.aktivitetslogg.Varselkode
import no.nav.helse.person.inntekt.ArbeidsgiverInntektsopplysning
import no.nav.helse.person.inntekt.Inntektshistorikk
import no.nav.helse.person.inntekt.Refusjonshistorikk
import no.nav.helse.person.inntekt.Refusjonshistorikk.Refusjon.EndringIRefusjon.Companion.refusjonsopplysninger
import no.nav.helse.person.inntekt.SkattSykepengegrunnlag

const val Frilans = "FRILANS"
const val Selvstendig = "SELVSTENDIG"
const val Arbeidsledig = "ARBEIDSLEDIG"

internal sealed interface Yrkesaktivitet {
    companion object {
        fun String.tilYrkesaktivitet() = when (this) {
            Selvstendig -> Selvstendig()
            Frilans -> Frilans()
            Arbeidsledig -> Arbeidsledig()
            else -> Arbeidstaker(this)
        }
    }

    fun avklarSykepengegrunnlag(
        skjæringstidspunkt: LocalDate,
        førsteFraværsdag: LocalDate?,
        inntektshistorikk: Inntektshistorikk,
        skattSykepengegrunnlag: SkattSykepengegrunnlag?,
        refusjonshistorikk: Refusjonshistorikk,
        aktivitetslogg: IAktivitetslogg?
    ): ArbeidsgiverInntektsopplysning? {
        throw NotImplementedError("Støtter ikke å avklare sykepengegrunnlag for ${this.identifikator()}")
    }

    fun tilkomneInntekter(
        aktivitetslogg: IAktivitetslogg,
        vedtaksperioder: List<Vedtaksperiode>
    ): List<ArbeidsgiverInntektsopplysning> {
        throw NotImplementedError("Støtter ikke å finne tilkomne inntekter for ${this.identifikator()}")
    }

    fun identifikator(): String

    fun jurist(other: MaskinellJurist): MaskinellJurist =
        other.medOrganisasjonsnummer(this.toString())
    fun erYrkesaktivitetenIkkeStøttet(hendelse: IAktivitetslogg): Boolean

    fun håndter(sykmelding: Sykmelding, sykmeldingsperioder: Sykmeldingsperioder) {
        sykmeldingsperioder.lagre(sykmelding)
    }

    class Arbeidstaker(private val organisasjonsnummer: String) : Yrkesaktivitet {
        override fun erYrkesaktivitetenIkkeStøttet(hendelse: IAktivitetslogg) = false
        override fun avklarSykepengegrunnlag(
            skjæringstidspunkt: LocalDate,
            førsteFraværsdag: LocalDate?,
            inntektshistorikk: Inntektshistorikk,
            skattSykepengegrunnlag: SkattSykepengegrunnlag?,
            refusjonshistorikk: Refusjonshistorikk,
            aktivitetslogg: IAktivitetslogg?
        ): ArbeidsgiverInntektsopplysning? {
            val inntektsopplysning = inntektshistorikk.avklarSykepengegrunnlag(skjæringstidspunkt, førsteFraværsdag, skattSykepengegrunnlag) ?: return null
            return ArbeidsgiverInntektsopplysning(
                orgnummer = organisasjonsnummer,
                gjelder = skjæringstidspunkt til LocalDate.MAX,
                inntektsopplysning = inntektsopplysning,
                refusjonsopplysninger = refusjonshistorikk.refusjonsopplysninger(skjæringstidspunkt, aktivitetslogg)
            )
        }

        override fun tilkomneInntekter(
            aktivitetslogg: IAktivitetslogg,
            vedtaksperioder: List<Vedtaksperiode>
        ): List<ArbeidsgiverInntektsopplysning> {
            return vedtaksperioder.tilkomneInntekter()
        }

        override fun hashCode(): Int {
            throw NotImplementedError()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Arbeidstaker) return false
            return this.organisasjonsnummer == other.organisasjonsnummer
        }

        override fun identifikator() = organisasjonsnummer
        override fun toString() = identifikator()
    }
    class Frilans : Yrkesaktivitet {
        override fun erYrkesaktivitetenIkkeStøttet(hendelse: IAktivitetslogg): Boolean {
            hendelse.funksjonellFeil(Varselkode.RV_SØ_39)
            return true
        }

        override fun hashCode(): Int {
            throw NotImplementedError()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Frilans
        }

        override fun identifikator() = Frilans
        override fun toString() = identifikator()
    }
    class Selvstendig : Yrkesaktivitet {
        override fun erYrkesaktivitetenIkkeStøttet(hendelse: IAktivitetslogg): Boolean {
            hendelse.funksjonellFeil(Varselkode.RV_SØ_39)
            return true
        }

        override fun hashCode(): Int {
            throw NotImplementedError()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Selvstendig
        }
        override fun identifikator() = Selvstendig
        override fun toString() = identifikator()
    }
    class Arbeidsledig : Yrkesaktivitet {
        override fun erYrkesaktivitetenIkkeStøttet(hendelse: IAktivitetslogg): Boolean {
            hendelse.funksjonellFeil(Varselkode.RV_SØ_39)
            return true
        }

        override fun håndter(sykmelding: Sykmelding, sykmeldingsperioder: Sykmeldingsperioder) {
            sykmelding.info("Lagrer _ikke_ sykmeldingsperiode ${sykmelding.periode()} ettersom det er en sykmelding som arbeidsledig.")
        }

        override fun avklarSykepengegrunnlag(
            skjæringstidspunkt: LocalDate,
            førsteFraværsdag: LocalDate?,
            inntektshistorikk: Inntektshistorikk,
            skattSykepengegrunnlag: SkattSykepengegrunnlag?,
            refusjonshistorikk: Refusjonshistorikk,
            aktivitetslogg: IAktivitetslogg?
        ): ArbeidsgiverInntektsopplysning? {
            val inntektsopplysning = inntektshistorikk.avklarSykepengegrunnlag(skjæringstidspunkt, førsteFraværsdag, skattSykepengegrunnlag) ?: return null
            return super.avklarSykepengegrunnlag(skjæringstidspunkt, førsteFraværsdag, inntektshistorikk, skattSykepengegrunnlag, refusjonshistorikk, aktivitetslogg)
        }

        override fun tilkomneInntekter(
            aktivitetslogg: IAktivitetslogg,
            vedtaksperioder: List<Vedtaksperiode>
        ): List<ArbeidsgiverInntektsopplysning> {
            return emptyList()
        }

        override fun hashCode(): Int {
            throw NotImplementedError()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Arbeidsledig
        }
        override fun identifikator() = Arbeidsledig
        override fun toString() = identifikator()
    }
}
