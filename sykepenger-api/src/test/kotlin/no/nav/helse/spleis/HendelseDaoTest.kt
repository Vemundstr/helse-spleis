package no.nav.helse.spleis

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spleis.dao.HendelseDao
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.testcontainers.containers.PostgreSQLContainer

@TestInstance(Lifecycle.PER_CLASS)
class HendelseDaoTest {

    private companion object {
        private val postgres = PostgreSQLContainer<Nothing>("postgres:14").apply {
            withReuse(true)
            withLabel("app-navn", "spleis-api")
            start()
        }
    }

    private val UNG_PERSON_FNR = "12029240045"
    private lateinit var dataSource: DataSource
    private lateinit var flyway: Flyway
    private val meldingsReferanse = UUID.randomUUID()

    @BeforeAll
    internal fun `start embedded environment`() {

        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            maximumPoolSize = 3
            minimumIdle = 1
            initializationFailTimeout = 5000
            idleTimeout = 10001
            connectionTimeout = 1000
            maxLifetime = 30001
        })
        flyway = Flyway
            .configure()
            .cleanDisabled(false)
            .dataSource(dataSource)
            .load()

    }

    @BeforeEach
    internal fun setup() {
        flyway.clean()
        flyway.migrate()
        dataSource.lagreHendelse(meldingsReferanse)
    }

    private fun DataSource.lagreHendelse(
        meldingsReferanse: UUID,
        meldingstype: HendelseDao.Meldingstype = HendelseDao.Meldingstype.INNTEKTSMELDING,
        fødselsnummer: String = UNG_PERSON_FNR,
        data: String = """{ "@opprettet": "${LocalDateTime.now()}" }"""
    ) {
        sessionOf(this).use {
            it.run(
                queryOf(
                    "INSERT INTO melding (fnr, melding_id, melding_type, data) VALUES (?, ?, ?, (to_json(?::json)))",
                    fødselsnummer.toLong(),
                    meldingsReferanse.toString(),
                    meldingstype.toString(),
                    data
                ).asExecute
            )
        }
    }

    @Test
    fun `hentAlleHendelser sql er valid`() {
        val dao = HendelseDao(dataSource)
        val events = dao.hentAlleHendelser(UNG_PERSON_FNR.toLong())
        Assertions.assertEquals(1, events.size)
    }

    @Test
    fun `hentHendelser sql er valid`() {
        val dao = HendelseDao(dataSource)
        val ingenEvents = dao.hentHendelser(UNG_PERSON_FNR.toLong())
        Assertions.assertEquals(1, ingenEvents.size)
    }

    @Test
    fun `hentHendelse sql er valid`() {
        val dao = HendelseDao(dataSource)
        val event = dao.hentHendelse(meldingsReferanse)
        Assertions.assertNotNull(event)
    }
}
