package com.jmvstv_v.repository

import com.jmvstv_v.model.SitesTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedSiteRepository : SiteRepository {

    private val seeds = listOf(
        Triple(1, "cityexpert",  "https://cityexpert.rs"),
        Triple(2, "4zida",       "https://www.4zida.rs"),
        Triple(3, "halooglasi",  "https://www.halooglasi.com"),
        Triple(4, "nekretnine",  "https://www.nekretnine.rs")
    )

    override fun seed() {
        transaction {
            seeds.forEach { (id, name, address) ->
                SitesTable.insertIgnore {
                    it[SitesTable.id]      = id
                    it[SitesTable.name]    = name
                    it[SitesTable.address] = address
                    it[SitesTable.active]  = true
                }
            }
        }
    }

    override fun findActiveNames(): Set<String> =
        transaction {
            SitesTable.selectAll()
                .where { SitesTable.active eq true }
                .map { it[SitesTable.name] }
                .toSet()
        }
}
