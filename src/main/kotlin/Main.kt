package com.jmvstv_v

import com.jmvstv_v.bot.FilterConversation
import com.jmvstv_v.bot.buildTelegramBot
import com.jmvstv_v.db.AppDatabase
import com.jmvstv_v.repository.ExposedFilterRepository
import com.jmvstv_v.repository.ExposedSeenListingsRepository
import com.jmvstv_v.repository.ExposedSiteRepository
import com.jmvstv_v.repository.ExposedUserRepository
import com.jmvstv_v.scraper.CollectScheduler
import com.jmvstv_v.scraper.processor.CityExpertProcessor
import com.jmvstv_v.scraper.processor.FourZidaProcessor
import com.jmvstv_v.scraper.processor.HaloOglasiProcessor
import com.jmvstv_v.scraper.processor.NekretninProcessor
import com.jmvstv_v.scraper.processor.SiteProcessor
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

fun main() {
    AppDatabase.init()

    val siteRepository         = ExposedSiteRepository().also { it.seed() }
    val userRepository         = ExposedUserRepository()
    val filterRepository       = ExposedFilterRepository()
    val seenListingsRepository = ExposedSeenListingsRepository()

    val conversation = FilterConversation(filterRepository, userRepository)

    val processors: List<SiteProcessor> = listOf(
        CityExpertProcessor,
        FourZidaProcessor,
        HaloOglasiProcessor,
        NekretninProcessor
    )

    val telegramBot = buildTelegramBot(userRepository, filterRepository, conversation)

    val collectScheduler = CollectScheduler(processors, seenListingsRepository, userRepository, siteRepository)
    collectScheduler.start(telegramBot)
    Runtime.getRuntime().addShutdownHook(Thread { collectScheduler.stop() })

    telegramBot.startPolling()

    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) {
            json()
        }
        routing {
            get("/actuator/health") {
                call.respond(Message("UP"))
            }
        }
    }.start(wait = true)
}
