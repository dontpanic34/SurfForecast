package com.surfcast.surfforecast.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Portage de SurfRepository.getTides() (Android) : site api-maree.fr le plus proche du
 * spot, puis marées de la période demandée. Comme côté Android, une erreur réseau donne
 * une liste vide plutôt qu'une exception (les marées sont un complément de la prévision).
 */
class SharedTideService(
    private val httpClient: HttpClient,
    private val apiKey: String
) {
    constructor() : this(
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        },
        // Même clé que SurfRepository.kt côté Android (déjà présente dans le dépôt).
        "0093ca14ffeffadcf739be7cc77f4738"
    )

    /** Dates au format ISO (yyyy-MM-dd). */
    suspend fun getTides(spot: SpotCoordinates, fromDateIso: String, toDateIso: String): List<DailyTide> =
        try {
            val sites: MareeSitesResponse = httpClient.get("https://api-maree.fr/sites").body()
            val siteId = sites.sites
                ?.minByOrNull { site ->
                    val dLat = site.latitude - spot.latitude
                    val dLon = site.longitude - spot.longitude
                    dLat * dLat + dLon * dLon
                }
                ?.siteId ?: "cordouan"

            val extrema: MareeExtremaResponse = httpClient.get("https://api-maree.fr/tide-extrema") {
                parameter("site", siteId)
                parameter("from", fromDateIso)
                parameter("to", toDateIso)
                parameter("tz", "Europe/Paris")
                parameter("key", apiKey)
            }.body()

            extrema.data.orEmpty().map { day -> toDailyTide(day) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

    private fun toDailyTide(day: MareeDayData): DailyTide {
        val extrema = day.extrema.orEmpty()
        fun isDaytime(e: MareeExtremum) = e.time >= "06:00" && e.time <= "21:30"
        val pm = extrema.firstOrNull { it.type == "PM" && isDaytime(it) } ?: extrema.firstOrNull { it.type == "PM" }
        val bm = extrema.firstOrNull { it.type == "BM" && isDaytime(it) } ?: extrema.firstOrNull { it.type == "BM" }
        // L'API ne met le coefficient que sur les PM : on retombe sur n'importe quelle entrée qui en a un.
        val coef = pm?.coef ?: extrema.firstNotNullOfOrNull { it.coef }
        return DailyTide(
            dateIso = day.date,
            highTideTime = pm?.time,
            lowTideTime = bm?.time,
            coefficient = coef,
            allExtrema = extrema
        )
    }
}
