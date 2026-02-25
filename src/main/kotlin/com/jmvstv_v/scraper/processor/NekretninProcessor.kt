package com.jmvstv_v.scraper.processor

import com.jmvstv_v.model.Filter
import com.jmvstv_v.model.Listing
import com.jmvstv_v.scraper.parser.NekretninParser
import com.jmvstv_v.scraper.url_builder.NekretninUrlBuilder

object NekretninProcessor : SiteProcessor {

    override val sourceName: String = "nekretnine"

    override fun buildUrl(filter: Filter): String = NekretninUrlBuilder.build(filter)

    override fun parse(html: String): List<Listing> =
        NekretninParser.parse(html).map { l ->
            Listing(
                sourceSite   = sourceName,
                externalId   = l.uniqueId,
                title        = l.title ?: l.uniqueId,
                url          = l.url,
                city         = l.location ?: "",
                propertyType = "",
                price        = l.price,
                area         = l.area
            )
        }
}
