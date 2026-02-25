package com.jmvstv_v.scraper.parser

import org.jsoup.Jsoup

private const val BASE_URL = "https://www.halooglasi.com"

data class HaloOglasiListing(
    val uniqueId: String,
    val url: String,
    val title: String?        = null,
    val municipality: String? = null,
    val area: Int?            = null,
    val rooms: String?        = null,
    val price: Int?           = null
)

/**
 * Parses HTML responses from halooglasi.com into [HaloOglasiListing] objects.
 */
object HaloOglasiParser {

    /**
     * Parses the HaloOglasi listing page HTML.
     *
     * @param html Raw HTML of the HaloOglasi listing page.
     * @return List of parsed listings, or an empty list if none found.
     */
    fun parse(html: String): List<HaloOglasiListing> {
        val doc = Jsoup.parse(html)

        return doc.select("div.product-item[data-id]").mapNotNull { card ->
            val uniqueId = card.attr("data-id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val href = card.selectFirst("h3.product-title a")?.attr("href")
                ?: card.selectFirst("a.a-images")?.attr("href")
                ?: return@mapNotNull null
            val cleanHref = href.substringBefore("?")
            val url = "$BASE_URL$cleanHref"

            val title = card.selectFirst("h3.product-title a")?.text()?.takeIf { it.isNotBlank() }

            val municipality = card.select("ul.subtitle-places li")
                .getOrNull(1)?.text()?.trim()?.takeIf { it.isNotBlank() }

            val price = card.selectFirst("div.central-feature span[data-value]")
                ?.attr("data-value")?.toIntOrNull()

            val valueWrappers = card.select("div.value-wrapper")
            var area: Int? = null
            var rooms: String? = null
            for (vw in valueWrappers) {
                val legend = vw.selectFirst("span.legend")?.text() ?: continue
                val raw = vw.ownText().trim()
                when {
                    legend.contains("Kvadratura", ignoreCase = true) ->
                        area = raw.replace("[^0-9]".toRegex(), "").toIntOrNull()
                    legend.contains("soba", ignoreCase = true) ->
                        rooms = raw.takeIf { it.isNotBlank() }
                }
            }

            HaloOglasiListing(
                uniqueId     = uniqueId,
                url          = url,
                title        = title,
                municipality = municipality,
                area         = area,
                rooms        = rooms,
                price        = price
            )
        }
    }
}
