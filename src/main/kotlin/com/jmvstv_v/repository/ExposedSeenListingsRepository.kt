package com.jmvstv_v.repository

import com.jmvstv_v.model.Listing
import com.jmvstv_v.model.SeenListingsTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedSeenListingsRepository : SeenListingsRepository {

    override fun getSeenIds(filterId: Int, source: String, uniqueIds: List<String>): Set<String> {
        if (uniqueIds.isEmpty()) return emptySet()
        return transaction {
            SeenListingsTable.selectAll()
                .where {
                    (SeenListingsTable.filterId   eq filterId) and
                    (SeenListingsTable.sourceSite eq source)  and
                    (SeenListingsTable.uniqueId   inList uniqueIds)
                }
                .map { it[SeenListingsTable.uniqueId] }
                .toSet()
        }
    }

    override fun markSeen(userId: Int, filterId: Int, listings: List<Listing>) {
        transaction {
            listings.forEach { listing ->
                SeenListingsTable.insertIgnore {
                    it[SeenListingsTable.userId]        = userId
                    it[SeenListingsTable.filterId]      = filterId
                    it[SeenListingsTable.sourceSite]    = listing.sourceSite
                    it[SeenListingsTable.uniqueId]      = listing.externalId
                    it[SeenListingsTable.title]         = listing.title
                    it[SeenListingsTable.url]           = listing.url
                    it[SeenListingsTable.city]          = listing.city
                    it[SeenListingsTable.propertyType]  = listing.propertyType
                    it[SeenListingsTable.price]         = listing.price
                    it[SeenListingsTable.area]          = listing.area
                    it[SeenListingsTable.advertiserType] = listing.advertiserType
                    it[SeenListingsTable.heatingType]   = listing.heatingType
                }
            }
        }
    }
}
