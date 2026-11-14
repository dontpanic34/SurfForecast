package com.surfcast.surfforecast

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Ce que dit un modèle du vent pour une heure : vitesse moyenne et rafales (km/h), direction d'où il vient (°). */
data class WindVoiceIn(val label: String, val speed: Double, val dir: Double, val gust: Double)

/** Le vent retenu pour une heure, et les modèles dont il vient. */
data class WindConsensus(val speed: Double, val dir: Double, val gust: Double, val source: String)

/**
 * Le consensus du vent : la médiane des vitesses de tous les modèles disponibles (un modèle qui s'écarte fortement ne tire pas la
 * moyenne), la direction moyenne pondérée par la vitesse, et pour les rafales le milieu entre la médiane des modèles et la moyenne des deux plus
 * fortes annoncées : les fortes comptent pour la sécurité, et les modèles mondiaux, trop grossiers près de la côte, les sous-estiment (20 octobre : 50 km/h
 * pour ECMWF et GFS, 70 à 87 pour l'AROME HD, ce que montrent les sites de prévision). Avec deux modèles seulement, c'est leur moyenne.
 * Null sans modèle.
 */
fun windConsensus(voices: List<WindVoiceIn>): WindConsensus? {
    if (voices.isEmpty()) return null
    val speeds = voices.map { it.speed }.sorted()
    val mid = speeds.size / 2
    val speed = if (speeds.size % 2 == 1) speeds[mid] else (speeds[mid - 1] + speeds[mid]) / 2.0
    var x = 0.0
    var y = 0.0
    for (v in voices) {
        val r = v.dir * PI / 180.0
        x += v.speed * sin(r)
        y += v.speed * cos(r)
    }
    val dir = if (x == 0.0 && y == 0.0) voices.first().dir else (atan2(x, y) * 180.0 / PI + 360.0) % 360.0
    // Rafales : à mi-chemin entre la médiane des modèles et la moyenne des deux plus fortes (bilan du 14 novembre : la moyenne des
    // deux plus fortes seule annonçait 6 km/h de trop en moyenne ; la médiane seule manquait de 7 km/h les rafales de 45 km/h et plus).
    val gusts = voices.map { maxOf(it.gust, it.speed) }.sortedDescending()
    val gm = gusts.size / 2
    val gustMedian = if (gusts.size % 2 == 1) gusts[gm] else (gusts[gm - 1] + gusts[gm]) / 2.0
    val gust = maxOf((gustMedian + gusts.take(2).average()) / 2.0, speed)
    return WindConsensus(speed, dir, gust, voices.joinToString("+") { it.label })
}
