package no.nav.helse.hendelser

import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.IAktivitetslogg

class KansellerUtbetaling(
    fnr: String,
    internal val orgnummer: String,
    internal val fagsystemId: String,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : IAktivitetslogg by aktivitetslogg {

}
