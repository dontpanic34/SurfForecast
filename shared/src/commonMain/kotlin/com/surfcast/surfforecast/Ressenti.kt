package com.surfcast.surfforecast

/**
 * Le ciel tel qu'on le voit (et pas le pourcentage brut de la prévision) : la couverture est lue sur la même courbe que le ciel
 * dessiné (10 % de couverture prévue = presque rien, 55 % = à moitié, 80 % = quasi fermé), puis dite en un mot.
 */
fun skyWord(cloudCoverPercent: Int): String = when {
    cloudCoverPercent < 10 -> "Grand soleil"
    cloudCoverPercent < 30 -> "Ciel voilé"
    cloudCoverPercent < 55 -> "Éclaircies"
    cloudCoverPercent < 80 -> "Nuageux"
    else -> "Très couvert"
}

/** Texte court du « i » du vent : à lire d'un coup d'œil sur mobile. */
const val WIND_FEEL_INFO_TITLE = "Vent ressenti"
const val WIND_FEEL_INFO_TEXT =
    "C'est le vent moyen, plus un tiers de l'écart avec les rafales.\n" +
        "Exemple : 8 km/h, rafales 22 km/h → 13 km/h.\n" +
        "Ce chiffre donne la couleur, la phrase et la note."
