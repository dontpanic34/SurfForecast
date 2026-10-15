package com.surfcast.surfforecast

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class ObservedNowTest {
    private val paris = TimeZone.of("Europe/Paris")
    private val vendays = ObservedNow(
        stationName = "VENDAYS-MONTALIVE", distanceKm = 4.0, timeUtc = LocalDateTime(2026, 10, 13, 12, 0),
        windKmh = 7, gustKmh = 12, directionDeg = 240, rainMm = 0.4, tempC = 20.3
    )

    @Test
    fun lineGivesWindGustsAndRainInLocalTime() {
        assertEquals(
            "Mesuré à 14 h 00, Vendays-Montalivet (4 km) : vent 7 km/h OSO, rafales 12 km/h · pluie 0,4 mm",
            observedLine(vendays, "kmh", paris)
        )
    }

    @Test
    fun lineSkipsGustsAndRainWhenTheyAddNothing() {
        val calm = vendays.copy(gustKmh = 7, rainMm = 0.0, directionDeg = null)
        assertEquals("Mesuré à 14 h 00, Vendays-Montalivet (4 km) : vent 7 km/h", observedLine(calm, "kmh", paris))
    }
}
