package com.jmvstv_v.repository

import com.jmvstv_v.model.Filter
import com.jmvstv_v.model.FilterDraft

interface FilterRepository {

    fun findByUserId(userId: Int): List<Pair<Int, String>>

    fun findByIdAndUserId(filterId: Int, userId: Int): Filter?

    fun insert(userId: Int, draft: FilterDraft)

    fun update(filterId: Int, draft: FilterDraft)

    fun deleteById(filterId: Int)

    /** Deactivates all filters for [userId] then sets [filterId] as active. */
    fun setActiveFilter(userId: Int, filterId: Int)

    /**
     * Clears active flag on [filterId] for [userId] if it is currently active.
     * Returns `true` if the filter was active.
     */
    fun clearActiveFilterIfMatches(userId: Int, filterId: Int): Boolean
}
