package com.jmvstv_v

import com.jmvstv_v.model.FiltersTable
import com.jmvstv_v.model.SeenListingsTable
import com.jmvstv_v.model.SitesTable
import com.jmvstv_v.model.UsersTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object TestDatabase {

    private var initialized = false

    fun init() {
        if (initialized) return
        val dbFile = File(System.getProperty("java.io.tmpdir"), "test_srbestate_${ProcessHandle.current().pid()}.db")
        dbFile.delete()
        dbFile.deleteOnExit()
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(UsersTable, FiltersTable, SeenListingsTable, SitesTable)
        }
        initialized = true
    }

    fun clear() = transaction {
        SeenListingsTable.deleteAll()
        FiltersTable.deleteAll()
        UsersTable.deleteAll()
        SitesTable.deleteAll()
    }
}
