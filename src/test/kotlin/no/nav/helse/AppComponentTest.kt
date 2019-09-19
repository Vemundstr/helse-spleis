package no.nav.helse

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.opentable.db.postgres.embedded.EmbeddedPostgres
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.CollectorRegistry
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.helse.Topics.inntektsmeldingTopic
import no.nav.helse.Topics.sykmeldingTopic
import no.nav.helse.Topics.søknadTopic
import no.nav.helse.sakskompleks.SakskompleksDao
import no.nav.helse.sakskompleks.SakskompleksService
import no.nav.helse.serde.JsonNodeSerializer
import no.nav.helse.sykmelding.domain.SykmeldingMessage
import no.nav.helse.søknad.SøknadConsumer.Companion.søknadObjectMapper
import no.nav.helse.sykmelding.SykmeldingConsumer.Companion.sykmeldingObjectMapper
import no.nav.helse.sykmelding.SykmeldingProbe.Companion.sykmeldingCounterName
import no.nav.helse.søknad.SøknadProbe.Companion.søknadCounterName
import no.nav.helse.søknad.SøknadProbe.Companion.søknaderIgnorertCounterName
import no.nav.helse.søknad.domain.Sykepengesøknad
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.Properties
import java.util.concurrent.TimeUnit

@KtorExperimentalAPI
class AppComponentTest {

    companion object {

        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"

        private val embeddedEnvironment = KafkaEnvironment(
                users = listOf(JAASCredential(username, password)),
                autoStart = false,
                withSchemaRegistry = false,
                withSecurity = true,
                topicNames = listOf(sykmeldingTopic, søknadTopic, inntektsmeldingTopic)
        )

        @BeforeAll
        @JvmStatic
        fun start() {
            embeddedEnvironment.start()
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            embeddedEnvironment.tearDown()
        }
    }

    private lateinit var embeddedPostgres: EmbeddedPostgres
    private lateinit var postgresConnection: Connection

    @BeforeEach
    fun `start postgres`() {
        embeddedPostgres = EmbeddedPostgres.builder()
                .start()

        postgresConnection = embeddedPostgres.postgresDatabase.connection
    }

    @AfterEach
    fun `stop postgres`() {
        postgresConnection.close()
        embeddedPostgres.close()
    }

    @Test
    fun `behandler ikke søknad om utlandsopphold`() {
        testServer(config = mapOf(
            "KAFKA_BOOTSTRAP_SERVERS" to embeddedEnvironment.brokersURL,
            "KAFKA_USERNAME" to username,
            "KAFKA_PASSWORD" to password,
            "DATABASE_JDBC_URL" to embeddedPostgres.getJdbcUrl("postgres", "postgres")
        )) {

            val søknadCounterBefore = getCounterValue(søknadCounterName)
            val søknadIgnorertCounterBefore = getCounterValue(søknaderIgnorertCounterName)

            val søknad = søknadObjectMapper.readTree("/søknad_om_utlandsopphold.json".readResource())
            produceOneMessage(søknadTopic, søknad["id"].asText(), søknad)

            await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted {
                    val søknadCounterAfter = getCounterValue(søknadCounterName)
                    val søknadIgnorertCounterAfter = getCounterValue(søknaderIgnorertCounterName)

                    assertEquals(0, søknadCounterAfter - søknadCounterBefore)
                    assertEquals(1, søknadIgnorertCounterAfter - søknadIgnorertCounterBefore)
                }
        }
    }

    @Test
    fun `behandler ikke søknad med status != SENDT`() {
        testServer(config = mapOf(
            "KAFKA_BOOTSTRAP_SERVERS" to embeddedEnvironment.brokersURL,
            "KAFKA_USERNAME" to username,
            "KAFKA_PASSWORD" to password,
            "DATABASE_JDBC_URL" to embeddedPostgres.getJdbcUrl("postgres", "postgres")
        )) {

            val søknadCounterBefore = getCounterValue(søknadCounterName)
            val søknadIgnorertCounterBefore = getCounterValue(søknaderIgnorertCounterName)

            val søknad = søknadObjectMapper.readTree("/søknad_frilanser_ny.json".readResource())
            produceOneMessage(søknadTopic, søknad["id"].asText(), søknad)

            await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted {
                    val søknadCounterAfter = getCounterValue(søknadCounterName)
                    val søknadIgnorertCounterAfter = getCounterValue(søknaderIgnorertCounterName)

                    assertEquals(0, søknadCounterAfter - søknadCounterBefore)
                    assertEquals(1, søknadIgnorertCounterAfter - søknadIgnorertCounterBefore)
                }
        }
    }

    @Test
    fun `kobler søknad til eksisterende sakskompleks`() {
        val sakskompleksDao = SakskompleksDao(embeddedPostgres.postgresDatabase)
        val sakskompleksService = SakskompleksService(sakskompleksDao)

        testServer(config = mapOf(
            "KAFKA_BOOTSTRAP_SERVERS" to embeddedEnvironment.brokersURL,
            "KAFKA_USERNAME" to username,
            "KAFKA_PASSWORD" to password,
            "DATABASE_JDBC_URL" to embeddedPostgres.getJdbcUrl("postgres", "postgres")
        )) {

            val sykmeldingCounterBefore = getCounterValue(sykmeldingCounterName)
            val søknadCounterBefore = getCounterValue(søknadCounterName)

            val sykmelding = sykmeldingObjectMapper.readTree("/sykmelding.json".readResource())
            produceOneMessage(sykmeldingTopic, sykmelding["sykmelding"]["id"].asText(), sykmelding, sykmeldingObjectMapper)

            await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted {
                    val sykmeldingCounterAfter = getCounterValue(sykmeldingCounterName)

                    assertEquals(1, sykmeldingCounterAfter - sykmeldingCounterBefore)
                    assertNotNull(sakskompleksService.finnSak(SykmeldingMessage(sykmelding).sykmelding))
                }

            val søknad = søknadObjectMapper.readTree("/søknad_arbeidstaker_sendt_nav.json".readResource())
            produceOneMessage(søknadTopic, søknad["id"].asText(), søknad)

            await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted {
                    val søknadCounterAfter = getCounterValue(søknadCounterName)

                    assertEquals(1, søknadCounterAfter - søknadCounterBefore)

                    val sak = sakskompleksService.finnSak(Sykepengesøknad(søknad))
                    assertEquals(listOf(SykmeldingMessage(sykmelding).sykmelding), sak?.sykmeldinger)
                    assertEquals(listOf(Sykepengesøknad(søknad)), sak?.søknader)
                }
        }
    }

    @Test
    fun `søknad uten tilhørende sykmelding ignoreres`() {
        val sakskompleksDao = SakskompleksDao(embeddedPostgres.postgresDatabase)
        val sakskompleksService = SakskompleksService(sakskompleksDao)

        testServer(config = mapOf(
            "KAFKA_BOOTSTRAP_SERVERS" to embeddedEnvironment.brokersURL,
            "KAFKA_USERNAME" to username,
            "KAFKA_PASSWORD" to password,
            "DATABASE_JDBC_URL" to embeddedPostgres.getJdbcUrl("postgres", "postgres")
        )) {

            val søknadCounterBefore = getCounterValue(søknadCounterName)
            val søknad = søknadObjectMapper.readTree("/søknad_arbeidstaker_sendt_nav.json".readResource())
            produceOneMessage(søknadTopic, søknad["id"].asText(), søknad)

            await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted {
                    val søknadCounterAfter = getCounterValue(søknadCounterName)

                    assertEquals(1, søknadCounterAfter - søknadCounterBefore)

                    val sak = sakskompleksService.finnSak(Sykepengesøknad(søknad))
                    assertNull(sak)
                }
        }
    }

    private fun produceOneMessage(topic: String, key: String, message: JsonNode, objectMapper: ObjectMapper = søknadObjectMapper) {
        val producer = KafkaProducer<String, JsonNode>(producerProperties(), StringSerializer(), JsonNodeSerializer(objectMapper))
        producer.send(ProducerRecord(topic, key, message))
                .get(1, TimeUnit.SECONDS)
    }

    private fun producerProperties() =
            Properties().apply {
                put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, embeddedEnvironment.brokersURL)
                put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
                put(SaslConfigs.SASL_MECHANISM, "PLAIN")
                put(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";")
            }

    private fun getCounterValue(name: String, labelValues: List<String> = emptyList()) =
            (CollectorRegistry.defaultRegistry
                    .findMetricSample(name, labelValues)
                    ?.value ?: 0.0).toInt()

    private fun CollectorRegistry.findMetricSample(name: String, labelValues: List<String>) =
            findSamples(name).firstOrNull { sample ->
                sample.labelValues.size == labelValues.size && sample.labelValues.containsAll(labelValues)
            }

    private fun CollectorRegistry.findSamples(name: String) =
            filteredMetricFamilySamples(setOf(name))
                    .toList()
                    .flatMap { metricFamily ->
                        metricFamily.samples
                    }
}
