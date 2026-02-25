package com.jmvstv_v.model

data class UserFilterEntry(
    val chatId: Long,
    val userId: Int,
    val filter: Filter
)
