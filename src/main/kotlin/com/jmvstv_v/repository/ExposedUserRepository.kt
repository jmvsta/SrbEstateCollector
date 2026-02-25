package com.jmvstv_v.repository

import com.jmvstv_v.model.Filter
import com.jmvstv_v.model.FiltersTable
import com.jmvstv_v.model.UserFilterEntry
import com.jmvstv_v.model.UsersTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedUserRepository : UserRepository {

    override fun registerIfAbsent(telegramId: Long, chatId: Long, username: String?): Boolean =
        transaction {
            val exists = UsersTable.selectAll()
                .where { UsersTable.telegramId eq telegramId }
                .count() > 0
            if (!exists) {
                UsersTable.insertIgnore {
                    it[UsersTable.telegramId] = telegramId
                    it[UsersTable.chatId]     = chatId
                    it[UsersTable.username]   = username
                }
                true
            } else {
                false
            }
        }

    override fun findIdByTelegramId(telegramId: Long): Int? =
        transaction {
            UsersTable.selectAll()
                .where { UsersTable.telegramId eq telegramId }
                .singleOrNull()
                ?.get(UsersTable.id)
        }

    override fun findUsersWithActiveFilter(): List<UserFilterEntry> =
        transaction {
            UsersTable
                .join(
                    otherTable  = FiltersTable,
                    joinType    = JoinType.INNER,
                    onColumn    = UsersTable.id,
                    otherColumn = FiltersTable.userId
                )
                .selectAll()
                .where { FiltersTable.active eq true }
                .map { row ->
                    UserFilterEntry(
                        chatId = row[UsersTable.chatId],
                        userId = row[UsersTable.id],
                        filter = Filter.fromRow(row)
                    )
                }
        }
}
