package com.jmvstv_v.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.jmvstv_v.config.AppConfig
import com.jmvstv_v.model.UserFilterEntry
import com.jmvstv_v.repository.FilterRepository
import com.jmvstv_v.repository.UserRepository
import com.jmvstv_v.scraper.CollectScheduler

/**
 * Builds and configures the Telegram bot with all command and callback handlers.
 *
 * @param userRepository   Repository for user registration and active filter management.
 * @param filterRepository Repository for CRUD operations on filters.
 * @param conversation     Multi-step filter creation/editing conversation handler.
 * @return Configured [Bot] instance ready for polling.
 */
fun buildTelegramBot(
    userRepository: UserRepository,
    filterRepository: FilterRepository,
    conversation: FilterConversation,
    collectScheduler: CollectScheduler
): Bot = bot {
    token = AppConfig.telegramBotToken
    dispatch {
        command("start") {
            val telegramId = message.from?.id ?: return@command
            val chatId = message.chat.id
            val username = message.from?.username
            bot.deleteMessage(ChatId.fromId(chatId), message.messageId)
            conversation.cancel(chatId)

            val isNew = userRepository.registerIfAbsent(telegramId, chatId, username)
            val reply = if (isNew) "Добро пожаловать! Вы зарегистрированы." else "С возвращением!"
            bot.sendMessage(chatId = ChatId.fromId(chatId), text = reply)
        }

        command("addfilter") {
            val telegramId = message.from?.id ?: return@command
            val chatId = message.chat.id
            bot.deleteMessage(ChatId.fromId(chatId), message.messageId)

            if (userRepository.findIdByTelegramId(telegramId) == null) {
                bot.sendMessage(ChatId.fromId(chatId), "Сначала выполните /start для регистрации.")
                return@command
            }

            conversation.start(bot, chatId)
        }

        command("setfilter") {
            val telegramId = message.from?.id ?: return@command
            val chatId = message.chat.id
            bot.deleteMessage(ChatId.fromId(chatId), message.messageId)

            val userId = userRepository.findIdByTelegramId(telegramId)
            if (userId == null) {
                bot.sendMessage(ChatId.fromId(chatId), "Сначала выполните /start для регистрации.")
                return@command
            }

            val filters = filterRepository.findByUserId(userId)
            if (filters.isEmpty()) {
                bot.sendMessage(ChatId.fromId(chatId), "У вас нет фильтров. Создайте фильтр командой /addfilter.")
                return@command
            }

            val keyboard = InlineKeyboardMarkup.create(
                filters.map { (id, name) ->
                    listOf(InlineKeyboardButton.CallbackData(text = name, callbackData = "setfilter:$id"))
                } + listOf(listOf(InlineKeyboardButton.CallbackData(text = "❌ Отмена", callbackData = "cancelselect")))
            )
            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = "Ваши фильтры. Нажмите для активации:",
                replyMarkup = keyboard
            )
        }

        callbackQuery {
            val data = callbackQuery.data
            if (!data.startsWith("setfilter:")) return@callbackQuery

            val filterId = data.removePrefix("setfilter:").toIntOrNull() ?: return@callbackQuery
            val telegramId = callbackQuery.from.id
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val messageId = callbackQuery.message?.messageId

            val userId = userRepository.findIdByTelegramId(telegramId) ?: return@callbackQuery
            val filter = filterRepository.findByIdAndUserId(filterId, userId)
            if (filter == null) {
                bot.answerCallbackQuery(callbackQuery.id, text = "Фильтр не найден")
                return@callbackQuery
            }

            filterRepository.setActiveFilter(userId, filterId)
            collectScheduler.runForUser(UserFilterEntry(chatId, userId, filter))
            bot.answerCallbackQuery(callbackQuery.id, text = "Фильтр «${filter.name}» активирован")
            if (messageId != null) bot.deleteMessage(ChatId.fromId(chatId), messageId)
        }

        command("editfilter") {
            val telegramId = message.from?.id ?: return@command
            val chatId = message.chat.id
            bot.deleteMessage(ChatId.fromId(chatId), message.messageId)

            val userId = userRepository.findIdByTelegramId(telegramId)
            if (userId == null) {
                bot.sendMessage(ChatId.fromId(chatId), "Сначала выполните /start для регистрации.")
                return@command
            }

            val filters = filterRepository.findByUserId(userId)
            if (filters.isEmpty()) {
                bot.sendMessage(ChatId.fromId(chatId), "У вас нет фильтров. Создайте фильтр командой /addfilter.")
                return@command
            }

            val keyboard = InlineKeyboardMarkup.create(
                filters.map { (id, name) ->
                    listOf(InlineKeyboardButton.CallbackData(text = name, callbackData = "editfilter:$id"))
                } + listOf(listOf(InlineKeyboardButton.CallbackData(text = "❌ Отмена", callbackData = "cancelselect")))
            )
            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = "Выберите фильтр для редактирования:",
                replyMarkup = keyboard
            )
        }

        callbackQuery {
            val data = callbackQuery.data
            if (!data.startsWith("editfilter:")) return@callbackQuery

            val filterId = data.removePrefix("editfilter:").toIntOrNull() ?: return@callbackQuery
            val telegramId = callbackQuery.from.id
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val messageId = callbackQuery.message?.messageId

            val userId = userRepository.findIdByTelegramId(telegramId) ?: return@callbackQuery
            val filter = filterRepository.findByIdAndUserId(filterId, userId)
            if (filter == null) {
                bot.answerCallbackQuery(callbackQuery.id, text = "Фильтр не найден")
                return@callbackQuery
            }

            bot.answerCallbackQuery(callbackQuery.id)
            if (messageId != null) bot.deleteMessage(ChatId.fromId(chatId), messageId)
            conversation.startEdit(bot, chatId, filter)
        }

        command("removefilter") {
            val telegramId = message.from?.id ?: return@command
            val chatId = message.chat.id
            bot.deleteMessage(ChatId.fromId(chatId), message.messageId)

            val userId = userRepository.findIdByTelegramId(telegramId)
            if (userId == null) {
                bot.sendMessage(ChatId.fromId(chatId), "Сначала выполните /start для регистрации.")
                return@command
            }

            val filters = filterRepository.findByUserId(userId)
            if (filters.isEmpty()) {
                bot.sendMessage(ChatId.fromId(chatId), "У вас нет фильтров.")
                return@command
            }

            val keyboard = InlineKeyboardMarkup.create(
                filters.map { (id, name) ->
                    listOf(
                        InlineKeyboardButton.CallbackData(
                            text = "🗑 $name",
                            callbackData = "removefilter:$id"
                        )
                    )
                } + listOf(listOf(InlineKeyboardButton.CallbackData(text = "❌ Отмена", callbackData = "cancelselect")))
            )
            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = "Выберите фильтр для удаления:",
                replyMarkup = keyboard
            )
        }

        callbackQuery {
            val data = callbackQuery.data
            if (!data.startsWith("removefilter:")) return@callbackQuery

            val filterId = data.removePrefix("removefilter:").toIntOrNull() ?: return@callbackQuery
            val telegramId = callbackQuery.from.id
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val messageId = callbackQuery.message?.messageId

            val userId = userRepository.findIdByTelegramId(telegramId) ?: return@callbackQuery
            val filter = filterRepository.findByIdAndUserId(filterId, userId)
            if (filter == null) {
                bot.answerCallbackQuery(callbackQuery.id, text = "Фильтр не найден")
                return@callbackQuery
            }

            val wasActive = filterRepository.clearActiveFilterIfMatches(userId, filterId)
            filterRepository.deleteById(filterId)

            val suffix = if (wasActive) " Активный фильтр сброшен." else ""
            bot.answerCallbackQuery(callbackQuery.id, text = "Фильтр «${filter.name}» удалён.$suffix")
            if (messageId != null) bot.deleteMessage(ChatId.fromId(chatId), messageId)
        }

        callbackQuery {
            val data = callbackQuery.data
            if (data != "cancelselect") return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val messageId = callbackQuery.message?.messageId
            bot.answerCallbackQuery(callbackQuery.id)
            if (messageId != null) bot.deleteMessage(ChatId.fromId(chatId), messageId)
        }

        callbackQuery {
            val data = callbackQuery.data
            if (!conversation.isConversationCallback(data)) return@callbackQuery
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val telegramId = callbackQuery.from.id
            val messageId = callbackQuery.message?.messageId
            if (conversation.isActive(chatId)) {
                conversation.handleCallback(bot, chatId, telegramId, callbackQuery.id, data, messageId)
            }
        }

        text {
            val chatId = message.chat.id
            val telegramId = message.from?.id ?: return@text
            val txt = message.text ?: return@text
            if (txt.startsWith("/")) return@text

            if (conversation.isActive(chatId)) {
                bot.deleteMessage(ChatId.fromId(chatId), message.messageId)
                conversation.handle(bot, chatId, telegramId, txt)
            }
        }
    }
}
