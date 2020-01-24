package no.nav.helse.person

import no.nav.helse.person.Vedtaksperiode.Vedtaksperiodetilstand
import no.nav.helse.sykdomstidslinje.Sykdomshistorikk
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeVisitor
import no.nav.helse.sykdomstidslinje.Utbetalingslinje
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.UtbetalingsdagVisitor

internal interface PersonVisitor: ArbeidsgiverVisitor {
    fun preVisitPerson(person: Person) {}
    fun visitPersonAktivitetslogger(aktivitetslogger: Aktivitetslogger) {}
    fun preVisitArbeidsgivere() {}
    fun postVisitArbeidsgivere() {}
    fun postVisitPerson(person: Person) {}
}

internal interface ArbeidsgiverVisitor: UtbetalingsdagVisitor, VedtaksperiodeVisitor {
    fun preVisitArbeidsgiver(arbeidsgiver: Arbeidsgiver) {}
    fun visitArbeidsgiverAktivitetslogger(aktivitetslogger: Aktivitetslogger) {}
    fun preVisitInntektHistorie(inntektHistorie: InntektHistorie) {}
    fun postVisitInntektHistorie(inntektHistorie: InntektHistorie) {}
    fun preVisitTidslinjer() {}
    fun postVisitTidslinjer() {}
    fun preVisitPerioder() {}
    fun postVisitPerioder() {}
    fun postVisitArbeidsgiver(arbeidsgiver: Arbeidsgiver) {}
    fun preVisitInntekter() {}
    fun postVisitInntekter() {}
    fun visitInntekt(inntekt: InntektHistorie.Inntekt) {}
}

internal interface VedtaksperiodeVisitor: SykdomstidslinjeVisitor, SykdomshistorikkVisitor {
    fun preVisitVedtaksperiode(vedtaksperiode: Vedtaksperiode) {}
    fun visitVedtaksperiodeAktivitetslogger(aktivitetslogger: Aktivitetslogger) {}
    fun visitUtbetalingslinje(linje: Utbetalingslinje) {}
    fun visitTilstand(tilstand: Vedtaksperiodetilstand) {}
    fun preVisitUtbetalingslinjer() {}
    fun postVisitUtbetalingslinjer() {}
    fun postVisitVedtaksperiode(vedtaksperiode: Vedtaksperiode) {}
}

internal interface SykdomshistorikkVisitor: SykdomstidslinjeVisitor {
    fun preVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) {}
    fun preVisitSykdomshistorikkElement(element: Sykdomshistorikk.Element) {}
    fun visitHendelse(hendelse: SykdomstidslinjeHendelse) {}
    fun postVisitSykdomshistorikkElement(element: Sykdomshistorikk.Element) {}
    fun postVisitSykdomshistorikk(sykdomshistorikk: Sykdomshistorikk) {}
    fun preVisitHendelseSykdomstidslinje() {}
    fun postVisitHendelseSykdomstidslinje() {}
    fun preVisitBeregnetSykdomstidslinje() {}
    fun postVisitBeregnetSykdomstidslinje() {}
}
