package com.jmvstv_v.scraper.parser

import org.jsoup.Jsoup

private const val BASE_URL = "https://www.nekretnine.rs"

data class NekretninListing(
    val uniqueId: String,
    val url: String,
    val title: String?    = null,
    val location: String? = null,
    val price: Int?       = null,
    val area: Int?        = null
)

/**
 * Parses HTML responses from nekretnine.rs into [NekretninListing] objects.
 */
object NekretninParser {

    /**
     * Parses the nekretnine.rs listing page HTML.
     *
     * @param html Raw HTML of the nekretnine.rs listing page.
     * @return List of parsed listings, or an empty list if none found.
     */
    fun parse(html: String): List<NekretninListing> {
        val doc = Jsoup.parse(html)

        return doc.select("div.advert-list div.row.offer").mapNotNull { card ->
            val href = card.selectFirst("h2.offer-title a")?.attr("href")
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

            val uniqueId = href.trimEnd('/').substringAfterLast('/')
                .takeIf { it.isNotBlank() } ?: return@mapNotNull null

            val url      = "$BASE_URL$href"
            val title    = card.selectFirst("h2.offer-title a")?.text()?.takeIf { it.isNotBlank() }
            val location = card.selectFirst("p.offer-location")?.text()?.trim()?.takeIf { it.isNotBlank() }

            val price = card.selectFirst("div.d-flex.justify-content-between p.offer-price:not(.offer-price--invert) span")
                ?.text()
                ?.replace("[^0-9]".toRegex(), "")
                ?.toIntOrNull()

            val area = card.selectFirst("p.offer-price.offer-price--invert span")
                ?.text()
                ?.replace("[^0-9]".toRegex(), "")
                ?.toIntOrNull()

            NekretninListing(
                uniqueId = uniqueId,
                url      = url,
                title    = title,
                location = location,
                price    = price,
                area     = area
            )
        }
    }
}
