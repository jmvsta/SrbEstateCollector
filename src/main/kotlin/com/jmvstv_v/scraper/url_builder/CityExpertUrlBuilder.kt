package com.jmvstv_v.scraper.url_builder

import com.jmvstv_v.model.City
import com.jmvstv_v.model.Filter
import kotlin.math.roundToInt

/**
 * Builds search URLs for cityexpert.rs from a [Filter].
 */
object CityExpertUrlBuilder {

    private const val BASE = "https://cityexpert.rs"

    enum class ListingType(val slug: String) {
        RENT("izdavanje-nekretnina"),
        SALE("prodaja-nekretnina")
    }

    /**
     * Builds a CityExpert search URL for the given filter.
     *
     * @param filter      User-defined search filter.
     * @param listingType Listing category: [ListingType.RENT] (default) or [ListingType.SALE].
     * @return Fully-qualified CityExpert search URL.
     */
    fun build(filter: Filter, listingType: ListingType = ListingType.RENT): String {
        val citySlug = City.fromCode(filter.city)

        val params = buildList {
            filter.propertyType?.let { add("ptId=$it") }
            filter.minPrice?.let { add("minPrice=$it") }
            filter.maxPrice?.let { add("maxPrice=$it") }
            filter.minArea?.let  { add("minSize=$it") }
            filter.maxArea?.let  { add("maxSize=$it") }
            filter.heating?.let  { add("heatingArray=$it") }
            val structures = buildStructure(filter.minRooms, filter.maxRooms)
            if (structures.isNotEmpty()) add("structure=${structures.joinToString(",")}")
        }

        val query = if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        return "$BASE/${listingType.slug}/${citySlug?.displayName}$query"
    }

    /**
     * Converts an integer room range to CityExpert's `structure` values.
     *
     * Serbian real estate uses 0.5-room increments:
     * `0.5` = garsonjera, `1.0` = jednosoban, `1.5` = jednoiposoban, `2.0` = dvosoban, …
     *
     * Examples:
     * - minRooms=1, maxRooms=3  →  `["1.0","1.5","2.0","2.5","3.0"]`
     * - minRooms=2, maxRooms=null  →  `["2.0"]`
     *
     * @param minRooms Minimum room count, or `null` to skip.
     * @param maxRooms Maximum room count, or `null` to use [minRooms] as exact value.
     * @return List of CityExpert structure strings.
     */
    private fun buildStructure(minRooms: Double?, maxRooms: Double?): List<String> {
        if (minRooms == null) return emptyList()
        val start = maxOf(1, (minRooms * 2).roundToInt())
        val end   = maxOf(1, ((maxRooms ?: minRooms) * 2).roundToInt())
        return (start..end).map { halfUnits ->
            val whole = halfUnits / 2
            if (halfUnits % 2 == 0) "$whole.0" else "$whole.5"
        }
    }
}
