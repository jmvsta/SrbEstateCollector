package com.jmvstv_v.scraper.processor

import com.jmvstv_v.model.Filter
import com.jmvstv_v.model.Listing

interface SiteProcessor {

    val sourceName: String

    fun buildUrl(filter: Filter): String

    fun parse(html: String): List<Listing>
}
