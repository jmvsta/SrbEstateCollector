package com.jmvstv_v.scraper.processor

import com.jmvstv_v.model.Filter
import com.jmvstv_v.model.Listing
import com.jmvstv_v.scraper.parser.HaloOglasiParser
import com.jmvstv_v.scraper.url_builder.HaloOglasiUrlBuilder

object HaloOglasiProcessor : SiteProcessor {

    override val sourceName: String = "halooglasi"

    override fun buildUrl(filter: Filter): String = HaloOglasiUrlBuilder.build(filter)

    override fun parse(html: String): List<Listing> =
        HaloOglasiParser.parse(html).map { l ->
            Listing(
                sourceSite   = sourceName,
                externalId   = l.uniqueId,
                title        = l.title ?: l.uniqueId,
                url          = l.url,
                city         = l.municipality ?: "",
                propertyType = l.rooms ?: "",
                price        = l.price,
                area         = l.area
            )
        }
}
