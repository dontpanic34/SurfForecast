package com.surfcast.surfforecast

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

// Équivalents multiplateformes de LocalDateTime.now() / format("HH:mm") (java.time).

@OptIn(ExperimentalTime::class)
fun nowLocalDateTime(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

/** Heure pleine la plus proche (10h44 -> 11h00), pour le "maintenant" affiché. */
@OptIn(ExperimentalTime::class)
fun nearestHourLocalDateTime(): LocalDateTime {
    val t = (Clock.System.now() + 30.minutes).toLocalDateTime(TimeZone.currentSystemDefault())
    return LocalDateTime(t.date, LocalTime(t.hour, 0))
}

fun LocalDateTime.formatHHmm(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

fun LocalTime.formatHHmm(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
