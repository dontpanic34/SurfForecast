package com.surfcast.surfforecast

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSBundle
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIViewController

private val iosPrefs: KeyValueStore by lazy { UserDefaultsStore() }

private val sessionLogStore: SessionLogStore by lazy { SessionLogRoomStore(sessionLogDatabase().sessionLogDao()) }

private val appVersion: String? by lazy {
    NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String
}

// Retour de l'appli au premier plan -> recharge des prévisions trop anciennes.
private val foregroundObserver by lazy {
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationWillEnterForegroundNotification,
        `object` = null,
        queue = null
    ) { _ -> AppForeground.notifyResumed() }
}

fun MainViewController(): UIViewController {
    foregroundObserver
    return ComposeUIViewController { SurfLogApp(prefs = iosPrefs, sessionLogStore = sessionLogStore, appVersion = appVersion) }
}
