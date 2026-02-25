package com.jmvstv_v

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import com.jmvstv_v.bot.FilterConversation
import com.jmvstv_v.model.City
import com.jmvstv_v.repository.ExposedFilterRepository
import com.jmvstv_v.repository.ExposedUserRepository
import com.jmvstv_v.model.Filter
import com.jmvstv_v.model.FiltersTable
import com.jmvstv_v.model.UsersTable
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TelegramCommandsTest {

    private val testTgId   = 100001L
    private val testChatId = 200001L

    private lateinit var bot: Bot
    private val filterConversation = FilterConversation(ExposedFilterRepository(), ExposedUserRepository())

    @BeforeAll
    fun initDb() {
        TestDatabase.init()
    }

    @BeforeEach
    fun setup() {
        TestDatabase.clear()
        filterConversation.cancel(testChatId)
        bot = mockk(relaxed = true)
    }

    // ── /start ────────────────────────────────────────────────────────────────

    @Test
    fun `start - new user is registered in DB`() {
        val tgId   = testTgId
        val chatId = testChatId

        val isNew = transaction {
            val exists = UsersTable.selectAll()
                .where { UsersTable.telegramId eq tgId }.count() > 0
            if (!exists) {
                UsersTable.insertIgnore {
                    it[UsersTable.telegramId] = tgId
                    it[UsersTable.chatId]     = chatId
                    it[UsersTable.username]   = "alice"
                }
                true
            } else false
        }

        assertTrue(isNew)
        val count = transaction {
            UsersTable.selectAll().where { UsersTable.telegramId eq tgId }.count()
        }
        assertEquals(1L, count)
    }

    @Test
    fun `start - existing user produces no duplicate row`() {
        val tgId   = testTgId
        val chatId = testChatId

        transaction {
            UsersTable.insertIgnore {
                it[UsersTable.telegramId] = tgId
                it[UsersTable.chatId]     = chatId
                it[UsersTable.username]   = "alice"
            }
        }

        val isNew = transaction {
            val exists = UsersTable.selectAll()
                .where { UsersTable.telegramId eq tgId }.count() > 0
            if (!exists) {
                UsersTable.insertIgnore {
                    it[UsersTable.telegramId] = tgId
                    it[UsersTable.chatId]     = chatId
                    it[UsersTable.username]   = "alice"
                }
                true
            } else false
        }

        assertFalse(isNew)
        val count = transaction {
            UsersTable.selectAll().where { UsersTable.telegramId eq tgId }.count()
        }
        assertEquals(1L, count)
    }

    // ── /addfilter ────────────────────────────────────────────────────────────

    @Test
    fun `addfilter - unregistered user triggers registration prompt`() {
        val tgId   = testTgId
        val chatId = testChatId

        val userExists = transaction {
            UsersTable.selectAll().where { UsersTable.telegramId eq tgId }.count() > 0
        }
        assertFalse(userExists)

        if (!userExists) {
            bot.sendMessage(ChatId.fromId(chatId), "Сначала выполните /start для регистрации.")
        }

        verify { bot.sendMessage(ChatId.fromId(chatId), "Сначала выполните /start для регистрации.") }
        assertFalse(filterConversation.isActive(chatId))
    }

    @Test
    fun `addfilter - registered user starts FilterConversation`() {
        insertUser()
        filterConversation.start(bot, testChatId)

        assertTrue(filterConversation.isActive(testChatId))
        verify { bot.sendMessage(chatId = ChatId.fromId(testChatId), text = any(), replyMarkup = any()) }
    }

    // ── /setfilter ────────────────────────────────────────────────────────────

    @Test
    fun `setfilter - unregistered user returns null filter list`() {
        val tgId = testTgId
        val filters = transaction {
            UsersTable.selectAll().where { UsersTable.telegramId eq tgId }.singleOrNull()
                ?: return@transaction null
            emptyList<Pair<Int, String>>()
        }
        assertNull(filters)
    }

    @Test
    fun `setfilter - registered user with no filters returns empty list`() {
        val userId = insertUser()

        val filters = transaction {
            FiltersTable.selectAll().where { FiltersTable.userId eq userId }
                .map { it[FiltersTable.id] to it[FiltersTable.name] }
        }

        assertTrue(filters.isEmpty())
    }

    @Test
    fun `setfilter - registered user with filters returns filter list`() {
        val userId   = insertUser()
        val filterId = insertFilter(userId, "My Filter")

        val filters = transaction {
            FiltersTable.selectAll().where { FiltersTable.userId eq userId }
                .map { it[FiltersTable.id] to it[FiltersTable.name] }
        }

        assertEquals(1, filters.size)
        assertEquals(filterId, filters[0].first)
        assertEquals("My Filter", filters[0].second)
    }

    // ── setfilter callback ────────────────────────────────────────────────────

    @Test
    fun `setfilter callback - activates filter and returns its name`() {
        val userId   = insertUser()
        val filterId = insertFilter(userId, "Work Filter")

        val filterName = transaction {
            val filterRow = FiltersTable.selectAll()
                .where { (FiltersTable.id eq filterId) and (FiltersTable.userId eq userId) }
                .singleOrNull() ?: return@transaction null
            FiltersTable.update({ FiltersTable.userId eq userId }) { it[FiltersTable.active] = false }
            FiltersTable.update({ (FiltersTable.id eq filterId) and (FiltersTable.userId eq userId) }) { it[FiltersTable.active] = true }
            filterRow[FiltersTable.name]
        }

        assertEquals("Work Filter", filterName)

        val isActive = transaction {
            FiltersTable.selectAll().where { FiltersTable.id eq filterId }
                .singleOrNull()?.get(FiltersTable.active)
        }
        assertEquals(true, isActive)
    }

    @Test
    fun `setfilter callback - returns null for filter owned by another user`() {
        val userId   = insertUser()
        val filterId = insertFilter(userId, "Owned")
        val otherId  = 999999L

        val filterName = transaction {
            val userRow = UsersTable.selectAll()
                .where { UsersTable.telegramId eq otherId }.singleOrNull()
                ?: return@transaction null
            val uid = userRow[UsersTable.id]
            FiltersTable.selectAll()
                .where { (FiltersTable.id eq filterId) and (FiltersTable.userId eq uid) }
                .singleOrNull()?.get(FiltersTable.name)
        }

        assertNull(filterName)
    }

    // ── /editfilter callback ──────────────────────────────────────────────────

    @Test
    fun `editfilter callback - starts FilterConversation for owned filter`() {
        val tgId     = testTgId
        val chatId   = testChatId
        val userId   = insertUser()
        val filterId = insertFilter(userId, "Edit Me")

        val filter = transaction {
            val userRow = UsersTable.selectAll()
                .where { UsersTable.telegramId eq tgId }.singleOrNull()
                ?: return@transaction null
            val uid = userRow[UsersTable.id]
            FiltersTable.selectAll()
                .where { (FiltersTable.id eq filterId) and (FiltersTable.userId eq uid) }
                .singleOrNull()?.let { Filter.fromRow(it) }
        }

        assertNotNull(filter)
        filterConversation.startEdit(bot, chatId, filter!!)

        assertTrue(filterConversation.isActive(chatId))
        verify {
            bot.sendMessage(
                chatId      = ChatId.fromId(chatId),
                text        = match { it.contains("Edit Me") },
                replyMarkup = any()
            )
        }
    }

    @Test
    fun `editfilter callback - returns null for unowned filter`() {
        val userId   = insertUser()
        val filterId = insertFilter(userId, "Mine")
        val otherId  = 888888L

        val filter = transaction {
            val userRow = UsersTable.selectAll()
                .where { UsersTable.telegramId eq otherId }.singleOrNull()
                ?: return@transaction null
            val uid = userRow[UsersTable.id]
            FiltersTable.selectAll()
                .where { (FiltersTable.id eq filterId) and (FiltersTable.userId eq uid) }
                .singleOrNull()?.let { Filter.fromRow(it) }
        }

        assertNull(filter)
        assertFalse(filterConversation.isActive(testChatId))
    }

    @Test
    fun `removefilter callback - deletes active filter and clears active flag`() {
        val userId   = insertUser()
        val filterId = insertFilter(userId, "To Delete")
        transaction {
            FiltersTable.update({ (FiltersTable.id eq filterId) and (FiltersTable.userId eq userId) }) { it[FiltersTable.active] = true }
        }

        val result = transaction {
            val filterRow = FiltersTable.selectAll()
                .where { (FiltersTable.id eq filterId) and (FiltersTable.userId eq userId) }
                .singleOrNull() ?: return@transaction null
            val name      = filterRow[FiltersTable.name]
            val wasActive = filterRow[FiltersTable.active]
            FiltersTable.deleteWhere { FiltersTable.id eq filterId }
            name to wasActive
        }

        assertNotNull(result)
        assertEquals("To Delete", result!!.first)
        assertTrue(result.second)

        val filterExists = transaction {
            FiltersTable.selectAll().where { FiltersTable.id eq filterId }.count()
        }
        assertEquals(0L, filterExists)
    }

    @Test
    fun `removefilter callback - deleting non-active filter leaves active filter unchanged`() {
        val userId    = insertUser()
        val filterId1 = insertFilter(userId, "Active One")
        val filterId2 = insertFilter(userId, "Passive One")
        transaction {
            FiltersTable.update({ (FiltersTable.id eq filterId1) and (FiltersTable.userId eq userId) }) { it[FiltersTable.active] = true }
        }

        transaction {
            val filterRow = FiltersTable.selectAll().where { FiltersTable.id eq filterId2 }.singleOrNull()!!
            val wasActive = filterRow[FiltersTable.active]
            if (wasActive) {
                FiltersTable.update({ FiltersTable.id eq filterId2 }) { it[FiltersTable.active] = false }
            }
            FiltersTable.deleteWhere { FiltersTable.id eq filterId2 }
        }

        val filter1Active = transaction {
            FiltersTable.selectAll().where { FiltersTable.id eq filterId1 }
                .singleOrNull()?.get(FiltersTable.active)
        }
        assertEquals(true, filter1Active)
    }

    @Test
    fun `FilterConversation - full flow saves filter to DB`() {
        val userId = insertUser()
        val chatId = testChatId

        filterConversation.start(bot, chatId)
        assertTrue(filterConversation.isActive(chatId))

        filterConversation.handle(bot, chatId, testTgId, "Balkan Flat")
        filterConversation.handle(bot, chatId, testTgId, "beograd")
        filterConversation.handle(bot, chatId, testTgId, "Готово")
        filterConversation.handle(bot, chatId, testTgId, "2")
        filterConversation.handle(bot, chatId, testTgId, "400")
        filterConversation.handle(bot, chatId, testTgId, "800")
        filterConversation.handle(bot, chatId, testTgId, "40")
        filterConversation.handle(bot, chatId, testTgId, "120")
        filterConversation.handle(bot, chatId, testTgId, "Пропустить")
        filterConversation.handle(bot, chatId, testTgId, "Пропустить")

        assertFalse(filterConversation.isActive(chatId))

        val row = transaction {
            FiltersTable.selectAll().where { FiltersTable.userId eq userId }.singleOrNull()
        }
        assertNotNull(row)
        assertEquals("Balkan Flat", row!![FiltersTable.name])
        assertEquals(City.BEOGRAD.code, row[FiltersTable.city])
        assertEquals(2, row[FiltersTable.minRooms])
        assertNull(row[FiltersTable.maxRooms])
        assertEquals(400, row[FiltersTable.minPrice])
        assertEquals(800, row[FiltersTable.maxPrice])
        assertEquals(40, row[FiltersTable.minArea])
        assertEquals(120, row[FiltersTable.maxArea])
        assertNull(row[FiltersTable.adType])
        assertNull(row[FiltersTable.heating])
    }

    @Test
    fun `FilterConversation - empty name is rejected`() {
        insertUser()
        filterConversation.start(bot, testChatId)
        filterConversation.handle(bot, testChatId, testTgId, "   ")

        assertTrue(filterConversation.isActive(testChatId))
        verify { bot.sendMessage(ChatId.fromId(testChatId), "Название не может быть пустым. Введите название:") }
    }

    @Test
    fun `FilterConversation - invalid city is rejected`() {
        insertUser()
        filterConversation.start(bot, testChatId)
        filterConversation.handle(bot, testChatId, testTgId, "My Filter")
        filterConversation.handle(bot, testChatId, testTgId, "Atlantida")

        assertTrue(filterConversation.isActive(testChatId))
        verify { bot.sendMessage(ChatId.fromId(testChatId), "Выберите город из предложенных вариантов:") }
    }

    @Test
    fun `FilterConversation - invalid room input is rejected`() {
        insertUser()
        filterConversation.start(bot, testChatId)
        filterConversation.handle(bot, testChatId, testTgId, "F")
        filterConversation.handle(bot, testChatId, testTgId, "beograd")
        filterConversation.handle(bot, testChatId, testTgId, "Готово")
        filterConversation.handle(bot, testChatId, testTgId, "abc")

        assertTrue(filterConversation.isActive(testChatId))
        verify { bot.sendMessage(ChatId.fromId(testChatId), "Введите число или диапазон (например: 2 или 1-3):") }
    }

    @Test
    fun `FilterConversation - rooms max less than min is rejected`() {
        insertUser()
        filterConversation.start(bot, testChatId)
        filterConversation.handle(bot, testChatId, testTgId, "F")
        filterConversation.handle(bot, testChatId, testTgId, "beograd")
        filterConversation.handle(bot, testChatId, testTgId, "Готово")
        filterConversation.handle(bot, testChatId, testTgId, "3-1")

        assertTrue(filterConversation.isActive(testChatId))
        verify {
            bot.sendMessage(
                ChatId.fromId(testChatId),
                "Максимум должен быть >= минимума. Введите диапазон:"
            )
        }
    }

    @Test
    fun `FilterConversation - skip all optional fields saves minimal filter`() {
        val userId = insertUser()

        filterConversation.start(bot, testChatId)
        filterConversation.handle(bot, testChatId, testTgId, "Minimal")
        filterConversation.handle(bot, testChatId, testTgId, "novi-sad")
        filterConversation.handle(bot, testChatId, testTgId, "Пропустить")
        filterConversation.handle(bot, testChatId, testTgId, "Пропустить")
        filterConversation.handle(bot, testChatId, testTgId, "Пропустить")
        filterConversation.handle(bot, testChatId, testTgId, "Пропустить")
        filterConversation.handle(bot, testChatId, testTgId, "Пропустить")
        filterConversation.handle(bot, testChatId, testTgId, "Пропустить")
        filterConversation.handle(bot, testChatId, testTgId, "Пропустить")
        filterConversation.handle(bot, testChatId, testTgId, "Пропустить")

        assertFalse(filterConversation.isActive(testChatId))

        val row = transaction {
            FiltersTable.selectAll().where { FiltersTable.userId eq userId }.singleOrNull()
        }
        assertNotNull(row)
        assertEquals("Minimal", row!![FiltersTable.name])
        assertEquals(City.NOVI_SAD.code, row[FiltersTable.city])
        assertNull(row[FiltersTable.minRooms])
        assertNull(row[FiltersTable.minPrice])
        assertNull(row[FiltersTable.propertyType])
    }

    @Test
    fun `FilterConversation - property type selection toggles correctly`() {
        insertUser()
        filterConversation.start(bot, testChatId)
        filterConversation.handle(bot, testChatId, testTgId, "F")
        filterConversation.handle(bot, testChatId, testTgId, "beograd")
        filterConversation.handle(bot, testChatId, testTgId, "Квартира")
        filterConversation.handle(bot, testChatId, testTgId, "Дом")
        filterConversation.handle(bot, testChatId, testTgId, "Квартира")

        assertTrue(filterConversation.isActive(testChatId))
    }

    @Test
    fun `FilterConversation - startEdit pre-populates and saves updates`() {
        val userId   = insertUser()
        val filterId = insertFilter(userId, "Old Name", minPrice = 100, maxPrice = 500)

        val filter = transaction {
            FiltersTable.selectAll().where { FiltersTable.id eq filterId }
                .singleOrNull()?.let { Filter.fromRow(it) }
        }
        assertNotNull(filter)

        filterConversation.startEdit(bot, testChatId, filter!!)
        assertTrue(filterConversation.isActive(testChatId))

        filterConversation.handleCallback(bot, testChatId, testTgId, "cq1", "editfield:price")
        filterConversation.handle(bot, testChatId, testTgId, "200")

        filterConversation.handleCallback(bot, testChatId, testTgId, "cq2", "saveedit")
        assertFalse(filterConversation.isActive(testChatId))

        val updated = transaction {
            FiltersTable.selectAll().where { FiltersTable.id eq filterId }.singleOrNull()
        }
        assertNotNull(updated)
        assertEquals(200, updated!![FiltersTable.minPrice])
    }

    @Test
    fun `FilterConversation - cancel removes active draft`() {
        insertUser()
        filterConversation.start(bot, testChatId)
        assertTrue(filterConversation.isActive(testChatId))

        filterConversation.cancel(testChatId)
        assertFalse(filterConversation.isActive(testChatId))
    }

    private fun insertUser(): Int {
        val tgId   = testTgId
        val chatId = testChatId
        return transaction {
            UsersTable.insertIgnore {
                it[UsersTable.telegramId] = tgId
                it[UsersTable.chatId]     = chatId
                it[UsersTable.username]   = "tester"
            }
            UsersTable.selectAll().where { UsersTable.telegramId eq tgId }
                .map { it[UsersTable.id] }.first()
        }
    }

    private fun insertFilter(
        userId: Int,
        name: String,
        minPrice: Int? = null,
        maxPrice: Int? = null
    ): Int = transaction {
        FiltersTable.insert {
            it[FiltersTable.name]     = name
            it[FiltersTable.userId]   = userId
            it[FiltersTable.active]   = false
            it[FiltersTable.city]     = City.BEOGRAD.code
            it[FiltersTable.minPrice] = minPrice
            it[FiltersTable.maxPrice] = maxPrice
        }
        FiltersTable.selectAll()
            .where { (FiltersTable.name eq name) and (FiltersTable.userId eq userId) }
            .map { it[FiltersTable.id] }.first()
    }
}
