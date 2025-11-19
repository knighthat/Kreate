package it.fast4x.rimusic.utils

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow

object AppLifecycleTracker : DefaultLifecycleObserver {
    private val _appState = MutableStateFlow(AppState.BACKGROUND)
    val appRunningInForeground: Boolean
        get() = _appState.value == AppState.FOREGROUND

    enum class AppState {
        FOREGROUND, BACKGROUND
    }

    override fun onStart(owner: LifecycleOwner) {
        // App has come to the foreground (or is already there)
        _appState.value = AppState.FOREGROUND
    }

    override fun onStop(owner: LifecycleOwner) {
        // App has gone to the background
        _appState.value = AppState.BACKGROUND
    }
}