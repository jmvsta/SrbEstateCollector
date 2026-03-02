package com.jmvstv_v.db

import com.jmvstv_v.config.AppConfig
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jetbrains.exposed.sql.Database
import java.sql.DriverManager

object AppDatabase {

    fun init() {
        val jdbcUrl = "jdbc:postgresql://${AppConfig.dbHost}:${AppConfig.dbPort}/${AppConfig.dbName}"
        DriverManager.getConnection(jdbcUrl, AppConfig.dbUser, AppConfig.dbPassword).use { conn ->
            val db = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(conn))
            Liquibase("db/changelog/db.changelog-master.xml", ClassLoaderResourceAccessor(), db)
                .update("")
        }
        Database.connect(
            url      = jdbcUrl,
            driver   = "org.postgresql.Driver",
            user     = AppConfig.dbUser,
            password = AppConfig.dbPassword
        )
    }
}
