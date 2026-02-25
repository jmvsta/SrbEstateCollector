package com.jmvstv_v.repository

import com.jmvstv_v.model.Filter
import com.jmvstv_v.model.FilterDraft
import com.jmvstv_v.model.FiltersTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class ExposedFilterRepository : FilterRepository {

    override fun findByUserId(userId: Int): List<Pair<Int, String>> =
        transaction {
            FiltersTable.selectAll()
                .where { FiltersTable.userId eq userId }
                .map { it[FiltersTable.id] to it[FiltersTable.name] }
        }

    override fun findByIdAndUserId(filterId: Int, userId: Int): Filter? =
        transaction {
            FiltersTable.selectAll()
                .where { (FiltersTable.id eq filterId) and (FiltersTable.userId eq userId) }
                .singleOrNull()
                ?.let { Filter.fromRow(it) }
        }

    override fun insert(userId: Int, draft: FilterDraft) {
        val filterName = draft.name ?: "Без названия"
        transaction {
            FiltersTable.insert {
                it[name]                = filterName
                it[FiltersTable.userId] = userId
                it[active]              = false
                it[city]                = draft.city
                it[propertyType]        = draft.propertyType
                it[minRooms]            = draft.minRooms
                it[maxRooms]            = draft.maxRooms
                it[minPrice]            = draft.minPrice
                it[maxPrice]            = draft.maxPrice
                it[minArea]             = draft.minArea
                it[maxArea]             = draft.maxArea
                it[adType]              = draft.adType
                it[heating]             = draft.heating
            }
        }
    }

    override fun update(filterId: Int, draft: FilterDraft) {
        val filterName = draft.name ?: "Без названия"
        transaction {
            FiltersTable.update({ FiltersTable.id eq filterId }) {
                it[name]         = filterName
                it[city]         = draft.city
                it[propertyType] = draft.propertyType
                it[minRooms]     = draft.minRooms
                it[maxRooms]     = draft.maxRooms
                it[minPrice]     = draft.minPrice
                it[maxPrice]     = draft.maxPrice
                it[minArea]      = draft.minArea
                it[maxArea]      = draft.maxArea
                it[adType]       = draft.adType
                it[heating]      = draft.heating
            }
        }
    }

    override fun deleteById(filterId: Int) {
        transaction {
            FiltersTable.deleteWhere { FiltersTable.id eq filterId }
        }
    }

    override fun setActiveFilter(userId: Int, filterId: Int) {
        transaction {
            FiltersTable.update({ FiltersTable.userId eq userId }) { it[active] = false }
            FiltersTable.update({ (FiltersTable.id eq filterId) and (FiltersTable.userId eq userId) }) { it[active] = true }
        }
    }

    override fun clearActiveFilterIfMatches(userId: Int, filterId: Int): Boolean =
        transaction {
            val row = FiltersTable.selectAll()
                .where { (FiltersTable.id eq filterId) and (FiltersTable.userId eq userId) }
                .singleOrNull() ?: return@transaction false
            val wasActive = row[FiltersTable.active]
            if (wasActive) {
                FiltersTable.update({ FiltersTable.id eq filterId }) { it[active] = false }
            }
            wasActive
        }
}
