package no.nav.helse.utbetalingslinjer

import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.UtbetalingVisitor
import no.nav.helse.utbetalingslinjer.Fagområde.SPREF
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.NavDag.Companion.arbeidsgiverUtbetaling
import java.time.LocalDate
import java.time.LocalDateTime

// Understands related payment activities for an Arbeidsgiver
internal class Utbetaling
    private constructor(
        private val utbetalingstidslinje: Utbetalingstidslinje,
        private val arbeidsgiverOppdrag: Oppdrag,
        private val personOppdrag: Oppdrag,
        private val tidsstempel: LocalDateTime
    ) {

    internal constructor(
        fødselsnummer: String,
        organisasjonsnummer: String,
        utbetalingstidslinje: Utbetalingstidslinje,
        sisteDato: LocalDate,
        aktivitetslogg: Aktivitetslogg,
        tidligere: Utbetaling?
    ) : this(
        utbetalingstidslinje,
        buildArb(organisasjonsnummer, utbetalingstidslinje, sisteDato, aktivitetslogg, tidligere),
        buildPerson(fødselsnummer, utbetalingstidslinje, sisteDato, aktivitetslogg, tidligere),
        LocalDateTime.now()
    )

    internal fun arbeidsgiverUtbetalingslinjer() = arbeidsgiverOppdrag

    internal fun personUtbetalingslinjer() = personOppdrag

    companion object {

        private fun buildArb(
            organisasjonsnummer: String,
            tidslinje: Utbetalingstidslinje,
            sisteDato: LocalDate,
            aktivitetslogg: Aktivitetslogg,
            tidligere: Utbetaling?
        ) = Oppdrag(
            organisasjonsnummer,
            SPREF,
            SpennBuilder(tidslinje, sisteDato, arbeidsgiverUtbetaling).result()
        )
            .forskjell(tidligere?.arbeidsgiverOppdrag ?: Oppdrag(organisasjonsnummer, SPREF))
            .also {
                if (it.isEmpty())
                    aktivitetslogg.info("Ingen utbetalingslinjer bygget")
                else
                    aktivitetslogg.info("Utbetalingslinjer bygget vellykket")
            }

        private fun buildPerson(
            fødselsnummer: String,
            tidslinje: Utbetalingstidslinje,
            sisteDato: LocalDate,
            aktivitetslogg: Aktivitetslogg,
            tidligere: Utbetaling?
        ): Oppdrag {
            return Oppdrag(fødselsnummer, Fagområde.SP)
        }
    }

    internal fun accept(visitor: UtbetalingVisitor) {
        visitor.preVisitUtbetaling(this, tidsstempel)
        utbetalingstidslinje.accept(visitor)
        visitor.preVisitArbeidsgiverUtbetalingslinjer(arbeidsgiverOppdrag)
        arbeidsgiverOppdrag.accept(visitor)
        visitor.postVisitArbeidsgiverUtbetalingslinjer(arbeidsgiverOppdrag)
        visitor.preVisitPersonUtbetalingslinjer(personOppdrag)
        personOppdrag.accept(visitor)
        visitor.postVisitPersonUtbetalingslinjer(personOppdrag)
        visitor.postVisitUtbetaling(this, tidsstempel)
    }

    internal fun utbetalingstidslinje() = utbetalingstidslinje
}


