package com.jmvstv_v.scraper.url_builder

import com.jmvstv_v.model.AdType
import com.jmvstv_v.model.City
import com.jmvstv_v.model.Filter
import com.jmvstv_v.model.Heating

/**
 * Builds search URLs for nekretnine.rs from a [Filter].
 */
object NekretninUrlBuilder {

    private const val BASE = "https://www.nekretnine.rs"

    private val ALL_HEATING = "centralno-grejanje_centralno-grejanje-na-gas_centralno-grejanje-na-struju_" +
            "podno-grejanje_samostalno-na-struju_ostalo_klima-uredaj"

    private val CENTRAL_HEATING = "centralno-grejanje_centralno-grejanje-na-gas_centralno-grejanje-na-struju"

    private val ROOM_SLUGS = mapOf(
        0 to "garsonjera",
        1 to "jednosoban-stan",
        2 to "dvosoban-stan",
        3 to "trosoban-stan",
        4 to "cetvorosoban-stan",
        5 to "petosoban-stan",
        6 to "petosoban-i-vise-soba"
    )

    /**
     * Builds a nekretnine.rs search URL for the given filter.
     *
     * @param filter User-defined search filter.
     * @return Fully-qualified nekretnine.rs search URL.
     */
    fun build(filter: Filter): String {
        val citySlug = City.fromCode(filter.city)?.displayName ?: "beograd"

        val heating = if (filter.heating == Heating.CENTRAL.code) CENTRAL_HEATING else ALL_HEATING

        val ownerPart = when (filter.adType) {
            AdType.OWNER.code  -> "/vlasnik"
            AdType.AGENCY.code -> ""
            else               -> ""
        }

        val tipStanovi = buildRoomSlugs(filter.minRooms, filter.maxRooms)
            ?.let { "/tip-stanovi/$it" } ?: ""

        val areaPart = if (filter.minArea != null || filter.maxArea != null) {
            "/kvadratura/${filter.minArea ?: 1}_${filter.maxArea ?: 9999}"
        } else ""

        val pricePart = if (filter.minPrice != null || filter.maxPrice != null) {
            "/cena/${filter.minPrice ?: 1}_${filter.maxPrice ?: 999999}"
        } else ""

        return "$BASE/stambeni-objekti/stanovi/izdavanje-prodaja/izdavanje" +
                "$tipStanovi" +
                "/grad/$citySlug" +
                "/vrsta-grejanja/$heating" +
                "$ownerPart$areaPart$pricePart" +
                "/lista/po-stranici/10/?order=2"
    }

    /**
     * Converts an integer room range to a `_`-joined nekretnine.rs slug string for the
     * `/tip-stanovi/` path segment.
     *
     * @param minRooms Minimum room count, or `null` to skip the segment entirely.
     * @param maxRooms Maximum room count, or `null` to use [minRooms] as exact value.
     * @return Joined slug string, or `null` when [minRooms] is `null`.
     */
    private fun buildRoomSlugs(minRooms: Double?, maxRooms: Double?): String? {
        if (minRooms == null) return null
        val minInt = minRooms.toInt()
        val maxInt = (maxRooms ?: minRooms).toInt()
        return (minInt..maxInt)
            .mapNotNull { rooms -> ROOM_SLUGS[rooms.coerceAtMost(6)] }
            .distinct()
            .joinToString("_")
            .takeIf { it.isNotEmpty() }
    }
}
