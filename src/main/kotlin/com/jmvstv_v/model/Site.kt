package com.jmvstv_v.model

import org.jetbrains.exposed.sql.Table

object SitesTable : Table("sites") {
    val id      = integer("id")
    val name    = varchar("name", 64).uniqueIndex()
    val address = varchar("address", 256)
    val active  = bool("active").default(true)
    override val primaryKey = PrimaryKey(id)
}

data class Site(
    val id: Int,
    val name: String,
    val address: String,
    val active: Boolean = true
)
