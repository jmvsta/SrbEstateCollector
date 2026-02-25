package com.jmvstv_v

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.jmvstv_v.bot.FilterConversation
import com.jmvstv_v.config.AppConfig
import com.jmvstv_v.db.SqliteDatabase
import com.jmvstv_v.scraper.CollectScheduler
import com.jmvstv_v.model.FiltersTable
import com.jmvstv_v.model.UsersTable
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

@Serializable
data class Message(
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

fun main() {
    SqliteDatabase.init()

    val telegramBot = bot {
        token = AppConfig.telegramBotToken
        dispatch {
            command("start") {
                val telegramId = message.from?.id ?: return@command
                val chatId = message.chat.id
                val username = message.from?.username

                FilterConversation.cancel(chatId)

                val isNew = transaction {
                    val exists = UsersTable.selectAll()
                        .where { UsersTable.telegramId eq telegramId }
                        .count() > 0

                    if (!exists) {
                        UsersTable.insertIgnore {
                            it[UsersTable.telegramId] = telegramId
                            it[UsersTable.chatId] = chatId
                            it[UsersTable.username] = username
                        }
                        true
                    } else {
                        false
                    }
                }

                val reply = if (isNew) "Добро пожаловать! Вы зарегистрированы." else "С возвращением!"
                bot.sendMessage(chatId = ChatId.fromId(chatId), text = reply)
            }

            command("addfilter") {
                val telegramId = message.from?.id ?: return@command
                val chatId = message.chat.id

                val userExists = transaction {
                    UsersTable.selectAll()
                        .where { UsersTable.telegramId eq telegramId }
                        .count() > 0
                }

                if (!userExists) {
                    bot.sendMessage(ChatId.fromId(chatId), "Сначала выполните /start для регистрации.")
                    return@command
                }

                FilterConversation.start(bot, chatId)
            }

            command("setfilter") {
                val telegramId = message.from?.id ?: return@command
                val chatId = message.chat.id

                val filters = transaction {
                    val userRow = UsersTable.selectAll()
                        .where { UsersTable.telegramId eq telegramId }
                        .singleOrNull() ?: return@transaction null
                    val userId = userRow[UsersTable.id]
                    FiltersTable.selectAll()
                        .where { FiltersTable.userId eq userId }
                        .map { it[FiltersTable.id] to it[FiltersTable.name] }
                }

                when {
                    filters == null -> bot.sendMessage(
                        ChatId.fromId(chatId),
                        "Сначала выполните /start для регистрации."
                    )
                    filters.isEmpty() -> bot.sendMessage(
                        ChatId.fromId(chatId),
                        "У вас нет фильтров. Создайте фильтр командой /addfilter."
                    )
                    else -> {
                        val keyboard = InlineKeyboardMarkup.create(
                            filters.map { (id, name) ->
                                listOf(InlineKeyboardButton.CallbackData(text = name, callbackData = "setfilter:$id"))
                            }
                        )
                        bot.sendMessage(
                            chatId = ChatId.fromId(chatId),
                            text = "Ваши фильтры. Нажмите для активации:",
                            replyMarkup = keyboard
                        )
                    }
                }
            }

            callbackQuery {
                val data = callbackQuery.data ?: return@callbackQuery
                if (!data.startsWith("setfilter:")) return@callbackQuery

                val filterId = data.removePrefix("setfilter:").toIntOrNull() ?: return@callbackQuery
                val telegramId = callbackQuery.from.id
                val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery

                val filterName = transaction {
                    val userRow = UsersTable.selectAll()
                        .where { UsersTable.telegramId eq telegramId }
                        .singleOrNull() ?: return@transaction null
                    val userId = userRow[UsersTable.id]

                    val filterRow = FiltersTable.selectAll()
                        .where { (FiltersTable.id eq filterId) and (FiltersTable.userId eq userId) }
                        .singleOrNull() ?: return@transaction null

                    UsersTable.update({ UsersTable.telegramId eq telegramId }) {
                        it[activeFilterId] = filterId
                    }

                    filterRow[FiltersTable.name]
                }

                if (filterName != null) {
                    bot.answerCallbackQuery(callbackQuery.id, text = "Фильтр «$filterName» активирован")
                    bot.sendMessage(ChatId.fromId(chatId), "Активный фильтр: «$filterName»")
                } else {
                    bot.answerCallbackQuery(callbackQuery.id, text = "Фильтр не найден")
                }
            }

            text {
                val chatId = message.chat.id
                val telegramId = message.from?.id ?: return@text
                val txt = message.text ?: return@text
                if (txt.startsWith("/")) return@text

                if (FilterConversation.isActive(chatId)) {
                    FilterConversation.handle(bot, chatId, telegramId, txt)
                }
            }
        }
    }

    CollectScheduler.start()
    Runtime.getRuntime().addShutdownHook(Thread { CollectScheduler.stop() })

    telegramBot.startPolling()

    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) {
            json()
        }
        routing {
            get("/hello") {
                call.respond(Message("Hello from Ktor!"))
            }
        }
    }.start(wait = true)
}
