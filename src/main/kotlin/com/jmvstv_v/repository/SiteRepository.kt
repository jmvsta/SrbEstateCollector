package com.jmvstv_v.repository

interface SiteRepository {
    /** Inserts seed rows if they don't already exist (preserves active flag). */
    fun seed()
    fun findActiveNames(): Set<String>
}
