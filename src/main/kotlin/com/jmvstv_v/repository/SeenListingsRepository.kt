package com.jmvstv_v.repository

import com.jmvstv_v.model.Listing

interface SeenListingsRepository {

    fun getSeenIds(filterId: Int, source: String, uniqueIds: List<String>): Set<String>

    /** Persists [listings] as seen for the given user and filter, storing all card fields. */
    fun markSeen(userId: Int, filterId: Int, listings: List<Listing>)
}
