package no.nav.helse.person.etterlevelse

import no.nav.helse.hendelser.Periode
import no.nav.helse.person.etterlevelse.MaskinellJurist.*
import java.time.LocalDate

internal interface SubsumsjonVisitor {
    fun preVisitSubsumsjon(
        utfall: Subsumsjon.Utfall,
        versjon: LocalDate,
        paragraf: Paragraf,
        ledd: Ledd?,
        punktum: Punktum?,
        bokstav: Bokstav?,
        input: Map<String, Any>,
        output: Map<String, Any>,
        kontekster: Map<String, KontekstType>
    ) {}

    fun visitGrupperbarSubsumsjon(perioder: List<Periode>) {}
    fun visitBetingetSubsumsjon(funnetRelevant: Boolean) {}

    fun postVisitSubsumsjon(
        utfall: Subsumsjon.Utfall,
        versjon: LocalDate,
        paragraf: Paragraf,
        ledd: Ledd?,
        punktum: Punktum?,
        bokstav: Bokstav?,
        input: Map<String, Any>,
        output: Map<String, Any>,
        kontekster: Map<String, KontekstType>
    ) {}
}
