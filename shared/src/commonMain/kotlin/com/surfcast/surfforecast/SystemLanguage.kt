package com.surfcast.surfforecast

/** Code de langue (« fr », « en », « de », « es »…) du téléphone ou du navigateur, en minuscules ; vide si inconnu. */
expect fun systemLanguageCode(): String

/** La langue choisie la première fois : celle du système si l'appli la propose (voir APP_LANGUAGES), sinon le français. */
fun defaultAppLanguage(system: String = systemLanguageCode()): String =
    APP_LANGUAGES.firstOrNull { it.code == system.lowercase().take(2) }?.code ?: "fr"
