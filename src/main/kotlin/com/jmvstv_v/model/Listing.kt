package com.jmvstv_v.model

import kotlinx.serialization.Serializable

@Serializable
data class Listing(
    val sourceSite: String,
    val externalId: String,
    val title: String,
    val url: String,
    val city: String,
    val propertyType: String,
    val price: Int?,
    val area: Int?,
    val advertiserType: String? = null,
    val heatingType: String? = null
)
