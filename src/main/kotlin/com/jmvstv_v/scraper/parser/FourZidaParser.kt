package com.jmvstv_v.scraper.parser

import org.jsoup.Jsoup

private const val BASE_URL     = "https://www.4zida.rs"
private val LISTING_HREF_REGEX = Regex("""/izdavanje-stanova/.+/([0-9a-f]{24})$""")

data class FourZidaListing(
    val uniqueId: String,
    val url: String,
    val street: String?       = null,
    val municipality: String? = null,
    val rooms: String?        = null,
    val price: Int?           = null
)

/**
 * Parses HTML responses from 4zida.rs into [FourZidaListing] objects.
 */
object FourZidaParser {

    /**
     * Parses the 4zida.rs listing page HTML.
     *
     * Each card has two `<a>` elements sharing the same href:
     * 1. Main link — contains `<p>` children: street, location, price.
     * 2. Detail link — plain text: "0.5 soba | prazno | centralno".
     *
     * @param html Raw HTML of the 4zida.rs listing page.
     * @return List of parsed listings, or an empty list if none found.
     */
    fun parse(html: String): List<FourZidaListing> {
        val doc = Jsoup.parse(html)

        val linksByHref = doc.select("a[href]")
            .filter { LISTING_HREF_REGEX.containsMatchIn(it.attr("href")) }
            .groupBy { it.attr("href") }

        return linksByHref.mapNotNull { (href, links) ->
            val uniqueId = href.substringAfterLast("/")
            val mainLink = links.firstOrNull { it.select("p").isNotEmpty() }
                ?: return@mapNotNull null
            val ps           = mainLink.select("p")
            val street       = ps.getOrNull(0)?.text()?.takeIf { it.isNotBlank() }
            val municipality = ps.getOrNull(1)?.text()?.takeIf { it.isNotBlank() }
            val price        = ps.getOrNull(2)?.text()
                ?.replace("[^0-9]".toRegex(), "")?.toIntOrNull()

            val rooms = links.firstOrNull { it.select("p").isEmpty() }
                ?.text()?.substringBefore("|")?.trim()?.takeIf { it.isNotBlank() }

            FourZidaListing(
                uniqueId     = uniqueId,
                url          = "$BASE_URL$href",
                street       = street,
                municipality = municipality,
                rooms        = rooms,
                price        = price
            )
        }
    }
}
