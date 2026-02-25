package com.jmvstv_v.model

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table

enum class City(val code: Int, val displayName: String) {
    BEOGRAD(0, "Beograd"),
    NOVI_SAD(1, "Novi Sad"),
    SUBOTICA(2, "Subotica"),
    NIS(3, "Nis");

    companion object {
        fun fromDisplayName(name: String) = entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
    }
}

enum class PropertyType(val code: Int, val displayName: String) {
    APARTMENT(0, "Квартира"),
    HOUSE(1, "Дом"),
    OFFICE(2, "Офис"),
    ROOM(3, "Комната");

    companion object {
        fun fromDisplayName(name: String) = entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
    }
}

enum class AdType(val code: Int, val displayName: String) {
    AGENCY(0, "Агентство"),
    OWNER(1, "Собственник");

    companion object {
        fun fromDisplayName(name: String) = entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
    }
}

enum class Heating(val code: Int, val displayName: String) {
    CENTRAL(0, "Центральное"),
    STOVE(1, "Печь"),
    AC(2, "Кондиционер");

    companion object {
        fun fromDisplayName(name: String) = entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
    }
}

data class Filter(
    val id: Int,
    val name: String,
    val userId: Int,
    val active: Boolean = false,
    val city: Int? = null,
    val propertyType: Int? = null,
    val minRooms: Int? = null,
    val maxRooms: Int? = null,
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val minArea: Int? = null,
    val maxArea: Int? = null,
    val adType: Int? = null,
    val heating: Int? = null
) {
    companion object {
        fun fromRow(row: ResultRow) = Filter(
            id          = row[FiltersTable.id],
            name        = row[FiltersTable.name],
            userId      = row[FiltersTable.userId],
            active      = row[FiltersTable.active],
            city        = row[FiltersTable.city],
            propertyType= row[FiltersTable.propertyType],
            minRooms    = row[FiltersTable.minRooms],
            maxRooms    = row[FiltersTable.maxRooms],
            minPrice    = row[FiltersTable.minPrice],
            maxPrice    = row[FiltersTable.maxPrice],
            minArea     = row[FiltersTable.minArea],
            maxArea     = row[FiltersTable.maxArea],
            adType      = row[FiltersTable.adType],
            heating     = row[FiltersTable.heating]
        )
    }
}

object FiltersTable : Table("filters") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val userId = reference("user_id", UsersTable.id)
    val active = bool("active").default(false)
    val city = integer("city").nullable()
    val propertyType = integer("property_type").nullable()
    val minRooms = integer("min_rooms").nullable()
    val maxRooms = integer("max_rooms").nullable()
    val minPrice = integer("min_price").nullable()
    val maxPrice = integer("max_price").nullable()
    val minArea = integer("min_area").nullable()
    val maxArea = integer("max_area").nullable()
    val adType = integer("ad_type").nullable()
    val heating = integer("heating").nullable()
    override val primaryKey = PrimaryKey(id)
}
