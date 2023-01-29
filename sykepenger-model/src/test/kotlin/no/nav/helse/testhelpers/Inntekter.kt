package no.nav.helse.testhelpers

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import no.nav.helse.hendelser.ArbeidsgiverInntekt
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.inntekt.Inntektshistorikk
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.månedlig

internal fun inntektperioderForSammenligningsgrunnlag(block: Inntektperioder.() -> Unit) = Inntektperioder(block).inntekter()
internal fun inntektperioderForSykepengegrunnlag(block: Inntektperioder.() -> Unit) = Inntektperioder(block).inntekter()

internal fun List<ArbeidsgiverInntekt>.lagreInntekter(inntektshistorikk: Inntektshistorikk, skjæringstidspunkt: LocalDate, meldingsreferanseId: UUID) {
    this
        .map { it.tilSykepengegrunnlag(skjæringstidspunkt, meldingsreferanseId) }
        .also { inntekter -> inntektshistorikk.leggTil(inntekter) }
}

internal class Inntektperioder(block: Inntektperioder.() -> Unit) {
    private val liste = mutableListOf<Pair<String, List<ArbeidsgiverInntekt.MånedligInntekt>>>()

    init {
        block()
    }

    internal fun inntekter(): List<ArbeidsgiverInntekt> = liste
        .groupBy({ (arbeidsgiver, _) -> arbeidsgiver }) { (_, inntekter) ->
            inntekter
        }
        .map { (arbeidsgiver, inntekter) -> ArbeidsgiverInntekt(arbeidsgiver, inntekter.flatten()) }


    internal infix fun Periode.inntekter(block: Inntekter.() -> Unit) =
        lagInntekter("fastloenn", ArbeidsgiverInntekt.MånedligInntekt.Inntekttype.LØNNSINNTEKT, block)

    internal infix fun Periode.sykepenger(block: Inntekter.() -> Unit) =
        lagInntekter("sykepenger", ArbeidsgiverInntekt.MånedligInntekt.Inntekttype.YTELSE_FRA_OFFENTLIGE, block)

    private fun Periode.lagInntekter(
        beskrivelse: String = "fastloenn",
        type: ArbeidsgiverInntekt.MånedligInntekt.Inntekttype = ArbeidsgiverInntekt.MånedligInntekt.Inntekttype.LØNNSINNTEKT,
        block: Inntekter.() -> Unit,
    ) =
        this.map(YearMonth::from)
            .distinct()
            .flatMap { yearMonth ->
                Inntekter(block).toList().groupBy({ (arbeidsgiver, _) -> arbeidsgiver }) { (_, inntekt) ->
                    ArbeidsgiverInntekt.MånedligInntekt(
                        yearMonth,
                        inntekt,
                        type,
                        "kontantytelse",
                        beskrivelse
                    )
                }.toList()
            }
            .groupBy({ (arbeidsgiver, _) -> arbeidsgiver }) { (_, inntekt) -> inntekt }
            .map { (arbeidsgiver, inntekter) ->
                arbeidsgiver to inntekter.flatten()
            }
            .also { liste.addAll(it) }

    internal class Inntekter(block: Inntekter.() -> Unit) {
        private val liste = mutableListOf<Pair<String, Inntekt>>()

        init {
            block()
        }

        internal fun toList() = liste.toList()

        infix fun String.inntekt(inntekt: Int) = this inntekt inntekt.månedlig
        infix fun String.inntekt(inntekt: Inntekt) = liste.add(this to inntekt)
    }
}
