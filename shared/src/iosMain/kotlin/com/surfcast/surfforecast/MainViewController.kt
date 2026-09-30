package com.surfcast.surfforecast

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

private val iosPrefs: KeyValueStore by lazy { UserDefaultsStore() }

fun MainViewController(): UIViewController = ComposeUIViewController { SurfLogApp(prefs = iosPrefs) }
