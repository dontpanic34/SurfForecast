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

        val (hours, tide) = store.conditionsFor("Soulac", LocalDate(2026, 10, 1), now = LocalDateTime(2026, 10, 2, 8, 0))!!
        // Prévision la plus récente pour ce jour-là (9h), pas celle de 8h.
        assertEquals(3, hours.single().windSpeedKmh)
        assertEquals("AROME HD", hours.single().windSource)
        assertNull(tide)
        assertNull(store.conditionsFor("Lacanau", LocalDate(2026, 10, 1), now = LocalDateTime(2026, 10, 2, 8, 0)))
    }

    @Test
    fun snapshotsOlderThanTwoWeeksAreDropped() {
        val store = ForecastHistoryStore(InMemoryKeyValueStore())
        store.record("Soulac", ForecastEngineConfig(), listOf(hour(1, 10, 8)), emptyMap(), now = LocalDateTime(2026, 10, 1, 8, 0))
        store.record("Soulac", ForecastEngineConfig(), listOf(hour(4, 10, 8)), emptyMap(), now = LocalDateTime(2026, 10, 4, 8, 0))
        // Trois jours plus tard, les deux copies sont encore là (14 jours de mémoire).
        assertEquals(2, store.load(now = LocalDateTime(2026, 10, 4, 9, 0)).size)
        // Seize jours plus tard, la première a disparu.
        val later = store.load(now = LocalDateTime(2026, 10, 17, 9, 0))
        assertEquals(1, later.size)
        assertTrue(later.single().loadedAt.date == LocalDate(2026, 10, 4))
    }

    @Test
    fun onlyOneCopyIsKeptPerDayAndSpot() {
        val store = ForecastHistoryStore(InMemoryKeyValueStore())
        store.record("Soulac", ForecastEngineConfig(), listOf(hour(1, 10, 8)), emptyMap(), now = LocalDateTime(2026, 10, 1, 8, 0))
        store.record("Soulac", ForecastEngineConfig(), listOf(hour(1, 10, 5)), emptyMap(), now = LocalDateTime(2026, 10, 1, 12, 0))
        store.record("Lacanau", ForecastEngineConfig(), listOf(hour(1, 10, 9)), emptyMap(), now = LocalDateTime(2026, 10, 1, 12, 5))
        val kept = store.load(now = LocalDateTime(2026, 10, 1, 13, 0))
        assertEquals(2, kept.size)
        assertEquals(5, kept.first { it.spotName == "Soulac" }.hours.single().windKmh)
    }
}
