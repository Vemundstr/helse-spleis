package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.LocalDate
import no.nav.helse.Grunnbeløp

internal class V119SletteSkatteopplydsningFraITVilkårsgrunnlag : JsonMigration(version = 119) {

    override val description: String = "Slette inntektsopplysninger fra skatt som har blitt lagt til vilkårsgrunnlag fra infotrygd ved en feil"

    override fun doMigration(jsonNode: ObjectNode, meldingerSupplier: MeldingerSupplier) {
        jsonNode["vilkårsgrunnlagHistorikk"]
            .flatMap { it["vilkårsgrunnlag"] }
            .filter { "Infotrygd" == it["type"]?.asText() }
            .forEach { fjernInntektsopplysninger((it as ObjectNode)) }
    }

    private fun fjernInntektsopplysninger(vilkårsgrunnlag: ObjectNode) {
        val sykepengegrunnlag = vilkårsgrunnlag["sykepengegrunnlag"] as ObjectNode
        val arbeidsgiverInntektsopplysninger = sykepengegrunnlag.withArray("arbeidsgiverInntektsopplysninger")
        val fjernetSkatteopplysninger = arbeidsgiverInntektsopplysninger.removeAll { it["inntektsopplysning"].has("skatteopplysninger") }

        if(!fjernetSkatteopplysninger) return

        val grunnlagForSykepengegrunnlag = arbeidsgiverInntektsopplysninger.sumOf { it["inntektsopplysning"]["beløp"].asDouble() } * 12
        val sykepengegrunnlag2 = minOf(grunnlagForSykepengegrunnlag, Grunnbeløp.`6G`.beløp(LocalDate.parse(vilkårsgrunnlag["skjæringstidspunkt"].asText())).årlig)

        sykepengegrunnlag.put("sykepengegrunnlag", sykepengegrunnlag2)
        sykepengegrunnlag.put("grunnlagForSykepengegrunnlag", grunnlagForSykepengegrunnlag)
    }
}
