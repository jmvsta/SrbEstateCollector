package com.jmvstv_v.db

import com.jmvstv_v.config.AppConfig
import com.jmvstv_v.model.FiltersTable
import com.jmvstv_v.model.SeenListingsTable
import com.jmvstv_v.model.SitesTable
import com.jmvstv_v.model.UsersTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object AppDatabase {

    fun init() {
        Database.connect(
            url      = "jdbc:postgresql://${AppConfig.dbHost}:${AppConfig.dbPort}/${AppConfig.dbName}",
            driver   = "org.postgresql.Driver",
            user     = AppConfig.dbUser,
            password = AppConfig.dbPassword
        )
        transaction {
            SchemaUtils.create(UsersTable, FiltersTable, SeenListingsTable, SitesTable)
        }
    }
}
