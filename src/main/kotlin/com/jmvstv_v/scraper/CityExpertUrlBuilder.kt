package com.jmvstv_v.scraper

import com.jmvstv_v.model.City
import com.jmvstv_v.model.Filter
import com.jmvstv_v.model.Heating
import com.jmvstv_v.model.PropertyType

/**
 * Builds a CityExpert search URL from a [Filter] entity.
 *
 * Example output:
 *   https://cityexpert.rs/izdavanje-nekretnina/beograd?ptId=1&minPrice=100&maxPrice=500&minSize=30&maxSize=80&heatingArray=1&structure=1.0,1.5,2.0
 */
object CityExpertUrlBuilder {

    private const val BASE = "https://cityexpert.rs"

    enum class ListingType(val slug: String) {
        RENT("izdavanje-nekretnina"),
        SALE("prodaja-nekretnina")
    }

    // PropertyType.code  →  CityExpert ptId
    private val ptIdMap = mapOf(
        PropertyType.APARTMENT.code to 1,
        PropertyType.HOUSE.code     to 2,
        PropertyType.OFFICE.code    to 3,
        PropertyType.ROOM.code      to 4
    )

    // City.code  →  URL path slug
    private val citySlugMap = mapOf(
        City.BEOGRAD.code   to "beograd",
        City.NOVI_SAD.code  to "novi-sad",
        City.SUBOTICA.code  to "subotica",
        City.NIS.code       to "nis"
    )

    // Heating.code  →  CityExpert heatingArray value
    private val heatingMap = mapOf(
        Heating.CENTRAL.code to 1,
        Heating.STOVE.code   to 4,
        Heating.AC.code      to 6
    )

    /**
     * @param filter      Filter row from DB
     * @param listingType RENT (default) or SALE
     */
    fun build(filter: Filter, listingType: ListingType = ListingType.RENT): String {
        val citySlug = filter.city?.let { citySlugMap[it] } ?: "beograd"

        val params = buildList {
            filter.propertyType?.let { ptIdMap[it] }?.let { add("ptId=$it") }
            filter.minPrice?.let { add("minPrice=$it") }
            filter.maxPrice?.let { add("maxPrice=$it") }
            filter.minArea?.let  { add("minSize=$it") }
            filter.maxArea?.let  { add("maxSize=$it") }
            filter.heating?.let  { heatingMap[it] }?.let { add("heatingArray=$it") }
            val structures = buildStructure(filter.minRooms, filter.maxRooms)
            if (structures.isNotEmpty()) add("structure=${structures.joinToString(",")}")
        }

        val query = if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        return "$BASE/${listingType.slug}/$citySlug$query"
    }

    /**
     * Converts an integer room range to CityExpert's `structure` values.
     *
     * Serbian real estate uses 0.5-room increments:
     *   0.5 = garsonjera, 1.0 = jednosoban, 1.5 = jednoiposoban, 2.0 = dvosoban …
     *
     * Examples:
     *   minRooms=1, maxRooms=3  →  ["1.0","1.5","2.0","2.5","3.0"]
     *   minRooms=2, maxRooms=null  →  ["2.0"]
     */
    private fun buildStructure(minRooms: Int?, maxRooms: Int?): List<String> {
        if (minRooms == null) return emptyList()
        // Work in half-room units (×2) to avoid floating-point precision issues
        val start = minRooms * 2
        val end   = (maxRooms ?: minRooms) * 2
        return (start..end).map { halfUnits ->
            val whole = halfUnits / 2
            if (halfUnits % 2 == 0) "$whole.0" else "$whole.5"
        }
    }
}
