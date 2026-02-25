package com.jmvstv_v.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.KeyboardReplyMarkup
import com.github.kotlintelegrambot.entities.ReplyKeyboardRemove
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton
import com.jmvstv_v.model.AdType
import com.jmvstv_v.model.City
import com.jmvstv_v.model.FiltersTable
import com.jmvstv_v.model.Heating
import com.jmvstv_v.model.PropertyType
import com.jmvstv_v.model.UsersTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.ConcurrentHashMap

private const val SKIP = "Пропустить"

enum class FilterStep {
    NAME, CITY, PROPERTY_TYPE, ROOMS,
    MIN_PRICE, MAX_PRICE, MIN_AREA, MAX_AREA,
    AD_TYPE, HEATING
}

data class FilterDraft(
    var step: FilterStep = FilterStep.NAME,
    var name: String? = null,
    var city: Int? = null,
    var propertyType: Int? = null,
    var minRooms: Int? = null,
    var maxRooms: Int? = null,
    var minPrice: Int? = null,
    var maxPrice: Int? = null,
    var minArea: Int? = null,
    var maxArea: Int? = null,
    var adType: Int? = null,
    var heating: Int? = null
)

object FilterConversation {
    private val drafts = ConcurrentHashMap<Long, FilterDraft>()

    fun isActive(chatId: Long) = drafts.containsKey(chatId)

    fun cancel(chatId: Long) {
        drafts.remove(chatId)
    }

    fun start(bot: Bot, chatId: Long) {
        drafts[chatId] = FilterDraft()
        bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = "Создание нового фильтра.\n\nВведите название:",
            replyMarkup = ReplyKeyboardRemove()
        )
    }

    fun handle(bot: Bot, chatId: Long, telegramId: Long, text: String) {
        val draft = drafts[chatId] ?: return
        val trimmed = text.trim()

        when (draft.step) {
            FilterStep.NAME -> {
                if (trimmed.isEmpty()) {
                    bot.sendMessage(ChatId.fromId(chatId), "Название не может быть пустым. Введите название:")
                    return
                }
                draft.name = trimmed
                draft.step = FilterStep.CITY
                askCity(bot, chatId)
            }

            FilterStep.CITY -> {
                val city = City.fromDisplayName(trimmed)
                if (city == null) {
                    bot.sendMessage(ChatId.fromId(chatId), "Выберите город из предложенных вариантов:")
                    askCity(bot, chatId)
                    return
                }
                draft.city = city.code
                draft.step = FilterStep.PROPERTY_TYPE
                askPropertyType(bot, chatId)
            }

            FilterStep.PROPERTY_TYPE -> {
                val pt = PropertyType.fromDisplayName(trimmed)
                if (pt == null) {
                    bot.sendMessage(ChatId.fromId(chatId), "Выберите тип из предложенных вариантов:")
                    askPropertyType(bot, chatId)
                    return
                }
                draft.propertyType = pt.code
                draft.step = FilterStep.ROOMS
                askWithSkip(bot, chatId, "Количество комнат (число или диапазон, например: 2 или 1-3):")
            }

            FilterStep.ROOMS -> {
                if (trimmed != SKIP) {
                    if (!trimmed.matches(Regex("\\d+(-\\d+)?"))) {
                        bot.sendMessage(ChatId.fromId(chatId), "Введите число или диапазон (например: 2 или 1-3):")
                        return
                    }
                    if (trimmed.contains('-')) {
                        val parts = trimmed.split('-')
                        draft.minRooms = parts[0].toInt()
                        draft.maxRooms = parts[1].toInt()
                    } else {
                        draft.minRooms = trimmed.toInt()
                        draft.maxRooms = null
                    }
                }
                draft.step = FilterStep.MIN_PRICE
                askWithSkip(bot, chatId, "Минимальная цена (€):")
            }

            FilterStep.MIN_PRICE -> {
                if (trimmed != SKIP) {
                    val v = trimmed.toIntOrNull()
                    if (v == null || v < 0) {
                        bot.sendMessage(ChatId.fromId(chatId), "Введите целое неотрицательное число или нажмите «$SKIP»:")
                        return
                    }
                    draft.minPrice = v
                }
                draft.step = FilterStep.MAX_PRICE
                askWithSkip(bot, chatId, "Максимальная цена (€):")
            }

            FilterStep.MAX_PRICE -> {
                if (trimmed != SKIP) {
                    val v = trimmed.toIntOrNull()
                    if (v == null || v < 0) {
                        bot.sendMessage(ChatId.fromId(chatId), "Введите целое неотрицательное число или нажмите «$SKIP»:")
                        return
                    }
                    draft.maxPrice = v
                }
                draft.step = FilterStep.MIN_AREA
                askWithSkip(bot, chatId, "Минимальная площадь (м²):")
            }

            FilterStep.MIN_AREA -> {
                if (trimmed != SKIP) {
                    val v = trimmed.toIntOrNull()
                    if (v == null || v < 0) {
                        bot.sendMessage(ChatId.fromId(chatId), "Введите целое неотрицательное число или нажмите «$SKIP»:")
                        return
                    }
                    draft.minArea = v
                }
                draft.step = FilterStep.MAX_AREA
                askWithSkip(bot, chatId, "Максимальная площадь (м²):")
            }

            FilterStep.MAX_AREA -> {
                if (trimmed != SKIP) {
                    val v = trimmed.toIntOrNull()
                    if (v == null || v < 0) {
                        bot.sendMessage(ChatId.fromId(chatId), "Введите целое неотрицательное число или нажмите «$SKIP»:")
                        return
                    }
                    draft.maxArea = v
                }
                draft.step = FilterStep.AD_TYPE
                askAdType(bot, chatId)
            }

            FilterStep.AD_TYPE -> {
                if (trimmed != SKIP) {
                    val at = AdType.fromDisplayName(trimmed)
                    if (at == null) {
                        bot.sendMessage(ChatId.fromId(chatId), "Выберите вариант из предложенных:")
                        askAdType(bot, chatId)
                        return
                    }
                    draft.adType = at.code
                }
                draft.step = FilterStep.HEATING
                askHeating(bot, chatId)
            }

            FilterStep.HEATING -> {
                if (trimmed != SKIP) {
                    val h = Heating.fromDisplayName(trimmed)
                    if (h == null) {
                        bot.sendMessage(ChatId.fromId(chatId), "Выберите вариант из предложенных:")
                        askHeating(bot, chatId)
                        return
                    }
                    draft.heating = h.code
                }
                drafts.remove(chatId)
                save(bot, chatId, telegramId, draft)
            }
        }
    }

    // --- keyboard helpers ---

    private fun askCity(bot: Bot, chatId: Long) {
        val keyboard = KeyboardReplyMarkup(
            keyboard = City.entries.chunked(2).map { row -> row.map { KeyboardButton(it.displayName) } },
            resizeKeyboard = true,
            oneTimeKeyboard = true
        )
        bot.sendMessage(ChatId.fromId(chatId), "Выберите город:", replyMarkup = keyboard)
    }

    private fun askPropertyType(bot: Bot, chatId: Long) {
        val keyboard = KeyboardReplyMarkup(
            keyboard = PropertyType.entries.chunked(2).map { row -> row.map { KeyboardButton(it.displayName) } },
            resizeKeyboard = true,
            oneTimeKeyboard = true
        )
        bot.sendMessage(ChatId.fromId(chatId), "Тип недвижимости:", replyMarkup = keyboard)
    }

    private fun askAdType(bot: Bot, chatId: Long) {
        val keyboard = KeyboardReplyMarkup(
            keyboard = listOf(
                AdType.entries.map { KeyboardButton(it.displayName) },
                listOf(KeyboardButton(SKIP))
            ),
            resizeKeyboard = true,
            oneTimeKeyboard = true
        )
        bot.sendMessage(ChatId.fromId(chatId), "Тип объявления:", replyMarkup = keyboard)
    }

    private fun askHeating(bot: Bot, chatId: Long) {
        val keyboard = KeyboardReplyMarkup(
            keyboard = listOf(
                Heating.entries.map { KeyboardButton(it.displayName) },
                listOf(KeyboardButton(SKIP))
            ),
            resizeKeyboard = true,
            oneTimeKeyboard = true
        )
        bot.sendMessage(ChatId.fromId(chatId), "Тип отопления:", replyMarkup = keyboard)
    }

    private fun askWithSkip(bot: Bot, chatId: Long, prompt: String) {
        val keyboard = KeyboardReplyMarkup(
            keyboard = listOf(listOf(KeyboardButton(SKIP))),
            resizeKeyboard = true,
            oneTimeKeyboard = true
        )
        bot.sendMessage(ChatId.fromId(chatId), prompt, replyMarkup = keyboard)
    }

    // --- DB save ---

    private fun save(bot: Bot, chatId: Long, telegramId: Long, draft: FilterDraft) {
        val filterName = draft.name ?: "Без названия"
        transaction {
            val userId = UsersTable
                .selectAll()
                .where { UsersTable.telegramId eq telegramId }
                .map { it[UsersTable.id] }
                .firstOrNull()

            if (userId == null) {
                bot.sendMessage(
                    ChatId.fromId(chatId),
                    "Ошибка: пользователь не найден. Выполните /start сначала.",
                    replyMarkup = ReplyKeyboardRemove()
                )
                return@transaction
            }

            FiltersTable.insert {
                it[name] = filterName
                it[FiltersTable.userId] = userId
                it[active] = false
                it[city] = draft.city
                it[propertyType] = draft.propertyType
                it[minRooms] = draft.minRooms
                it[maxRooms] = draft.maxRooms
                it[minPrice] = draft.minPrice
                it[maxPrice] = draft.maxPrice
                it[minArea] = draft.minArea
                it[maxArea] = draft.maxArea
                it[adType] = draft.adType
                it[heating] = draft.heating  // Int? (code of Heating enum)
            }
        }

        bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = "Фильтр «$filterName» сохранён.",
            replyMarkup = ReplyKeyboardRemove()
        )
    }
}
