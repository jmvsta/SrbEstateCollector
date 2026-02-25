package com.jmvstv_v.scraper.url_builder

import com.jmvstv_v.model.AdType
import com.jmvstv_v.model.City
import com.jmvstv_v.model.Filter
import com.jmvstv_v.model.Heating

/**
 * Builds search URLs for 4zida.rs from a [Filter].
 */
object FourZidaUrlBuilder {

    private const val BASE = "https://www.4zida.rs"

    enum class ListingType(val slug: String) {
        RENT("izdavanje-stanova"),
        SALE("prodaja-stanova")
    }

    /**
     * Builds a 4zida.rs search URL for the given filter.
     *
     * `maxPrice` maps to the path segment `do-{maxPrice}-evra`.
     * `minPrice` maps to query param `skuplje_od={minPrice}eur`.
     *
     * @param filter      User-defined search filter.
     * @param listingType Listing category: [ListingType.RENT] (default) or [ListingType.SALE].
     * @return Fully-qualified 4zida.rs search URL.
     */
    fun build(filter: Filter, listingType: ListingType = ListingType.RENT): String {
        val citySlug  = City.fromCode(filter.city)?.displayName ?: "beograd"
        val pricePath = filter.maxPrice?.let { "/do-${it}-evra" } ?: ""
        val basePath  = "$BASE/${listingType.slug}/$citySlug$pricePath"

        val params = buildList {
            add("sortiranje=najnoviji")

            buildStructures(filter.minRooms, filter.maxRooms).forEach {
                add("struktura=$it")
            }

            when (filter.adType) {
                AdType.OWNER.code  -> add("oglasivac=vlasnik")
                AdType.AGENCY.code -> add("oglasivac=agencija")
                else               -> { add("oglasivac=vlasnik"); add("oglasivac=agencija") }
            }

            if (filter.heating == Heating.CENTRAL.code) add("grejanje=centralno")
            filter.minArea?.let { add("vece_od=${it}m2") }
            filter.maxArea?.let { add("manje_od=${it}m2") }
            filter.minPrice?.let { add("skuplje_od=${it}eur") }
        }

        return "$basePath?${params.joinToString("&")}"
    }

    /**
     * Converts an integer room range to 4zida `struktura` slug values.
     *
     * Uses the same 0.5-increment convention as CityExpert (halfUnits = rooms × 2):
     * 1 = garsonjera, 2 = jednosoban, 3 = jednoiposoban, 4 = dvosoban, …
     *
     * @param minRooms Minimum room count, or `null` to skip.
     * @param maxRooms Maximum room count, or `null` to use [minRooms] as exact value.
     * @return List of 4zida structure slug strings.
     */
    private fun buildStructures(minRooms: Int?, maxRooms: Int?): List<String> {
        if (minRooms == null) return emptyList()
        val start = maxOf(1, minRooms * 2)
        val end   = maxOf(1, (maxRooms ?: minRooms) * 2)
        return (start..end).mapNotNull { STRUCTURE_NAMES[it] }
    }

    private val STRUCTURE_NAMES = mapOf(
        1  to "garsonjera",
        2  to "jednosoban",
        3  to "jednoiposoban",
        4  to "dvosoban",
        5  to "dvoiposoban",
        6  to "trosoban",
        7  to "troiposoban",
        8  to "cetvorosoban",
        9  to "cetvoroiposoban",
        10 to "petosoban"
    )
}
