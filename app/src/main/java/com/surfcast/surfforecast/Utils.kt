package com.surfcast.surfforecast

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt

fun getCardinalDirection(angle: Double): String {
    val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO")
    val index = ((angle % 360) / 22.5).roundToInt() % 16
    return directions[index]
}

fun calculateWaveEnergy(heightMeters: Double, periodSeconds: Double): Int {
    val density = 1025.0
    val gravity = 9.81
    val energyJoules = (density * gravity * gravity / (64 * PI)) * (heightMeters * heightMeters) * periodSeconds
    return (energyJoules / 100).toInt()
}

fun parseOpenMeteoTime(isoString: String): LocalDateTime {
    return LocalDateTime.parse(isoString, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}

fun formatHour(dateTime: LocalDateTime): String {
    return String.format(Locale.US, "%02d:00", dateTime.hour)
}