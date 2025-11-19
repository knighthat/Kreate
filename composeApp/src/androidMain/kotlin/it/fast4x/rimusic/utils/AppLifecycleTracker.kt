package it.fast4x.rimusic.utils

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update


object AppLifecycleTracker: DefaultLifecycleObserver {

    private val _state = MutableStateFlow(false)

    /**
     * @return `true` when app is visible to user
     */
    fun isInForeground() = _state.value

    /**
     * @return `true` is no longer visible to user
     */
    fun isInBackground() = !_state.value
    
    override fun onStart( owner: LifecycleOwner ) = _state.update { true }

    override fun onStop( owner: LifecycleOwner ) = _state.update { false }
}