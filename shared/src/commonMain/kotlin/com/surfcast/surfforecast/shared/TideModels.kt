package com.surfcast.surfforecast.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MareeSite(
    @SerialName("site_id") val siteId: String,
    @SerialName("site_name") val siteName: String = "",
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class MareeSitesResponse(val sites: List<MareeSite>? = null)

@Serializable
data class MareeExtremum(
    val type: String,
    val time: String,
    val height: Double = 0.0,
    val coef: Int? = null
)

@Serializable
data class MareeDayData(
    val date: String,
    val extrema: List<MareeExtremum>? = null
)

@Serializable
data class MareeExtremaResponse(
    @SerialName("site_id") val siteId: String? = null,
    val data: List<MareeDayData>? = null
)

/**
 * Une pleine mer + une basse mer "de jour" (06:00-21:30, pensé pour le surf) par date,
 * comme DailyTideInfo côté Android. `allExtrema` garde toutes les marées du jour (nuit
 * comprise), dont le widget aura besoin pour la vraie prochaine marée.
 */
data class DailyTide(
    val dateIso: String,
    val highTideTime: String?,
    val lowTideTime: String?,
    val coefficient: Int?,
    val allExtrema: List<MareeExtremum>
)
