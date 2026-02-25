package com.jmvstv_v.config

import io.github.cdimascio.dotenv.dotenv

object AppConfig {
    private val dotenv = dotenv {
        ignoreIfMissing = true
    }

    val telegramBotToken: String = dotenv["TELEGRAM_BOT_TOKEN"] ?: error("TELEGRAM_BOT_TOKEN not set")
    val adminLogin: String = dotenv["ADMIN_LOGIN"] ?: "admin"
    val adminPassword: String = dotenv["ADMIN_PASSWORD"] ?: error("ADMIN_PASSWORD not set")
    val sqlitePath: String = dotenv["SQLITE_PATH"] ?: "./data/srbestate.db"
}
