package com.surfcast.surfforecast

import androidx.compose.runtime.Composable

/**
 * Photo/vidéo jointe à une session : sélection et affichage dépendent de la plateforme
 * (Android : sélecteur de documents + Coil ; iOS : à venir).
 *
 * Renvoie l'action qui ouvre le sélecteur, ou null si la plateforme ne le gère pas encore
 * (le bouton "Ajouter photo / vidéo" est alors masqué).
 */
@Composable
expect fun rememberMediaPicker(onPicked: (String) -> Unit): (() -> Unit)?

/** Aperçu du média d'une session dans l'historique. */
@Composable
expect fun SessionMediaView(mediaUri: String)
