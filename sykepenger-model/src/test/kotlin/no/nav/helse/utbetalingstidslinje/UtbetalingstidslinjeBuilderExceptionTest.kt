package no.nav.helse.utbetalingstidslinje

import no.nav.helse.januar
import no.nav.helse.person.aktivitetslogg.Aktivitetslogg
import no.nav.helse.person.aktivitetslogg.IAktivitetslogg
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.testhelpers.TestEvent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class UtbetalingstidslinjeBuilderExceptionTest {
    private lateinit var aktivitetslogg: IAktivitetslogg

    @BeforeEach
    fun setup() {
        aktivitetslogg = Aktivitetslogg()
    }

    @Test
    fun `logger uforventet dag til aktivitetslogg`() {
        val melding = "Kan ikke velge"
        UtbetalingstidslinjeBuilderException.UforventetDagException(Dag.ProblemDag(1.januar, TestEvent.søknad, melding), melding)
            .logg(aktivitetslogg)
        assertTrue(aktivitetslogg.harFunksjonelleFeilEllerVerre())
    }
}
