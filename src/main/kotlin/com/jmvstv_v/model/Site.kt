package com.jmvstv_v.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Site(
    @Contextual val _id: ObjectId = ObjectId(),
    val name: String,
    val baseUrl: String,
    val enabled: Boolean = true
)

@Serializable
data class SiteDto(
    val id: String? = null,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean = true
)

fun Site.toDto() = SiteDto(id = _id.toHexString(), name = name, baseUrl = baseUrl, enabled = enabled)
fun SiteDto.toEntity() = Site(
    _id = if (id != null) ObjectId(id) else ObjectId(),
    name = name,
    baseUrl = baseUrl,
    enabled = enabled
)
