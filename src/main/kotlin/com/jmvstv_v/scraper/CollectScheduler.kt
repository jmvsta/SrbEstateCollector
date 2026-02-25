package com.jmvstv_v.scraper

import com.jmvstv_v.model.Filter
import com.jmvstv_v.model.FiltersTable
import com.jmvstv_v.model.UsersTable
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.hours

object CollectScheduler {

    private val log = LoggerFactory.getLogger(CollectScheduler::class.java)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
    }

    fun start() {
        scope.launch {
            while (true) {
                log.info("CollectScheduler: tick started")
                runTick()
                log.info("CollectScheduler: tick finished, next run in 1h")
                delay(1.hours)
            }
        }
    }

    fun stop() {
        scope.cancel()
        client.close()
    }

    // -------------------------------------------------------------------------

    private suspend fun runTick() {
        val entries = fetchUsersWithFilter()
        log.info("CollectScheduler: ${entries.size} user(s) with active filter")

        entries.forEach { (chatId, filter) ->
            // каждый пользователь в своей корутине, ошибка одного не роняет других
            scope.launch {
                processUser(chatId, filter)
            }
        }
    }

    /** SELECT users.*, filters.* FROM users INNER JOIN filters ON users.active_filter_id = filters.id */
    private fun fetchUsersWithFilter(): List<Pair<Long, Filter>> = transaction {
        UsersTable
            .join(
                otherTable  = FiltersTable,
                joinType    = JoinType.INNER,
                onColumn    = UsersTable.activeFilterId,
                otherColumn = FiltersTable.id
            )
            .selectAll()
            .map { row -> row[UsersTable.chatId] to Filter.fromRow(row) }
    }

    private suspend fun processUser(chatId: Long, filter: Filter) {
        val url = CityExpertUrlBuilder.build(filter)
        log.info("Fetching CityExpert | chatId=$chatId filterId=${filter.id} url=$url")

        val body = try {
            val response = client.get(url)
            if (!response.status.isSuccess()) {
                log.error(
                    "CityExpert HTTP error | chatId=$chatId status=${response.status} url=$url"
                )
                return
            }
            response.bodyAsText()
        } catch (e: Exception) {
            log.error("CityExpert request failed | chatId=$chatId url=$url error=${e.message}", e)
            return
        }

        // TODO: распарсить листинги из body
        // TODO: отфильтровать уже виденные (проверка по БД / кэшу)
        // TODO: отправить новые листинги пользователю через Telegram bot
        log.info("CityExpert response received | chatId=$chatId bodyLength=${body.length}")
    }
}
