package me.knighthat.kreate.di

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue


class TopLayoutConfiguration {

    var title: String by mutableStateOf( "" )
    var isAppReady: Boolean by mutableStateOf( false )
        private set

    /**
     * Mark the initialization process is over.
     * Meaning, it can now display content to user.
     */
    fun showContent() {
        isAppReady = true
    }
}