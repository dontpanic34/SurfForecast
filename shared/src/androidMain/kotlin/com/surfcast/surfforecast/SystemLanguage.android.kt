package com.surfcast.surfforecast

actual fun systemLanguageCode(): String = java.util.Locale.getDefault().language.lowercase()
