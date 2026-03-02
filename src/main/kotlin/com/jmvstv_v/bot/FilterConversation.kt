package com.jmvstv_v.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ReplyMarkup
import com.github.kotlintelegrambot.entities.KeyboardReplyMarkup
import com.github.kotlintelegrambot.entities.ReplyKeyboardRemove
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton
import com.jmvstv_v.model.AdType
import com.jmvstv_v.model.City
import com.jmvstv_v.model.Filter
import com.jmvstv_v.model.FilterDraft
import com.jmvstv_v.model.FilterStep
import com.jmvstv_v.model.Heating
import com.jmvstv_v.model.PropertyType
import com.jmvstv_v.repository.FilterRepository
import com.jmvstv_v.repository.UserRepository
import java.util.concurrent.ConcurrentHashMap

private const val SKIP = "Пропустить"
private const val DONE = "Готово"

/**
 * Manages multi-step filter creation and card-based editing conversations per Telegram chat.
 *
 * @param filterRepository Repository for persisting filter data.
 * @param userRepository   Repository for resolving user IDs.
 */
class FilterConversation(
    private val filterRepository: FilterRepository,
    private val userRepository: UserRepository
) {
    private val drafts = ConcurrentHashMap<Long, FilterDraft>()

    /**
     * Returns `true` if a conversation is currently active for the given [chatId].
     */
    fun isActive(chatId: Long): Boolean = drafts.containsKey(chatId)

    /**
     * Cancels any active conversation for the given [chatId].
     */
    fun cancel(chatId: Long) {
        drafts.remove(chatId)
    }

    /**
     * Returns `true` if [data] is a callback query payload belonging to this conversation.
     */
    fun isConversationCallback(data: String): Boolean =
        data.startsWith("editfield:") ||
        data == "saveedit" || data == "canceledit" ||
        data.startsWith("setcity:") || data == "skipcity" ||
        data.startsWith("togglept:") || data == "savept" || data == "skippt" ||
        data.startsWith("setadtype:") || data == "skipadtype" ||
        data.startsWith("setheating:") || data == "skipheating" ||
        data == "skipfield" || data == "backtocard"

    /**
     * Starts a new filter creation wizard for the given [chatId].
     */
    fun start(bot: Bot, chatId: Long) {
        val draft = FilterDraft()
        drafts[chatId] = draft
        sendCreationMessage(bot, chatId, draft, "Создание нового фильтра.\n\nВведите название:", ReplyKeyboardRemove())
    }

    /**
     * Opens the card-based editor for an existing [filter].
     *
     * @param bot    Telegram bot used to send the card.
     * @param chatId Telegram chat ID.
     * @param filter Existing filter to be edited.
     */
    fun startEdit(bot: Bot, chatId: Long, filter: Filter) {
        val ptSelection = filter.propertyType
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toMutableList() ?: mutableListOf()
        val draft = FilterDraft(
            step                  = FilterStep.CARD,
            editingFilterId       = filter.id,
            name                  = filter.name,
            city                  = filter.city,
            propertyType          = filter.propertyType,
            propertyTypeSelection = ptSelection,
            minRooms              = filter.minRooms,
            maxRooms              = filter.maxRooms,
            minPrice              = filter.minPrice,
            maxPrice              = filter.maxPrice,
            minArea               = filter.minArea,
            maxArea               = filter.maxArea,
            adType                = filter.adType,
            heating               = filter.heating
        )
        drafts[chatId] = draft
        showCard(bot, chatId, draft)
    }

    /**
     * Handles inline keyboard callbacks for the edit card.
     * Covers `editfield:*`, `saveedit`, `canceledit`, choice selection callbacks,
     * `skipfield`, and `backtocard`.
     *
     * @param bot             Telegram bot.
     * @param chatId          Telegram chat ID.
     * @param telegramId      Telegram user ID.
     * @param callbackQueryId ID of the callback query (for answering).
     * @param data            Callback data string.
     * @param messageId       Message ID of the card, captured from the callback.
     */
    fun handleCallback(
        bot: Bot,
        chatId: Long,
        telegramId: Long,
        callbackQueryId: String,
        data: String,
        messageId: Long? = null
    ) {
        val draft = drafts[chatId] ?: return
        if (draft.cardMessageId == null && messageId != null) {
            draft.cardMessageId = messageId
        }
        when {
            data == "saveedit" -> {
                bot.answerCallbackQuery(callbackQueryId, text = "Фильтр сохранён")
                drafts.remove(chatId)
                val prevId = draft.cardMessageId
                if (prevId != null) bot.deleteMessage(ChatId.fromId(chatId), prevId)
                save(bot, chatId, telegramId, draft)
            }
            data == "canceledit" -> {
                bot.answerCallbackQuery(callbackQueryId, text = "Редактирование отменено")
                drafts.remove(chatId)
                val prevId = draft.cardMessageId
                if (prevId != null) bot.deleteMessage(ChatId.fromId(chatId), prevId)
            }
            data == "backtocard" -> {
                bot.answerCallbackQuery(callbackQueryId)
                draft.step = FilterStep.CARD
                draft.promptMessageId = null
                showCard(bot, chatId, draft)
            }
            data == "skipfield" -> {
                bot.answerCallbackQuery(callbackQueryId)
                draft.promptMessageId = null
                when (draft.step) {
                    FilterStep.EDIT_ROOMS -> { draft.minRooms = null; draft.maxRooms = null }
                    FilterStep.EDIT_PRICE -> { draft.minPrice = null; draft.maxPrice = null }
                    FilterStep.EDIT_AREA  -> { draft.minArea  = null; draft.maxArea  = null }
                    else -> Unit
                }
                draft.step = FilterStep.CARD
                showCard(bot, chatId, draft)
            }
            data.startsWith("editfield:") && draft.step == FilterStep.CARD -> {
                bot.answerCallbackQuery(callbackQueryId)
                startFieldEdit(bot, chatId, draft, data.removePrefix("editfield:"))
            }
            data.startsWith("setcity:") -> {
                bot.answerCallbackQuery(callbackQueryId)
                draft.city = data.removePrefix("setcity:").toIntOrNull()
                draft.step = FilterStep.CARD
                showCard(bot, chatId, draft)
            }
            data == "skipcity" -> {
                bot.answerCallbackQuery(callbackQueryId)
                draft.city = null
                draft.step = FilterStep.CARD
                showCard(bot, chatId, draft)
            }
            data.startsWith("togglept:") -> {
                bot.answerCallbackQuery(callbackQueryId)
                val code = data.removePrefix("togglept:").toIntOrNull() ?: return
                if (code in draft.propertyTypeSelection) draft.propertyTypeSelection.remove(code)
                else draft.propertyTypeSelection.add(code)
                showPropertyTypeCard(bot, chatId, draft)
            }
            data == "savept" -> {
                bot.answerCallbackQuery(callbackQueryId)
                draft.propertyType = draft.propertyTypeSelection
                    .takeIf { it.isNotEmpty() }?.joinToString(",")
                draft.step = FilterStep.CARD
                showCard(bot, chatId, draft)
            }
            data == "skippt" -> {
                bot.answerCallbackQuery(callbackQueryId)
                draft.propertyType = null
                draft.propertyTypeSelection.clear()
                draft.step = FilterStep.CARD
                showCard(bot, chatId, draft)
            }
            data.startsWith("setadtype:") -> {
                bot.answerCallbackQuery(callbackQueryId)
                draft.adType = data.removePrefix("setadtype:").toIntOrNull()
                draft.step = FilterStep.CARD
                showCard(bot, chatId, draft)
            }
            data == "skipadtype" -> {
                bot.answerCallbackQuery(callbackQueryId)
                draft.adType = null
                draft.step = FilterStep.CARD
                showCard(bot, chatId, draft)
            }
            data.startsWith("setheating:") -> {
                bot.answerCallbackQuery(callbackQueryId)
                draft.heating = data.removePrefix("setheating:").toIntOrNull()
                draft.step = FilterStep.CARD
                showCard(bot, chatId, draft)
            }
            data == "skipheating" -> {
                bot.answerCallbackQuery(callbackQueryId)
                draft.heating = null
                draft.step = FilterStep.CARD
                showCard(bot, chatId, draft)
            }
        }
    }

    /**
     * Handles the next user text message for an active conversation.
     *
     * @param bot        Telegram bot.
     * @param chatId     Telegram chat ID.
     * @param telegramId Telegram user ID.
     * @param text       Text submitted by the user.
     */
    fun handle(bot: Bot, chatId: Long, telegramId: Long, text: String) {
        val draft = drafts[chatId] ?: return
        val trimmed = text.trim()

        when (draft.step) {
            FilterStep.CARD -> Unit

            FilterStep.NAME -> {
                if (trimmed.isEmpty()) {
                    sendCreationMessage(bot, chatId, draft, "Название не может быть пустым. Введите название:")
                    return
                }
                draft.name = trimmed
                draft.step = FilterStep.CITY
                askCity(bot, chatId, draft)
            }

            FilterStep.CITY -> {
                val city = City.fromDisplayName(trimmed)
                if (city == null) {
                    sendCreationMessage(bot, chatId, draft, "Выберите город из предложенных вариантов:")
                    askCity(bot, chatId, draft)
                    return
                }
                draft.city = city.code
                draft.step = FilterStep.PROPERTY_TYPE
                askPropertyType(bot, chatId, draft, draft.propertyTypeSelection)
            }

            FilterStep.PROPERTY_TYPE -> {
                when (trimmed) {
                    DONE -> {
                        draft.propertyType = draft.propertyTypeSelection
                            .takeIf { it.isNotEmpty() }
                            ?.joinToString(",")
                        draft.step = FilterStep.ROOMS
                        askWithSkip(bot, chatId, draft, "Количество комнат (число или диапазон, например: 2 или 1-3):")
                    }
                    SKIP -> {
                        draft.propertyType = null
                        draft.propertyTypeSelection.clear()
                        draft.step = FilterStep.ROOMS
                        askWithSkip(bot, chatId, draft, "Количество комнат (число или диапазон, например: 2 или 1-3):")
                    }
                    else -> {
                        val pt = PropertyType.fromDisplayName(trimmed)
                        if (pt != null) {
                            if (pt.code in draft.propertyTypeSelection) {
                                draft.propertyTypeSelection.remove(pt.code)
                            } else {
                                draft.propertyTypeSelection.add(pt.code)
                            }
                        }
                        askPropertyType(bot, chatId, draft, draft.propertyTypeSelection)
                    }
                }
            }

            FilterStep.ROOMS -> handleRoomsInput(bot, chatId, draft, trimmed) {
                draft.step = FilterStep.MIN_PRICE
                askWithSkip(bot, chatId, draft, "Минимальная цена (€):")
            }

            FilterStep.MIN_PRICE -> {
                if (trimmed != SKIP) {
                    val v = trimmed.toIntOrNull()
                    if (v == null || v < 0) {
                        sendCreationMessage(bot, chatId, draft, "Введите целое неотрицательное число или нажмите «$SKIP»:")
                        return
                    }
                    draft.minPrice = v
                }
                draft.step = FilterStep.MAX_PRICE
                askWithSkip(bot, chatId, draft, "Максимальная цена (€):")
            }

            FilterStep.MAX_PRICE -> {
                if (trimmed != SKIP) {
                    val v = trimmed.toIntOrNull()
                    if (v == null || v < 0) {
                        sendCreationMessage(bot, chatId, draft, "Введите целое неотрицательное число или нажмите «$SKIP»:")
                        return
                    }
                    draft.maxPrice = v
                }
                draft.step = FilterStep.MIN_AREA
                askWithSkip(bot, chatId, draft, "Минимальная площадь (м²):")
            }

            FilterStep.MIN_AREA -> {
                if (trimmed != SKIP) {
                    val v = trimmed.toIntOrNull()
                    if (v == null || v < 0) {
                        sendCreationMessage(bot, chatId, draft, "Введите целое неотрицательное число или нажмите «$SKIP»:")
                        return
                    }
                    draft.minArea = v
                }
                draft.step = FilterStep.MAX_AREA
                askWithSkip(bot, chatId, draft, "Максимальная площадь (м²):")
            }

            FilterStep.MAX_AREA -> {
                if (trimmed != SKIP) {
                    val v = trimmed.toIntOrNull()
                    if (v == null || v < 0) {
                        sendCreationMessage(bot, chatId, draft, "Введите целое неотрицательное число или нажмите «$SKIP»:")
                        return
                    }
                    draft.maxArea = v
                }
                draft.step = FilterStep.AD_TYPE
                askAdType(bot, chatId, draft)
            }

            FilterStep.AD_TYPE -> {
                if (trimmed != SKIP) {
                    val at = AdType.fromDisplayName(trimmed)
                    if (at == null) {
                        sendCreationMessage(bot, chatId, draft, "Выберите вариант из предложенных:")
                        askAdType(bot, chatId, draft)
                        return
                    }
                    draft.adType = at.code
                }
                draft.step = FilterStep.HEATING
                askHeating(bot, chatId, draft)
            }

            FilterStep.HEATING -> {
                if (trimmed != SKIP) {
                    val h = Heating.fromDisplayName(trimmed)
                    if (h == null) {
                        sendCreationMessage(bot, chatId, draft, "Выберите вариант из предложенных:")
                        askHeating(bot, chatId, draft)
                        return
                    }
                    draft.heating = h.code
                }
                drafts.remove(chatId)
                save(bot, chatId, telegramId, draft)
            }

            FilterStep.EDIT_NAME -> {
                if (trimmed.isEmpty()) {
                    sendPromptMessage(bot, chatId, draft, "Название не может быть пустым:",
                        InlineKeyboardMarkup.create(listOf(listOf(btn("↩ Назад", "backtocard")))))
                    return
                }
                draft.name = trimmed
                returnToCard(bot, chatId, draft)
            }

            FilterStep.EDIT_ROOMS -> handleRoomsInput(bot, chatId, draft, trimmed) {
                returnToCard(bot, chatId, draft)
            }

            FilterStep.EDIT_PRICE -> {
                if (trimmed == SKIP) {
                    draft.minPrice = null
                    draft.maxPrice = null
                    returnToCard(bot, chatId, draft)
                    return
                }
                val range = parseIntRange(trimmed)
                if (range == null) {
                    sendPromptMessage(bot, chatId, draft, "Введите число или диапазон (например: 400-800 или 400), или нажмите «$SKIP» для сброса:",
                        InlineKeyboardMarkup.create(listOf(listOf(btn("Пропустить (сбросить)", "skipfield"), btn("↩ Назад", "backtocard")))))
                    return
                }
                draft.minPrice = range.first
                draft.maxPrice = range.second
                returnToCard(bot, chatId, draft)
            }

            FilterStep.EDIT_AREA -> {
                if (trimmed == SKIP) {
                    draft.minArea = null
                    draft.maxArea = null
                    returnToCard(bot, chatId, draft)
                    return
                }
                val range = parseIntRange(trimmed)
                if (range == null) {
                    sendPromptMessage(bot, chatId, draft, "Введите число или диапазон (например: 40-120 или 40), или нажмите «$SKIP» для сброса:",
                        InlineKeyboardMarkup.create(listOf(listOf(btn("Пропустить (сбросить)", "skipfield"), btn("↩ Назад", "backtocard")))))
                    return
                }
                draft.minArea = range.first
                draft.maxArea = range.second
                returnToCard(bot, chatId, draft)
            }
        }
    }

    private fun startFieldEdit(bot: Bot, chatId: Long, draft: FilterDraft, field: String) {
        when (field) {
            "name" -> {
                draft.step = FilterStep.EDIT_NAME
                sendPromptMessage(
                    bot, chatId, draft,
                    "Введите новое название:",
                    InlineKeyboardMarkup.create(listOf(listOf(btn("↩ Назад", "backtocard"))))
                )
            }
            "city"         -> showCityCard(bot, chatId, draft)
            "propertytype" -> showPropertyTypeCard(bot, chatId, draft)
            "rooms" -> {
                draft.step = FilterStep.EDIT_ROOMS
                sendPromptMessage(
                    bot, chatId, draft,
                    "Комнат (число или диапазон: 2 или 1-3):",
                    InlineKeyboardMarkup.create(listOf(
                        listOf(btn("Пропустить (сбросить)", "skipfield"), btn("↩ Назад", "backtocard"))
                    ))
                )
            }
            "price" -> {
                draft.step = FilterStep.EDIT_PRICE
                sendPromptMessage(
                    bot, chatId, draft,
                    "Цена (€): диапазон (400-800) или минимум (400):",
                    InlineKeyboardMarkup.create(listOf(
                        listOf(btn("Пропустить (сбросить)", "skipfield"), btn("↩ Назад", "backtocard"))
                    ))
                )
            }
            "area" -> {
                draft.step = FilterStep.EDIT_AREA
                sendPromptMessage(
                    bot, chatId, draft,
                    "Площадь (м²): диапазон (40-120) или минимум (40):",
                    InlineKeyboardMarkup.create(listOf(
                        listOf(btn("Пропустить (сбросить)", "skipfield"), btn("↩ Назад", "backtocard"))
                    ))
                )
            }
            "adtype"  -> showAdTypeCard(bot, chatId, draft)
            "heating" -> showHeatingCard(bot, chatId, draft)
        }
    }

    private fun showCityCard(bot: Bot, chatId: Long, draft: FilterDraft) {
        val rows = City.entries.chunked(2).map { row ->
            row.map { btn(it.displayName, "setcity:${it.code}") }
        } + listOf(listOf(btn("Пропустить (сбросить)", "skipcity"), btn("↩ Назад", "backtocard")))
        updateCardMessage(bot, chatId, draft, "Выберите город:", InlineKeyboardMarkup.create(rows))
    }

    private fun showPropertyTypeCard(bot: Bot, chatId: Long, draft: FilterDraft) {
        val rows = PropertyType.entries.map { pt ->
            val check = if (pt.code in draft.propertyTypeSelection) "✓ " else ""
            listOf(btn("$check${pt.displayName}", "togglept:${pt.code}"))
        } + listOf(listOf(btn("✅ Сохранить выбор", "savept"), btn("Пропустить (сбросить)", "skippt")))
        val selectedNames = draft.propertyTypeSelection
            .mapNotNull { code -> PropertyType.entries.firstOrNull { it.code == code }?.displayName }
        val selectionText = if (selectedNames.isEmpty()) "не выбрано" else selectedNames.joinToString(", ")
        updateCardMessage(
            bot, chatId, draft,
            "Тип недвижимости (выбрано: $selectionText):",
            InlineKeyboardMarkup.create(rows)
        )
    }

    private fun showAdTypeCard(bot: Bot, chatId: Long, draft: FilterDraft) {
        val rows = AdType.entries.map { listOf(btn(it.displayName, "setadtype:${it.code}")) } +
                listOf(listOf(btn("Пропустить (любой)", "skipadtype"), btn("↩ Назад", "backtocard")))
        updateCardMessage(bot, chatId, draft, "Тип объявления:", InlineKeyboardMarkup.create(rows))
    }

    private fun showHeatingCard(bot: Bot, chatId: Long, draft: FilterDraft) {
        val rows = Heating.entries.map { listOf(btn(it.displayName, "setheating:${it.code}")) } +
                listOf(listOf(btn("Пропустить (любое)", "skipheating"), btn("↩ Назад", "backtocard")))
        updateCardMessage(bot, chatId, draft, "Тип отопления:", InlineKeyboardMarkup.create(rows))
    }

    private fun returnToCard(bot: Bot, chatId: Long, draft: FilterDraft) {
        draft.step = FilterStep.CARD
        val promptId = draft.promptMessageId
        if (promptId != null) {
            bot.deleteMessage(ChatId.fromId(chatId), promptId)
            draft.promptMessageId = null
        }
        showCard(bot, chatId, draft)
    }

    private fun showCard(bot: Bot, chatId: Long, draft: FilterDraft) {
        val name = draft.name ?: "Без названия"

        val cityLabel = City.fromCode(draft.city)?.displayName ?: "любой"

        val ptLabel = draft.propertyType
            ?.split(",")
            ?.mapNotNull { code -> PropertyType.entries.firstOrNull { it.code == code.trim().toIntOrNull() }?.displayName }
            ?.joinToString(", ")
            ?: "любой"

        val roomsLabel = when {
            draft.minRooms != null && draft.maxRooms != null -> "${draft.minRooms!!.fmtRooms()}-${draft.maxRooms!!.fmtRooms()}"
            draft.minRooms != null                           -> draft.minRooms!!.fmtRooms()
            else                                             -> "любое"
        }

        val priceLabel = when {
            draft.minPrice != null && draft.maxPrice != null -> "${draft.minPrice}-${draft.maxPrice}€"
            draft.minPrice != null                           -> "от ${draft.minPrice}€"
            draft.maxPrice != null                           -> "до ${draft.maxPrice}€"
            else                                             -> "любая"
        }

        val areaLabel = when {
            draft.minArea != null && draft.maxArea != null -> "${draft.minArea}-${draft.maxArea}м²"
            draft.minArea != null                          -> "от ${draft.minArea}м²"
            draft.maxArea != null                          -> "до ${draft.maxArea}м²"
            else                                           -> "любая"
        }

        val adTypeLabel = draft.adType
            ?.let { code -> AdType.entries.firstOrNull { it.code == code }?.displayName }
            ?: "любой"

        val heatingLabel = draft.heating
            ?.let { code -> Heating.entries.firstOrNull { it.code == code }?.displayName }
            ?: "любое"

        val keyboard = InlineKeyboardMarkup.create(
            listOf(
                listOf(btn("🏷 $name", "editfield:name")),
                listOf(btn("🌆 Город: $cityLabel", "editfield:city")),
                listOf(btn("🏠 Тип: $ptLabel", "editfield:propertytype")),
                listOf(btn("🚪 Комнат: $roomsLabel", "editfield:rooms")),
                listOf(btn("💰 Цена: $priceLabel", "editfield:price")),
                listOf(btn("📐 Площадь: $areaLabel", "editfield:area")),
                listOf(btn("👤 Объявление: $adTypeLabel", "editfield:adtype")),
                listOf(btn("🔥 Отопление: $heatingLabel", "editfield:heating")),
                listOf(btn("✅ Сохранить", "saveedit"), btn("❌ Отмена", "canceledit"))
            )
        )
        updateCardMessage(bot, chatId, draft, "Редактирование фильтра «$name»:", keyboard)
    }

    /**
     * Deletes the previous card message if present, then sends a new one.
     * The new message ID is captured on the next incoming callback via [handleCallback].
     */
    private fun updateCardMessage(
        bot: Bot,
        chatId: Long,
        draft: FilterDraft,
        text: String,
        keyboard: InlineKeyboardMarkup
    ) {
        val prevId = draft.cardMessageId
        if (prevId != null) bot.deleteMessage(ChatId.fromId(chatId), prevId)
        draft.cardMessageId = null
        draft.promptMessageId = null
        bot.sendMessage(
            chatId      = ChatId.fromId(chatId),
            text        = text,
            replyMarkup = keyboard
        )
    }

    /**
     * Deletes the previous card message if present, sends a text-input prompt,
     * and tracks the sent message ID in [FilterDraft.promptMessageId] via reflection.
     */
    private fun sendPromptMessage(
        bot: Bot,
        chatId: Long,
        draft: FilterDraft,
        text: String,
        keyboard: InlineKeyboardMarkup
    ) {
        val prevId = draft.cardMessageId
        if (prevId != null) bot.deleteMessage(ChatId.fromId(chatId), prevId)
        draft.cardMessageId = null
        draft.promptMessageId = null
        val result = bot.sendMessage(chatId = ChatId.fromId(chatId), text = text, replyMarkup = keyboard)
        draft.promptMessageId = try {
            result.javaClass.getDeclaredField("value").also { it.isAccessible = true }.get(result)
                ?.let { msg ->
                    msg.javaClass.getDeclaredField("messageId").also { it.isAccessible = true }.get(msg) as? Long
                }
        } catch (_: Exception) { null }
    }

    private fun btn(text: String, data: String) = InlineKeyboardButton.CallbackData(text = text, callbackData = data)

    /** Sends a message. In creation mode tracks ID for bulk deletion; in edit mode replaces promptMessageId. */
    private fun sendCreationMessage(bot: Bot, chatId: Long, draft: FilterDraft, text: String, replyMarkup: ReplyMarkup? = null) {
        if (draft.editingFilterId != null) {
            val prevId = draft.promptMessageId
            if (prevId != null) bot.deleteMessage(ChatId.fromId(chatId), prevId)
            draft.promptMessageId = null
        }
        val result = bot.sendMessage(chatId = ChatId.fromId(chatId), text = text, replyMarkup = replyMarkup)
        val msgId = try {
            result.javaClass.getDeclaredField("value").also { it.isAccessible = true }.get(result)
                ?.let { msg -> msg.javaClass.getDeclaredField("messageId").also { it.isAccessible = true }.get(msg) as? Long }
        } catch (_: Exception) { null }
        if (draft.editingFilterId != null) {
            draft.promptMessageId = msgId
        } else if (msgId != null) {
            draft.creationMessageIds.add(msgId)
        }
    }

    private fun handleRoomsInput(
        bot: Bot,
        chatId: Long,
        draft: FilterDraft,
        trimmed: String,
        onSuccess: () -> Unit
    ) {
        if (trimmed == SKIP) {
            draft.minRooms = null
            draft.maxRooms = null
            onSuccess()
            return
        }
        val part = Regex("^\\d+(\\.5)?$")
        val errorMsg = "Введите число или диапазон с шагом 0.5 (например: 2 или 1.5-3):"
        if (trimmed.contains('-')) {
            val parts = trimmed.split('-')
            if (parts.size != 2 || !parts[0].matches(part) || !parts[1].matches(part)) {
                sendCreationMessage(bot, chatId, draft, errorMsg)
                return
            }
            val min = parts[0].toDouble()
            val max = parts[1].toDouble()
            if (max < min) {
                sendCreationMessage(bot, chatId, draft, "Максимум должен быть >= минимума. Введите диапазон:")
                return
            }
            draft.minRooms = min
            draft.maxRooms = max
        } else {
            if (!trimmed.matches(part)) {
                sendCreationMessage(bot, chatId, draft, errorMsg)
                return
            }
            draft.minRooms = trimmed.toDouble()
            draft.maxRooms = null
        }
        onSuccess()
    }

    private fun Double.fmtRooms() = if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()

    private fun parseIntRange(input: String): Pair<Int, Int?>? {
        return if (input.contains('-')) {
            val parts = input.split('-')
            if (parts.size != 2) return null
            val min = parts[0].trim().toIntOrNull() ?: return null
            val max = parts[1].trim().toIntOrNull() ?: return null
            if (min < 0 || max < 0 || max < min) return null
            min to max
        } else {
            val v = input.toIntOrNull() ?: return null
            if (v < 0) return null
            v to null
        }
    }

    private fun askCity(bot: Bot, chatId: Long, draft: FilterDraft) {
        val keyboard = KeyboardReplyMarkup(
            keyboard = City.entries.chunked(2).map { row -> row.map { KeyboardButton(it.displayName) } },
            resizeKeyboard = true,
            oneTimeKeyboard = true
        )
        sendCreationMessage(bot, chatId, draft, "Выберите город:", keyboard)
    }

    private fun askPropertyType(bot: Bot, chatId: Long, draft: FilterDraft, selected: List<Int> = emptyList()) {
        val selectedNames = selected
            .mapNotNull { code -> PropertyType.entries.firstOrNull { it.code == code }?.displayName }
        val selectionText = if (selectedNames.isEmpty()) "не выбрано" else selectedNames.joinToString(", ")
        val keyboard = KeyboardReplyMarkup(
            keyboard = PropertyType.entries.chunked(2).map { row -> row.map { KeyboardButton(it.displayName) } }
                    + listOf(listOf(KeyboardButton(DONE), KeyboardButton(SKIP))),
            resizeKeyboard = true,
            oneTimeKeyboard = false
        )
        sendCreationMessage(bot, chatId, draft, "Тип недвижимости (выбрано: $selectionText):", keyboard)
    }

    private fun askAdType(bot: Bot, chatId: Long, draft: FilterDraft) {
        val keyboard = KeyboardReplyMarkup(
            keyboard = listOf(
                AdType.entries.map { KeyboardButton(it.displayName) },
                listOf(KeyboardButton(SKIP))
            ),
            resizeKeyboard = true,
            oneTimeKeyboard = true
        )
        sendCreationMessage(bot, chatId, draft, "Тип объявления:", keyboard)
    }

    private fun askHeating(bot: Bot, chatId: Long, draft: FilterDraft) {
        val keyboard = KeyboardReplyMarkup(
            keyboard = listOf(
                Heating.entries.map { KeyboardButton(it.displayName) },
                listOf(KeyboardButton(SKIP))
            ),
            resizeKeyboard = true,
            oneTimeKeyboard = true
        )
        sendCreationMessage(bot, chatId, draft, "Тип отопления:", keyboard)
    }

    private fun askWithSkip(bot: Bot, chatId: Long, draft: FilterDraft, prompt: String) {
        val keyboard = KeyboardReplyMarkup(
            keyboard = listOf(listOf(KeyboardButton(SKIP))),
            resizeKeyboard = true,
            oneTimeKeyboard = true
        )
        sendCreationMessage(bot, chatId, draft, prompt, keyboard)
    }

    private fun save(bot: Bot, chatId: Long, telegramId: Long, draft: FilterDraft) {
        val editingFilterId = draft.editingFilterId

        if (editingFilterId != null) {
            filterRepository.update(editingFilterId, draft)
            return
        }

        val userId = userRepository.findIdByTelegramId(telegramId)
        if (userId == null) {
            draft.creationMessageIds.forEach { bot.deleteMessage(ChatId.fromId(chatId), it) }
            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = "Ошибка: пользователь не найден. Выполните /start сначала.",
                replyMarkup = ReplyKeyboardRemove()
            )
            return
        }

        filterRepository.insert(userId, draft)
        draft.creationMessageIds.forEach { bot.deleteMessage(ChatId.fromId(chatId), it) }
    }
}
