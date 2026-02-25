package com.jmvstv_v.repository

import com.jmvstv_v.model.UserFilterEntry

interface UserRepository {

    fun registerIfAbsent(telegramId: Long, chatId: Long, username: String?): Boolean

    fun findIdByTelegramId(telegramId: Long): Int?

    /** Returns all users that have at least one filter with active = true. */
    fun findUsersWithActiveFilter(): List<UserFilterEntry>
}
