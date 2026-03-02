package com.jmvstv_v.model

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table

enum class City(val code: Int, val displayName: String) {
    BEOGRAD(0, "beograd"),
    NOVI_SAD(1, "novi-sad"),
    SUBOTICA(2, "subotica"),
    NIS(3, "nis ");

    companion object {
        /** Returns the [City] whose [displayName] matches [name] (case-insensitive), or `null`. */
        fun fromDisplayName(name: String) = entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }

        /** Returns the [City] with the given [code], or `null`. */
        fun fromCode(code: Int?) = entries.firstOrNull { it.code == code }
    }
}

enum class PropertyType(val code: Int, val displayName: String) {
    APARTMENT(1, "Квартира"),
    HOUSE(2, "Дом"),
    OFFICE(3, "Офис"),
    AREA(4, "Коммерческое помещение"),
    HOUSE_APARTMENT(5, "Квартира в доме");

    companion object {
        /** Returns the [PropertyType] whose [displayName] matches [name] (case-insensitive), or `null`. */
        fun fromDisplayName(name: String) = entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
    }
}

enum class AdType(val code: Int, val displayName: String) {
    AGENCY(0, "Агентство"),
    OWNER(1, "Собственник");

    companion object {
        /** Returns the [AdType] whose [displayName] matches [name] (case-insensitive), or `null`. */
        fun fromDisplayName(name: String) = entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
    }
}

enum class Heating(val code: Int, val displayName: String) {
    CENTRAL(1, "Центральное");

    companion object {
        /** Returns the [Heating] whose [displayName] matches [name] (case-insensitive), or `null`. */
        fun fromDisplayName(name: String) = entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
    }
}

/**
 * Domain model representing a saved search filter.
 */
data class Filter(
    val id: Int,
    val name: String,
    val userId: Int,
    val active: Boolean = false,
    val city: Int? = null,
    val propertyType: String? = null,
    val minRooms: Double? = null,
    val maxRooms: Double? = null,
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val minArea: Int? = null,
    val maxArea: Int? = null,
    val adType: Int? = null,
    val heating: Int? = null
) {
    companion object {
        /**
         * Maps a database [ResultRow] to a [Filter] domain object.
         *
         * @param row Exposed result row containing filter columns.
         * @return Populated [Filter] instance.
         */
        fun fromRow(row: ResultRow) = Filter(
            id           = row[FiltersTable.id],
            name         = row[FiltersTable.name],
            userId       = row[FiltersTable.userId],
            active       = row[FiltersTable.active],
            city         = row[FiltersTable.city],
            propertyType = row[FiltersTable.propertyType],
            minRooms     = row[FiltersTable.minRooms],
            maxRooms     = row[FiltersTable.maxRooms],
            minPrice     = row[FiltersTable.minPrice],
            maxPrice     = row[FiltersTable.maxPrice],
            minArea      = row[FiltersTable.minArea],
            maxArea      = row[FiltersTable.maxArea],
            adType       = row[FiltersTable.adType],
            heating      = row[FiltersTable.heating]
        )
    }
}

object FiltersTable : Table("filters") {
    val id           = integer("id").autoIncrement()
    val name         = varchar("name", 255)
    val userId       = reference("user_id", UsersTable.id)
    val active       = bool("active").default(false)
    val city         = integer("city").nullable()
    val propertyType = varchar("property_type", 50).nullable()
    val minRooms     = double("min_rooms").nullable()
    val maxRooms     = double("max_rooms").nullable()
    val minPrice     = integer("min_price").nullable()
    val maxPrice     = integer("max_price").nullable()
    val minArea      = integer("min_area").nullable()
    val maxArea      = integer("max_area").nullable()
    val adType       = integer("ad_type").nullable()
    val heating      = integer("heating").nullable()
    override val primaryKey = PrimaryKey(id)
}
