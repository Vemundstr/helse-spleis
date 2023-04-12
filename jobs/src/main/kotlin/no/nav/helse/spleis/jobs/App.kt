package no.nav.helse.spleis.jobs

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import javax.sql.DataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotliquery.Row
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.etterlevelse.MaskinellJurist
import no.nav.helse.serde.SerialisertPerson
import no.nav.helse.serde.api.serializePersonForSpeil
import no.nav.helse.serde.serialize
import no.nav.rapids_and_rivers.cli.AivenConfig
import no.nav.rapids_and_rivers.cli.ConsumerProducerFactory
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import kotlin.math.ceil
import kotlin.properties.Delegates
import kotlin.system.measureTimeMillis
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

private val log = LoggerFactory.getLogger("no.nav.helse.spleis.gc.App")

@ExperimentalTime
fun main(args: Array<String>) {
    Thread.setDefaultUncaughtExceptionHandler { thread, err ->
        log.error(
            "Uncaught exception in thread ${thread.name}: {}",
            err.message,
            err
        )
    }

    if (args.isEmpty()) return log.error("Provide a task name as CLI argument")

    when (val task = args[0].trim().lowercase()) {
        "vacuum" -> vacuumTask()
        "avstemming" -> avstemmingTask(ConsumerProducerFactory(AivenConfig.default), args.getOrNull(1)?.toIntOrNull())
        "migrate" -> migrateTask(ConsumerProducerFactory(AivenConfig.default))
        "migrate_v2" -> migrateV2Task(args[1].trim().toInt())
        "test_speiljson" -> testSpeilJsonTask()
        else -> log.error("Unknown task $task")
    }
}

@ExperimentalTime
private fun vacuumTask() {
    val ds = DataSourceConfiguration(DbUser.SPLEIS).dataSource()
    log.info("Commencing VACUUM FULL")
    val duration = measureTime {
        sessionOf(ds).use { session -> session.run(queryOf(("VACUUM FULL person")).asExecute) }
    }
    log.info(
        "VACUUM FULL completed after {} hour(s), {} minute(s) and {} second(s)",
        duration.toInt(DurationUnit.HOURS),
        duration.toInt(DurationUnit.MINUTES) % 60,
        duration.toInt(DurationUnit.SECONDS) % 60
    )
}

private fun migrateV2Task(targetVersjon: Int) {
    DataSourceConfiguration(DbUser.MIGRATE).dataSource().use { ds ->
        sessionOf(ds).use { session ->
            val finnArbeid = { txSession: TransactionalSession, utførArbeid: (Long, String) -> Unit ->
                txSession.run(queryOf("SELECT fnr,data FROM person WHERE skjema_versjon < $targetVersjon LIMIT 1 FOR UPDATE SKIP LOCKED;").map {
                    it.long("fnr") to it.string("data")
                }.asSingle)?.also { (ident, arbeid) -> utførArbeid(ident, arbeid) } != null

            }
            var arbeidUtført: Boolean
            var migreringCounter = 0
            do {
                arbeidUtført = session.transaction { txSession ->
                    finnArbeid(txSession) { ident, data ->
                        migreringCounter += 1
                        log.info("[$migreringCounter] Utfører migrering")
                        val time = measureTimeMillis {
                            val resultat = SerialisertPerson(data).deserialize(MaskinellJurist()).serialize()
                            check(1 == txSession.run(queryOf("UPDATE person SET skjema_versjon=:skjemaversjon, data=:data WHERE fnr=:ident", mapOf(
                                "skjemaversjon" to resultat.skjemaVersjon,
                                "data" to resultat.json,
                                "ident" to ident
                            )).asUpdate))
                        }
                        log.info("[$migreringCounter] Utført på $time ms")
                    }
                }
            } while (arbeidUtført)
        }
    }
}

// tester hvorvidt personer lar seg serialisere til speil uten exceptions
// bør bare kjøres med parallellism=1 fordi arbeidet kan ikke fordeles på flere podder
@OptIn(ExperimentalCoroutinesApi::class)
private fun testSpeilJsonTask(numberOfWorkers: Int = 16) {
    DataSourceConfiguration(DbUser.MIGRATE).dataSource(numberOfWorkers + 3).use { ds ->
        sessionOf(ds).use { session ->
            runBlocking(Dispatchers.IO) {
                val personer = producer(session)
                val progress = Channel<Pair<String, String?>>(capacity = UNLIMITED)
                val latch = CountDownLatch(numberOfWorkers)
                repeat(latch.count.toInt()) {
                    consumer(it + 1, session, personer, progress, latch)
                }
                // lukker progress-channel når alle consumerne er ferdig/latchen går til 0
                launch {
                    while (latch.count > 0) { /* nop */ }
                    progress.close()
                }
                var counter = 0
                val start = System.currentTimeMillis()
                while (!progress.isClosedForReceive) {
                    val now = System.currentTimeMillis()
                    progress.receiveCatching().getOrNull()?.let { (aktørId, err) ->
                        counter += 1
                        if (err != null) {
                            log.info("[${counter.toString().padStart(7)}][${now - start} ms elapsed][${latch.count}] - $aktørId virker ikke å være ok: $err")
                        } else {
                            log.info("[${counter.toString().padStart(7)}][${now - start} ms elapsed][${latch.count}] - $aktørId viker å være ok")
                        }
                    }
                }
                log.info("Task finished")
            }
        }
    }
}

@ExperimentalCoroutinesApi
private fun CoroutineScope.producer(session: Session) = produce<Long>(capacity = UNLIMITED) {
    log.info("[PRODUCER] Starting 👍")
    session.run(queryOf("SELECT fnr FROM unike_person").map { it.long("fnr") }.asList)
        .forEach { send(it) }
    log.info("[PRODUCER] Done 👍")
}

@ExperimentalCoroutinesApi
private fun CoroutineScope.consumer(id: Int, session: Session, personer: ReceiveChannel<Long>, progress: SendChannel<Pair<String, String?>>, latch: CountDownLatch) {
    launch {
        log.info("[CONSUMER $id] Starting UP!")
        while (!personer.isClosedForReceive) {
            personer.receiveCatching().getOrNull()?.also { fnr ->
                log.info("[CONSUMER $id] Consuming")
                hentPerson(session, fnr.toString())?.let { (_, aktørId, data) ->
                    val err = try {
                        serializePersonForSpeil(SerialisertPerson(data).deserialize(MaskinellJurist()), emptyList())
                        null
                    } catch (err: Exception) {
                        err.message
                    }
                    progress.send(aktørId to err)
                } ?: log.info("[CONSUMER $id] Got null")
            }
        }
        log.info("[CONSUMER $id] DOWN!")
        latch.countDown()
    }
}

private fun hentPerson(session: Session, ident: String) =
    session.run(queryOf("SELECT fnr,aktor_id FROM unike_person WHERE fnr=:ident OR aktor_id=:ident", mapOf("ident" to ident.toLong())).map { it.string("fnr") }.asSingle)?.let { fnr ->
        session.run(queryOf("SELECT data FROM person WHERE fnr = ? ORDER BY id DESC LIMIT 1", fnr.toLong()).map {
            Triple(it.long("fnr"), it.string("aktor_id"), it.string("data"))
        }.asSingle)
    }

private fun migrateTask(factory: ConsumerProducerFactory) {
    DataSourceConfiguration(DbUser.MIGRATE).dataSource().use { ds ->
        var count = 0L
        factory.createProducer().use { producer ->
            sessionOf(ds).use { session ->
                session.run(queryOf("SELECT fnr,aktor_id FROM unike_person").map { row ->
                    val fnr = row.string("fnr").padStart(11, '0')
                    fnr to row.string("aktor_id")
                }.asList)
            }.forEach { (fnr, aktørId) ->
                count += 1
                producer.send(ProducerRecord("tbd.rapid.v1", fnr, lagMigrate(fnr, aktørId)))
            }
            producer.flush()
        }
        println()
        println("==============================")
        println("Sendte ut $count migreringer")
        println("==============================")
        println()
    }

}

@ExperimentalTime
private fun avstemmingTask(factory: ConsumerProducerFactory, customDayOfMonth: Int? = null) {
    // Håndter on-prem og gcp database tilkobling forskjellig
    val ds = DataSourceConfiguration(DbUser.AVSTEMMING).dataSource()
    val dayOfMonth = customDayOfMonth ?: LocalDate.now().dayOfMonth
    log.info("Commencing avstemming for dayOfMonth=$dayOfMonth")
    val producer = factory.createProducer()
    // spørringen funker slik at den putter alle personer opp i 27 (*) ulike "bøtter" (fnr % 27 gir tall mellom 0 og 26, også plusser vi på én slik at vi starter fom. 1)
    // hvor én bøtte tilsvarer én dag i måneden. Tallet 27 (for februar) ble valgt slik at vi sikrer oss at vi avstemmer
    // alle personer hver måned. Dag 28, 29, 30, 31 avstemmes 0 personer siden det er umulig å ha disse rest-verdiene
    //
    // * det skulle nok vært 28
    val paginated = PaginatedQuery(
        "fnr,aktor_id",
        "unike_person",
        "(1 + mod(fnr, 27)) = :dayOfMonth AND (sist_avstemt is null or sist_avstemt < now() - interval '1 day')"
    )
    val duration = measureTime {
        paginated.run(ds, mapOf("dayOfMonth" to dayOfMonth)) { row ->
            val fnr = row.string("fnr").padStart(11, '0')
            producer.send(ProducerRecord("tbd.rapid.v1", fnr, lagAvstemming(fnr, row.string("aktor_id"))))
        }
    }
    producer.flush()
    log.info(
        "Avstemming completed after {} hour(s), {} minute(s) and {} second(s)",
        duration.toInt(DurationUnit.HOURS),
        duration.toInt(DurationUnit.MINUTES) % 60,
        duration.toInt(DurationUnit.SECONDS) % 60
    )
}

private fun lagMigrate(fnr: String, aktørId: String) = """
{
  "@id": "${UUID.randomUUID()}",
  "@event_name": "json_migrate",
  "@opprettet": "${LocalDateTime.now()}",
  "aktørId": "$aktørId",
  "fødselsnummer": "$fnr"
}
"""

private fun lagAvstemming(fnr: String, aktørId: String) = """
{
  "@id": "${UUID.randomUUID()}",
  "@event_name": "person_avstemming",
  "@opprettet": "${LocalDateTime.now()}",
  "aktørId": "$aktørId",
  "fødselsnummer": "$fnr"
}
"""

private class PaginatedQuery(private val select: String, private val table: String, private val where: String) {
    private var count by Delegates.notNull<Long>()
    private val resultsPerPage = 1000

    private fun count(session: Session, params: Map<String, Any>) {
        this.count =
            session.run(queryOf("SELECT COUNT(1) FROM $table WHERE $where", params).map { it.long(1) }.asSingle) ?: 0
    }

    fun run(dataSource: DataSource, params: Map<String, Any>, handler: (Row) -> Unit) {
        sessionOf(dataSource).use { session ->
            count(session, params)
            val pages = ceil(count / resultsPerPage.toDouble()).toInt()
            log.info("Total of $count records, yielding $pages pages ($resultsPerPage results per page)")
            var currentPage = 0
            while (currentPage < pages) {
                val rows = session.run(
                    queryOf(
                        "SELECT $select FROM $table WHERE $where LIMIT $resultsPerPage OFFSET ${currentPage * resultsPerPage}",
                        params
                    ).map { row -> handler(row) }.asList
                ).count()
                currentPage += 1
                log.info("Page $currentPage of $pages complete ($rows rows)")
            }
        }
    }
}

private class DataSourceConfiguration(dbUsername: DbUser) {
    private val env = System.getenv()

    private val gcpProjectId = requireNotNull(env["GCP_TEAM_PROJECT_ID"]) { "gcp project id must be set" }
    private val databaseRegion = requireNotNull(env["DATABASE_REGION"]) { "database region must be set" }
    private val databaseInstance = requireNotNull(env["DATABASE_INSTANCE"]) { "database instance must be set" }
    private val databaseUsername = requireNotNull(env["${dbUsername}_USERNAME"]) { "database username must be set" }
    private val databasePassword = requireNotNull(env["${dbUsername}_PASSWORD"]) { "database password must be set"}
    private val databaseName = requireNotNull(env["${dbUsername}_DATABASE"]) { "database name must be set"}

    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = String.format(
            "jdbc:postgresql:///%s?%s&%s",
            databaseName,
            "cloudSqlInstance=$gcpProjectId:$databaseRegion:$databaseInstance",
            "socketFactory=com.google.cloud.sql.postgres.SocketFactory"
        )

        username = databaseUsername
        password = databasePassword

        maximumPoolSize = 3
        minimumIdle = 1
        initializationFailTimeout = Duration.ofMinutes(1).toMillis()
        connectionTimeout = Duration.ofSeconds(5).toMillis()
        maxLifetime = Duration.ofMinutes(30).toMillis()
        idleTimeout = Duration.ofMinutes(10).toMillis()
    }

    fun dataSource(maximumPoolSize: Int = 3) = HikariDataSource(hikariConfig.apply {
        this.maximumPoolSize = maximumPoolSize
    })
}

private enum class DbUser(private val dbUserPrefix: String) {
    SPLEIS("DATABASE"), AVSTEMMING("DATABASE_SPLEIS_AVSTEMMING"), MIGRATE("DATABASE_SPLEIS_MIGRATE");

    override fun toString() = dbUserPrefix
}
