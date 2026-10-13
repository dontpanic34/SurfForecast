package com.surfcast.surfforecast

import kotlinx.browser.window

actual fun systemLanguageCode(): String = runCatching { window.navigator.language.lowercase().take(2) }.getOrDefault("")
