package com.jmvstv_v.db

import com.jmvstv_v.config.AppConfig
import com.jmvstv_v.model.FiltersTable
import com.jmvstv_v.model.UsersTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object SqliteDatabase {
    fun init() {
        val dbFile = File(AppConfig.sqlitePath)
        dbFile.parentFile?.mkdirs()

        Database.connect(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC"
        )

        transaction {
            SchemaUtils.create(UsersTable, FiltersTable)
        }
    }
}
