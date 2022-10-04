package no.nav.helse.person.infotrygdhistorikk

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.desember
import no.nav.helse.dsl.ArbeidsgiverHendelsefabrikk
import no.nav.helse.februar
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.Inntektsinspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.Inntektshistorikk
import no.nav.helse.person.etterlevelse.MaskinellJurist
import no.nav.helse.september
import no.nav.helse.somPersonidentifikator
import no.nav.helse.testhelpers.inntektperioderForSykepengegrunnlag
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class InfotrygdInntektsopplysningTest {

    private companion object {
        private const val ORGNR = "123456789"
        private val INNTEKT = 31000.00.månedlig
        private val DATO = 1.januar
        private val hendelsefabrikk = ArbeidsgiverHendelsefabrikk(
            aktørId = "aktørId",
            personidentifikator = "12029112345".somPersonidentifikator(),
            organisasjonsnummer = ORGNR,
            fødselsdato = 12.februar(1992)
        )
    }

    private lateinit var historikk: Inntektshistorikk
    private lateinit var aktivitetslogg: Aktivitetslogg
    private val inspektør get() = Inntektsinspektør(historikk)

    @BeforeEach
    fun setup() {
        historikk = Inntektshistorikk()
        aktivitetslogg = Aktivitetslogg()
    }

    @Test
    fun likhet() {
        assertEquals(inntektsopplysning(null).hashCode(), inntektsopplysning(null).hashCode())
        assertEquals(inntektsopplysning(1.januar).hashCode(), inntektsopplysning(1.januar).hashCode())
        assertNotEquals(inntektsopplysning().hashCode(), inntektsopplysning(1.januar).hashCode())
        assertNotEquals(inntektsopplysning(1.januar).hashCode(), inntektsopplysning(2.januar).hashCode())
        assertNotEquals(Inntektsopplysning(ORGNR, DATO, 1000.månedlig, true, null).hashCode(), Inntektsopplysning(ORGNR, DATO, 1000.månedlig, false, null).hashCode())
        assertNotEquals(Inntektsopplysning(ORGNR, DATO, 1000.månedlig, true, null).hashCode(), Inntektsopplysning(ORGNR, DATO, 2000.månedlig, true, null).hashCode())
        assertNotEquals(Inntektsopplysning(ORGNR, 1.januar, 1000.månedlig, true, null).hashCode(), Inntektsopplysning(ORGNR, 2.januar, 2100.månedlig, true, null).hashCode())
        assertNotEquals(Inntektsopplysning("ag1", DATO, 1000.månedlig, true, null).hashCode(), Inntektsopplysning("ag2", DATO, 2100.månedlig, true, null).hashCode())
        assertEquals(Inntektsopplysning("ag1", DATO, 123.6667.månedlig, true, null).hashCode(), Inntektsopplysning("ag1", DATO, 123.6667.månedlig, true, null).hashCode())
    }

    @Test
    fun `refusjon opphører før perioden`() {
        inntektsopplysning(1.januar).valider(aktivitetslogg, DATO, Nødnummer.Sykepenger)
        assertFalse(aktivitetslogg.harFunksjonelleFeilEllerVerre())
    }

    @Test
    fun `refusjon opphører i perioden`() {
        inntektsopplysning(15.februar).valider(aktivitetslogg, DATO, Nødnummer.Sykepenger)
        assertFalse(aktivitetslogg.harFunksjonelleFeilEllerVerre())
    }

    @Test
    fun `refusjon opphører etter perioden`() {
        inntektsopplysning(1.mars).valider(aktivitetslogg, DATO, Nødnummer.Sykepenger)
        assertFalse(aktivitetslogg.harFunksjonelleFeilEllerVerre())
    }

    @Test
    fun `Inntekt fra infotrygd brukes ikke til å beregne sykepengegrunnlaget`() {
        Inntektsopplysning.lagreInntekter(
            listOf(Inntektsopplysning(ORGNR, 1.januar, INNTEKT, true)),
            historikk,
            UUID.randomUUID()
        )
        assertEquals(1, inspektør.inntektTeller.size)
        assertEquals(1, inspektør.inntektTeller.first())
        assertNull(historikk.omregnetÅrsinntekt(1.januar, 1.januar)?.omregnetÅrsinntekt())
    }

    @Test
    fun `Bruker inntekt fra inntektsmelding fremfor inntekt fra infotrygd for å beregne sykepengegrunnlaget`() {
        inntektsmelding(beregnetInntekt = 20000.månedlig).addInntekt(historikk, 1.januar, MaskinellJurist())
        Inntektsopplysning.lagreInntekter(
            listOf(Inntektsopplysning(ORGNR, 1.januar, 25000.månedlig, true)),
            historikk,
            UUID.randomUUID()
        )
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(2, inspektør.inntektTeller.first())
        assertEquals(1, inspektør.inntektTeller.last())
        assertEquals(20000.månedlig, historikk.omregnetÅrsinntekt(1.januar, 1.januar)?.omregnetÅrsinntekt())
    }

    @Test
    fun `Bruker skatt fremfor infotrygd for å beregne sykepengegrunnlaget - skatt kommer først`() {
        inntektperioderForSykepengegrunnlag {
            1.desember(2016) til 1.desember(2017) inntekter {
                ORGNR inntekt INNTEKT
            }
            1.desember(2016) til 1.september(2017) inntekter {
                ORGNR inntekt INNTEKT
            }
        }.forEach { it.lagreInntekter(historikk, 1.januar, UUID.randomUUID()) }
        Inntektsopplysning.lagreInntekter(
            listOf(Inntektsopplysning(ORGNR, 1.januar, 25000.månedlig, true)),
            historikk,
            UUID.randomUUID()
        )
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(24, inspektør.inntektTeller.first())
        assertEquals(23, inspektør.inntektTeller.last())
        assertEquals(INNTEKT, historikk.omregnetÅrsinntekt(1.januar, 1.januar)?.omregnetÅrsinntekt())
    }

    @Test
    fun `Duplikate opplysninger`() {
        repeat(2) {
            Inntektsopplysning.lagreInntekter(
                listOf(
                    Inntektsopplysning(ORGNR, 1.januar, 25000.månedlig, true),
                    Inntektsopplysning(ORGNR, 1.mars, 25000.månedlig, true)
                ),
                historikk,
                UUID.randomUUID()
            )
        }

        Inntektsopplysning.lagreInntekter(
            listOf(
                Inntektsopplysning(ORGNR, 1.januar, 26900.månedlig, true),
                Inntektsopplysning(ORGNR, 1.mars, 25000.månedlig, true)
            ),
            historikk,
            UUID.randomUUID()
        )

        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(2, inspektør.inntektTeller[0])
        assertEquals(2, inspektør.inntektTeller[1])
    }

    @Test
    fun `Bruker skatt fremfor infotrygd for å beregne sykepengegrunnlaget - skatt kommer sist`() {
        Inntektsopplysning.lagreInntekter(
            listOf(Inntektsopplysning(ORGNR, 1.januar, 25000.månedlig, true)),
            historikk,
            UUID.randomUUID()
        )
        inntektperioderForSykepengegrunnlag {
            1.desember(2016) til 1.desember(2017) inntekter {
                ORGNR inntekt INNTEKT
            }
            1.desember(2016) til 1.september(2017) inntekter {
                ORGNR inntekt INNTEKT
            }
        }.forEach { it.lagreInntekter(historikk, 1.januar, UUID.randomUUID()) }
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(24, inspektør.inntektTeller.first())
        assertEquals(1, inspektør.inntektTeller.last())
        assertEquals(INNTEKT, historikk.omregnetÅrsinntekt(1.januar, 1.januar)?.omregnetÅrsinntekt())
    }

    @Test
    fun `Inntekt for samme dato og annen kilde erstatter ikke eksisterende`() {
        inntektsmelding().addInntekt(historikk, 1.januar, MaskinellJurist())
        Inntektsopplysning.lagreInntekter(
            listOf(Inntektsopplysning(ORGNR, 1.januar, INNTEKT, true)),
            historikk,
            UUID.randomUUID()
        )
        assertEquals(2, inspektør.inntektTeller.size)
        assertEquals(2, inspektør.inntektTeller.first())
        assertEquals(1, inspektør.inntektTeller.last())
    }

    @Test
    fun equals() {
        val inntektID = UUID.randomUUID()
        val hendelseId = UUID.randomUUID()
        val tidsstempel = LocalDateTime.now()
        val inntektsopplysning1 = Inntektshistorikk.Infotrygd(
            id = inntektID,
            dato = 1.januar,
            hendelseId = hendelseId,
            beløp = 25000.månedlig,
            tidsstempel = tidsstempel
        )
        assertEquals(
            inntektsopplysning1,
            Inntektshistorikk.Infotrygd(
                id = inntektID,
                dato = 1.januar,
                hendelseId = hendelseId,
                beløp = 25000.månedlig,
                tidsstempel = tidsstempel
            )
        )
        assertNotEquals(
            inntektsopplysning1,
            Inntektshistorikk.Infotrygd(
                id = inntektID,
                dato = 5.januar,
                hendelseId = hendelseId,
                beløp = 25000.månedlig,
                tidsstempel = tidsstempel
            )
        )
        assertNotEquals(
            inntektsopplysning1,
            Inntektshistorikk.Infotrygd(
                id = inntektID,
                dato = 1.januar,
                hendelseId = hendelseId,
                beløp = 32000.månedlig,
                tidsstempel = tidsstempel
            )
        )
        assertEquals(
            inntektsopplysning1,
            Inntektshistorikk.Infotrygd(
                id = UUID.randomUUID(),
                dato = 1.januar,
                hendelseId = hendelseId,
                beløp = 25000.månedlig,
                tidsstempel = tidsstempel
            )
        )
        assertEquals(
            inntektsopplysning1,
            Inntektshistorikk.Infotrygd(
                id = inntektID,
                dato = 1.januar,
                hendelseId = UUID.randomUUID(),
                beløp = 25000.månedlig,
                tidsstempel = tidsstempel
            )
        )
        assertEquals(
            inntektsopplysning1,
            Inntektshistorikk.Infotrygd(
                id = inntektID,
                dato = 1.januar,
                hendelseId = hendelseId,
                beløp = 25000.månedlig,
                tidsstempel = LocalDate.EPOCH.atStartOfDay()
            )
        )
    }

    private fun inntektsopplysning(refusjonTom: LocalDate? = null) =
        Inntektsopplysning(ORGNR, DATO, 1000.månedlig, true, refusjonTom)

    private fun inntektsmelding(
        beregnetInntekt: Inntekt = INNTEKT,
        førsteFraværsdag: LocalDate = 1.januar,
        arbeidsgiverperioder: List<Periode> = listOf(1.januar til 16.januar)
    ) = hendelsefabrikk.lagInntektsmelding(
        refusjon = Inntektsmelding.Refusjon(INNTEKT, null, emptyList()),
        førsteFraværsdag = førsteFraværsdag,
        beregnetInntekt = beregnetInntekt,
        arbeidsgiverperioder = arbeidsgiverperioder,
        arbeidsforholdId = null,
        begrunnelseForReduksjonEllerIkkeUtbetalt = null
    )
}
