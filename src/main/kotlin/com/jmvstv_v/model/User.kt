package com.jmvstv_v.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object UsersTable : Table("users") {
    val id         = integer("id").autoIncrement()
    val telegramId = long("telegram_id").uniqueIndex()
    val chatId     = long("chat_id")
    val username   = varchar("username", 255).nullable()
    val createdAt  = datetime("created_at").default(LocalDateTime.now())
    override val primaryKey = PrimaryKey(id)
}

data class User(
    val id: Int = 0,
    val telegramId: Long,
    val chatId: Long,
    val username: String?,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
