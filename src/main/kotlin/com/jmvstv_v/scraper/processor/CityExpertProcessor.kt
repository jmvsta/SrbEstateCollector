package com.jmvstv_v.scraper.processor

import com.jmvstv_v.model.Filter
import com.jmvstv_v.model.Listing
import com.jmvstv_v.scraper.parser.CityExpertListing
import com.jmvstv_v.scraper.parser.CityExpertParser
import com.jmvstv_v.scraper.url_builder.CityExpertUrlBuilder

object CityExpertProcessor : SiteProcessor {

    override val sourceName: String = "cityexpert"

    override fun buildUrl(filter: Filter): String = CityExpertUrlBuilder.build(filter)

    override fun parse(html: String): List<Listing> =
        CityExpertParser.parse(html).map { it.toListing() }

    private fun CityExpertListing.toListing(): Listing {
        val cityName = when (cityId) {
            1    -> "belgrade"
            2    -> "novi-sad"
            3    -> "subotica"
            4    -> "nis"
            else -> ""
        }
        val type = if (rentOrSale == "r") "properties-for-rent" else "properties-for-sale"
        val url = "https://cityexpert.rs/en/$type/$cityName/$propId/link"
        val title = listOfNotNull(street, municipality).joinToString(", ").ifBlank { uniqueID }
        return Listing(
            sourceSite     = sourceName,
            externalId     = uniqueID,
            title          = title,
            url            = url,
            city           = cityName,
            propertyType   = structure ?: "",
            price          = price,
            area           = size,
            advertiserType = rentOrSale
        )
    }
}
