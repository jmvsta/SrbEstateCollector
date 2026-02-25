package com.jmvstv_v.scraper.processor

import com.jmvstv_v.model.Filter
import com.jmvstv_v.model.Listing
import com.jmvstv_v.scraper.parser.FourZidaParser
import com.jmvstv_v.scraper.url_builder.FourZidaUrlBuilder

object FourZidaProcessor : SiteProcessor {

    override val sourceName: String = "4zida"

    override fun buildUrl(filter: Filter): String = FourZidaUrlBuilder.build(filter)

    override fun parse(html: String): List<Listing> =
        FourZidaParser.parse(html).map { l ->
            val title = listOfNotNull(l.street, l.municipality).joinToString(", ").ifBlank { l.uniqueId }
            Listing(
                sourceSite = sourceName,
                externalId = l.uniqueId,
                title = title,
                url = l.url,
                city = l.municipality ?: "",
                propertyType = l.rooms ?: "",
                price = l.price,
                area           = null,
                advertiserType = null,
                heatingType    = null
            )
        }
}
