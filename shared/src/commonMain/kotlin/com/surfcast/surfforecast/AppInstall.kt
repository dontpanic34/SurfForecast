package com.surfcast.surfforecast

/**
 * Installation du site comme une appli (version web). [platform] : "android", "ios" ou "other".
 * Android (Chrome) : [prompt] ouvre la fenêtre d'installation du navigateur quand [canPrompt] est vrai.
 * iPhone : Apple interdit à un site d'ajouter une icône lui-même, on affiche une carte guidée.
 */
class AppInstall(
    val platform: String,
    val isInstalled: () -> Boolean,
    val canPrompt: () -> Boolean,
    val prompt: () -> Unit
)
