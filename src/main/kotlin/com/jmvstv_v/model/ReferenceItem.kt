package com.jmvstv_v.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class ReferenceItem(
    @Contextual val _id: ObjectId = ObjectId(),
    val name: String
)

@Serializable
data class ReferenceItemDto(
    val id: String? = null,
    val name: String
)

fun ReferenceItem.toDto() = ReferenceItemDto(id = _id.toHexString(), name = name)
fun ReferenceItemDto.toEntity() = ReferenceItem(
    _id = if (id != null) ObjectId(id) else ObjectId(),
    name = name
)
