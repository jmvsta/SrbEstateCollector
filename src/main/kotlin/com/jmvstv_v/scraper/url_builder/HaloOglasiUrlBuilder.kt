package com.jmvstv_v.scraper.url_builder

import com.jmvstv_v.model.AdType
import com.jmvstv_v.model.City
import com.jmvstv_v.model.Filter
import com.jmvstv_v.model.Heating
import kotlin.collections.get

/**
 * Builds search URLs for halooglasi.com from a [Filter].
 */
object HaloOglasiUrlBuilder {

    private const val BASE = "https://www.halooglasi.com/nekretnine/izdavanje-stanova"

    private const val OWNER_ID  = "387237"
    private const val AGENCY_ID = "387238"

    // Maps integer room count to Serbian URL path slug (exact match)
    private val ROOM_WORD = mapOf(
        1 to "jednosoban",
        2 to "dvosoban",
        3 to "trosoban",
        4 to "cetvorosoban",
        5 to "petosoban"
    )

    // Maps integer room count to HaloOglasi internal code (range params)
    private val ROOM_CODE = mapOf(
        1 to 2, 2 to 4, 3 to 6, 4 to 8, 5 to 11
    )

    private val HEATING_MAP = mapOf(
        Heating.CENTRAL.code to "1542"
    )

    fun build(filter: Filter): String {
        val citySlug  = City.fromCode(filter.city)?.displayName ?: "beograd"
        val isExact   = filter.minRooms != null && (filter.maxRooms == null || filter.minRooms == filter.maxRooms)
        val roomSlug  = if (isExact) ROOM_WORD[filter.minRooms?.toInt()] else null

        val params = buildList {
            filter.maxPrice?.let { add("cena_d_to=$it"); add("cena_d_unit=4") }
            filter.minPrice?.let { add("cena_d_from=$it"); if (filter.maxPrice == null) add("cena_d_unit=4") }
            filter.minArea?.let  { add("kvadratura_d_from=$it"); add("kvadratura_d_unit=1") }
            filter.maxArea?.let  { add("kvadratura_d_to=$it") }

            if (!isExact) {
                filter.minRooms?.let { ROOM_CODE[it.toInt()]?.let { c -> add("broj_soba_order_i_from=$c") } }
                filter.maxRooms?.let { ROOM_CODE[it.toInt()]?.let { c -> add("broj_soba_order_i_to=$c") } }
            }

            val advertisers = when (filter.adType) {
                AdType.OWNER.code  -> listOf(OWNER_ID)
                AdType.AGENCY.code -> listOf(AGENCY_ID)
                else               -> listOf(OWNER_ID, AGENCY_ID)
            }
            add("oglasivac_nekretnine_id_l=${advertisers.joinToString("%2C")}")

            HEATING_MAP[filter.heating]?.let { add("grejanje_id_l=$it") }
        }

        val basePath = if (roomSlug != null) "$BASE/$citySlug/$roomSlug" else "$BASE/$citySlug"
        return "$basePath?${params.joinToString("&")}"
    }
}
