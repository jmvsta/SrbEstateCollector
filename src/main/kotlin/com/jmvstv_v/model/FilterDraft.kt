package com.jmvstv_v.model

enum class FilterStep {
    NAME, CITY, PROPERTY_TYPE, ROOMS,
    MIN_PRICE, MAX_PRICE, MIN_AREA, MAX_AREA,
    AD_TYPE, HEATING,
    CARD,
    EDIT_NAME, EDIT_ROOMS, EDIT_PRICE, EDIT_AREA
}

data class FilterDraft(
    var step: FilterStep = FilterStep.NAME,
    var editingFilterId: Int? = null,
    var cardMessageId: Long? = null,
    var promptMessageId: Long? = null,
    var creationMessageIds: MutableList<Long> = mutableListOf(),
    var name: String? = null,
    var city: Int? = null,
    var propertyType: String? = null,
    var propertyTypeSelection: MutableList<Int> = mutableListOf(),
    var minRooms: Int? = null,
    var maxRooms: Int? = null,
    var minPrice: Int? = null,
    var maxPrice: Int? = null,
    var minArea: Int? = null,
    var maxArea: Int? = null,
    var adType: Int? = null,
    var heating: Int? = null
)
