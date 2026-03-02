package com.jmvstv_v.scraper

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.jmvstv_v.config.AppConfig
import com.jmvstv_v.model.Listing
import com.jmvstv_v.model.UserFilterEntry
import com.jmvstv_v.repository.SeenListingsRepository
import com.jmvstv_v.repository.SiteRepository
import com.jmvstv_v.repository.UserRepository
import com.jmvstv_v.scraper.processor.SiteProcessor
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
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.hours

/**
 * Periodically fetches new listings for every user that has an active filter
 * and forwards them via the Telegram bot.
 *
 * @param processors           List of site-specific scraping strategies to execute each tick.
 * @param seenListingsRepository Repository for deduplication of already-sent listings.
 * @param userRepository        Repository for resolving users with active filters.
 */
class CollectScheduler(
    private val processors: List<SiteProcessor>,
    private val seenListingsRepository: SeenListingsRepository,
    private val userRepository: UserRepository,
    private val siteRepository: SiteRepository
) {
    private val log = LoggerFactory.getLogger(CollectScheduler::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = AppConfig.httpRequestTimeoutMs
            connectTimeoutMillis = AppConfig.httpConnectTimeoutMs
        }
    }

    private lateinit var bot: Bot

    /**
     * Starts the periodic collection loop and sends new listings to users via [bot].
     *
     * @param bot Telegram bot used for delivering listing messages.
     */
    fun start(bot: Bot) {
        this.bot = bot
        scope.launch {
            while (true) {
                log.info("CollectScheduler: tick started")
                runTick()
                log.info("CollectScheduler: tick finished, next run in ${AppConfig.collectIntervalHours}h")
                delay(AppConfig.collectIntervalHours.hours)
            }
        }
    }

    /**
     * Stops the collection loop and releases HTTP client resources.
     */
    fun stop() {
        scope.cancel()
        client.close()
    }

    fun runForUser(entry: UserFilterEntry) {
        val activeProcessors = processors.filter { it.sourceName in siteRepository.findActiveNames() }
        scope.launch { processUser(entry, activeProcessors) }
    }

    private fun runTick() {
        val activeNames = siteRepository.findActiveNames()
        val activeProcessors = processors.filter { it.sourceName in activeNames }
        val entries = userRepository.findUsersWithActiveFilter()
        log.info("CollectScheduler: ${entries.size} user(s) with active filter")
        entries.forEach { entry ->
            scope.launch { processUser(entry, activeProcessors) }
        }
    }

    private suspend fun processUser(entry: UserFilterEntry, activeProcessors: List<SiteProcessor>) {
        activeProcessors.forEach { processor ->
            processWithProcessor(entry, processor)
        }
    }

    private suspend fun processWithProcessor(entry: UserFilterEntry, processor: SiteProcessor) {
        val url = processor.buildUrl(entry.filter)
        log.info("Fetching ${processor.sourceName} | chatId=${entry.chatId} filterId=${entry.filter.id} url=$url")

        val html = try {
            val response = client.get(url)
            if (!response.status.isSuccess()) {
                log.error("${processor.sourceName} HTTP error | chatId=${entry.chatId} status=${response.status} url=$url")
                return
            }
            response.bodyAsText()
        } catch (e: Exception) {
            log.error("${processor.sourceName} request failed | chatId=${entry.chatId} url=$url error=${e.message}", e)
            return
        }

        val listings = processor.parse(html)
        if (listings.isEmpty()) {
            log.warn("${processor.sourceName}: no listings parsed | chatId=${entry.chatId} url=$url")
            return
        }
        log.info("${processor.sourceName}: parsed ${listings.size} listing(s) | chatId=${entry.chatId}")

        val seenIds     = seenListingsRepository.getSeenIds(entry.filter.id, processor.sourceName, listings.map { it.externalId })
        val newListings = listings.filter { it.externalId !in seenIds }
        log.info("${processor.sourceName}: ${newListings.size} new listing(s) | chatId=${entry.chatId}")

        if (newListings.isEmpty()) return

        seenListingsRepository.markSeen(entry.userId, entry.filter.id, newListings)

        newListings.forEach { listing ->
            bot.sendMessage(chatId = ChatId.fromId(entry.chatId), text = listing.toText())
        }
    }
}

private fun Listing.toText(): String = buildString {
    appendLine("[${sourceSite}] $title")
    if (city.isNotBlank())         appendLine("Город: $city")
    if (propertyType.isNotBlank()) appendLine("Тип: $propertyType")
    price?.let          { appendLine("Цена: ${it}€") }
    area?.let           { appendLine("Площадь: ${it}м²") }
    advertiserType?.let { appendLine("Объявление: $it") }
    appendLine(url)
}
