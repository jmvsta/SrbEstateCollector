package com.jmvstv_v.config

import io.github.cdimascio.dotenv.dotenv

/**
 * Application-wide configuration loaded from environment variables or an `.env` file.
 * All sensitive and tuneable values must be declared here; nothing is hard-coded elsewhere.
 */
object AppConfig {
    private val dotenv = dotenv {
        ignoreIfMissing = true
    }

    val telegramBotToken: String = dotenv["TELEGRAM_BOT_TOKEN"] ?: error("TELEGRAM_BOT_TOKEN not set")
    val adminLogin: String = dotenv["ADMIN_LOGIN"] ?: "admin"
    val adminPassword: String = dotenv["ADMIN_PASSWORD"] ?: error("ADMIN_PASSWORD not set")
    val dbHost: String     = dotenv["DB_HOST"] ?: "localhost"
    val dbPort: Int        = dotenv["DB_PORT"]?.toInt() ?: 5432
    val dbName: String     = dotenv["DB_NAME"] ?: error("DB_NAME not set")
    val dbUser: String     = dotenv["DB_USER"] ?: error("DB_USER not set")
    val dbPassword: String = dotenv["DB_PASSWORD"] ?: error("DB_PASSWORD not set")

    val httpRequestTimeoutMs: Long = dotenv["HTTP_REQUEST_TIMEOUT_MS"]?.toLong() ?: 30_000L
    val httpConnectTimeoutMs: Long = dotenv["HTTP_CONNECT_TIMEOUT_MS"]?.toLong() ?: 10_000L
    val collectIntervalHours: Long = dotenv["COLLECT_INTERVAL_HOURS"]?.toLong() ?: 1L
}
