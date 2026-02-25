package com.jmvstv_v

import com.github.kotlintelegrambot.Bot
import com.jmvstv_v.model.City
import com.jmvstv_v.model.FiltersTable
import com.jmvstv_v.model.UsersTable
import com.jmvstv_v.repository.ExposedSeenListingsRepository
import com.jmvstv_v.repository.ExposedSiteRepository
import com.jmvstv_v.repository.ExposedUserRepository
import com.jmvstv_v.scraper.CollectScheduler
import com.jmvstv_v.scraper.processor.CityExpertProcessor
import com.jmvstv_v.scraper.processor.FourZidaProcessor
import com.jmvstv_v.scraper.processor.HaloOglasiProcessor
import com.jmvstv_v.scraper.processor.NekretninProcessor
import com.jmvstv_v.scraper.url_builder.CityExpertUrlBuilder
import com.jmvstv_v.scraper.url_builder.FourZidaUrlBuilder
import com.jmvstv_v.scraper.url_builder.HaloOglasiUrlBuilder
import com.jmvstv_v.scraper.url_builder.NekretninUrlBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SiteRequestsTest {

    private val testTgId = 300001L
    private val testChatId = 400001L

    private lateinit var collectScheduler: CollectScheduler

    @BeforeAll
    fun initDb() {
        TestDatabase.init()
    }

    @BeforeEach
    fun setup() {
        TestDatabase.clear()
        val siteRepository = ExposedSiteRepository().also { it.seed() }
        collectScheduler = CollectScheduler(
            processors             = listOf(CityExpertProcessor, FourZidaProcessor, HaloOglasiProcessor, NekretninProcessor),
            seenListingsRepository = ExposedSeenListingsRepository(),
            userRepository         = ExposedUserRepository(),
            siteRepository         = siteRepository
        )
    }

    @AfterEach
    fun cleanup() {
        unmockkObject(CityExpertUrlBuilder, FourZidaUrlBuilder, HaloOglasiUrlBuilder, NekretninUrlBuilder)
    }

    @Test
    fun `CityExpertUrlBuilder generates cityexpert-rs URL`() {
        val url = CityExpertUrlBuilder.build(minimalFilter())
        assertTrue(url.startsWith("https://cityexpert.rs"), "Got: $url")
    }

    @Test
    fun `FourZidaUrlBuilder generates 4zida-rs URL`() {
        val url = FourZidaUrlBuilder.build(minimalFilter())
        assertTrue(url.startsWith("https://www.4zida.rs"), "Got: $url")
    }

    @Test
    fun `HaloOglasiUrlBuilder generates halooglasi-com URL`() {
        val url = HaloOglasiUrlBuilder.build(minimalFilter())
        assertTrue(url.startsWith("https://www.halooglasi.com"), "Got: $url")
    }

    @Test
    fun `NekretninUrlBuilder generates nekretnine-rs URL`() {
        val url = NekretninUrlBuilder.build(minimalFilter())
        assertTrue(url.startsWith("https://www.nekretnine.rs"), "Got: $url")
    }

    @Test
    fun `CityExpertUrlBuilder embeds price and structure params`() {
        val filter = minimalFilter(minPrice = 300, maxPrice = 900, minRooms = 1, maxRooms = 2)
        val url = CityExpertUrlBuilder.build(filter)
        assertTrue(url.contains("minPrice=300"), url)
        assertTrue(url.contains("maxPrice=900"), url)
        assertTrue(url.contains("structure="), url)
    }

    @Test
    fun `FourZidaUrlBuilder embeds max price in path segment`() {
        val url = FourZidaUrlBuilder.build(minimalFilter(maxPrice = 500))
        assertTrue(url.contains("do-500-evra"), url)
    }

    @Test
    fun `HaloOglasiUrlBuilder embeds city slug in path`() {
        val url = HaloOglasiUrlBuilder.build(minimalFilter())
        assertTrue(url.contains("beograd"), url)
    }

    @Test
    fun `NekretninUrlBuilder embeds city and price segment`() {
        val url = NekretninUrlBuilder.build(minimalFilter(minPrice = 200, maxPrice = 700))
        assertTrue(url.contains("/grad/beograd"), url)
        assertTrue(url.contains("/cena/200_700"), url)
    }

    @Test
    fun `runTick calls URL builder for every enabled site`() {
        setupUserWithActiveFilter()
        injectMockBot()

        mockkObject(CityExpertUrlBuilder, FourZidaUrlBuilder, HaloOglasiUrlBuilder, NekretninUrlBuilder)
        every { CityExpertUrlBuilder.build(any()) } returns "http://127.0.0.1:0/ce"
        every { FourZidaUrlBuilder.build(any()) } returns "http://127.0.0.1:0/fz"
        every { HaloOglasiUrlBuilder.build(any()) } returns "http://127.0.0.1:0/ho"
        every { NekretninUrlBuilder.build(any()) } returns "http://127.0.0.1:0/ne"

        invokeRunTick()
        Thread.sleep(500)

        verify { CityExpertUrlBuilder.build(any()) }
        verify { FourZidaUrlBuilder.build(any()) }
        verify { HaloOglasiUrlBuilder.build(any()) }
        verify { NekretninUrlBuilder.build(any()) }
    }

    @Test
    fun `runTick does not call URL builders when no user has an active filter`() {
        injectMockBot()

        mockkObject(CityExpertUrlBuilder, FourZidaUrlBuilder, HaloOglasiUrlBuilder, NekretninUrlBuilder)

        invokeRunTick()
        Thread.sleep(200)

        verify(exactly = 0) { CityExpertUrlBuilder.build(any()) }
        verify(exactly = 0) { FourZidaUrlBuilder.build(any()) }
        verify(exactly = 0) { HaloOglasiUrlBuilder.build(any()) }
        verify(exactly = 0) { NekretninUrlBuilder.build(any()) }
    }

    private fun minimalFilter(
        minPrice: Int? = null,
        maxPrice: Int? = null,
        minRooms: Int? = null,
        maxRooms: Int? = null
    ) = com.jmvstv_v.model.Filter(
        id = 1,
        name = "Test",
        userId = 1,
        city = City.BEOGRAD.code,
        minPrice = minPrice,
        maxPrice = maxPrice,
        minRooms = minRooms,
        maxRooms = maxRooms
    )

    private fun setupUserWithActiveFilter() {
        val tgId = testTgId
        val chatId = testChatId
        transaction {
            UsersTable.insertIgnore {
                it[UsersTable.telegramId] = tgId
                it[UsersTable.chatId] = chatId
                it[UsersTable.username] = "sched_tester"
            }
            val userId = UsersTable
                .select(UsersTable.id)
                .where { UsersTable.telegramId eq tgId }
                .first()[UsersTable.id]

            FiltersTable.insert {
                it[FiltersTable.name]   = "Sched Filter"
                it[FiltersTable.userId] = userId
                it[FiltersTable.active] = true
                it[FiltersTable.city]   = City.BEOGRAD.code
            }
        }
    }

    private fun injectMockBot() {
        val botField = CollectScheduler::class.java.getDeclaredField("bot")
        botField.isAccessible = true
        botField.set(collectScheduler, mockk<Bot>(relaxed = true))
    }

    private fun invokeRunTick() {
        val method = CollectScheduler::class.java.getDeclaredMethod("runTick")
        method.isAccessible = true
        method.invoke(collectScheduler)
    }
}