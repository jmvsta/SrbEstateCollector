package com.jmvstv_v.scraper.parser

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory

@Serializable
data class CityExpertListing(
    val uniqueID: String,
    val propId: Int,
    val cityId: Int?          = null,
    val street: String?       = null,
    val size: Int?            = null,
    val structure: String?    = null,
    val municipality: String? = null,
    val price: Int?           = null,
    val rentOrSale: String?   = null
)

/**
 * Parses HTML responses from cityexpert.rs into [CityExpertListing] objects.
 */
object CityExpertParser {

    private val log = LoggerFactory.getLogger(CityExpertParser::class.java)

    private val scriptRegex = Regex(
        """<script[^>]+id="ng-state"[^>]*>(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses the CityExpert listing page HTML and extracts all listings embedded in the Angular state.
     *
     * @param html Raw HTML of the CityExpert listing page.
     * @return List of parsed listings, or an empty list if parsing fails.
     */
    fun parse(html: String): List<CityExpertListing> {
        val jsonStr = scriptRegex.find(html)?.groupValues?.get(1)?.trim()
            ?: return emptyList()

        return try {
            val root = json.parseToJsonElement(jsonStr).jsonObject

            val resultArray: JsonArray = root.values
                .asSequence()
                .mapNotNull { (it as? JsonObject)?.get("b") }
                .mapNotNull { (it as? JsonObject)?.get("result") }
                .filterIsInstance<JsonArray>()
                .firstOrNull()
                ?: return emptyList()

            resultArray.map { json.decodeFromJsonElement<CityExpertListing>(it) }
        } catch (e: Exception) {
            log.error("CityExpert parse error", e)
            emptyList()
        }
    }
}
