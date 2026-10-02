package com.surfcast.surfforecast

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Signal "l'appli revient au premier plan", émis par la plateforme (iOS :
 * UIApplicationWillEnterForegroundNotification ; Android : onResume) et écouté par
 * SurfLogApp pour recharger des prévisions trop anciennes.
 */
object AppForeground {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun notifyResumed() {
        _events.tryEmit(Unit)
    }
}
