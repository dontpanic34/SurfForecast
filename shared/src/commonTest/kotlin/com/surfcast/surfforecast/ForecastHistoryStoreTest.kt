package com.surfcast.surfforecast

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForecastHistoryStoreTest {

    private fun hour(day: Int, h: Int, wind: Int) = HourlyUiModel(
        timeFormatted = "$h:00",
        rawTime = LocalDateTime(2026, 10, day, h, 0),
        waveHeight = 1.2,
        wavePeriod = 11.0,
        waveDirection = 270f,
        energyKj = 0,
        windSpeedKmh = wind,
        windDirectionStr = "NO",
        weatherCode = 0,
        temperature = 18,
        windSource = "AROME HD"
    )

    @Test
    fun yesterdayConditionsComeFromLatestForecastForThatDay() {
        val store = ForecastHistoryStore(InMemoryKeyValueStore())
        val forecast = listOf(hour(1, 10, 8), hour(2, 10, 15))
        store.record("Soulac", ForecastEngineConfig(), forecast, mapOf(LocalDate(2026, 10, 1) to DailyTideInfo("07:34", "13:39", 93)),
            now = LocalDateTime(2026, 10, 1, 8, 0))
        store.record("Soulac", ForecastEngineConfig(), listOf(hour(1, 10, 3)), emptyMap(),
            now = LocalDateTime(2026, 10, 1, 9, 0))

        val (hours, tide) = store.conditionsFor("Soulac", LocalDate(2026, 10, 1))!!
        // Prévision la plus récente pour ce jour-là (9h), pas celle de 8h.
        assertEquals(3, hours.single().windSpeedKmh)
        assertEquals("AROME HD", hours.single().windSource)
        assertNull(tide)
        assertNull(store.conditionsFor("Lacanau", LocalDate(2026, 10, 1)))
    }

    @Test
    fun snapshotsOlderThanTwoDaysAreDropped() {
        val store = ForecastHistoryStore(InMemoryKeyValueStore())
        store.record("Soulac", ForecastEngineConfig(), listOf(hour(1, 10, 8)), emptyMap(), now = LocalDateTime(2026, 10, 1, 8, 0))
        store.record("Soulac", ForecastEngineConfig(), listOf(hour(4, 10, 8)), emptyMap(), now = LocalDateTime(2026, 10, 4, 8, 0))
        val kept = store.load(now = LocalDateTime(2026, 10, 4, 9, 0))
        assertEquals(1, kept.size)
        assertTrue(kept.single().loadedAt.date == LocalDate(2026, 10, 4))
    }
}
