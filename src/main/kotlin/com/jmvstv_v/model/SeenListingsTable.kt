package com.jmvstv_v.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object SeenListingsTable : Table("seen_listings") {
    val userId        = reference("user_id", UsersTable.id)
    val filterId      = reference("filter_id", FiltersTable.id)
    val sourceSite    = varchar("source", 64)
    val uniqueId      = varchar("unique_id", 64)
    val seenAt        = datetime("seen_at").default(LocalDateTime.now())
    // card fields for review
    val title         = varchar("title", 512)
    val url           = varchar("url", 1024)
    val city          = varchar("city", 64)
    val propertyType  = varchar("property_type", 64)
    val price         = integer("price").nullable()
    val area          = integer("area").nullable()
    val advertiserType = varchar("advertiser_type", 64).nullable()
    val heatingType   = varchar("heating_type", 64).nullable()
    override val primaryKey = PrimaryKey(filterId, sourceSite, uniqueId)
}
