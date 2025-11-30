package me.knighthat.kreate.constant

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

enum class ColorScheme {

    LIGHT, DARK, DYNAMIC;

    @Composable
    fun eval(): Boolean =
        when( this ) {
            LIGHT   -> false
            DARK    -> true
            DYNAMIC -> isSystemInDarkTheme()
        }
}