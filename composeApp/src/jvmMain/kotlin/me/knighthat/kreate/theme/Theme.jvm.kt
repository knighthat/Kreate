package me.knighthat.kreate.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable


actual fun isDynamicColorSupported(): Boolean = false

@Composable
actual fun getDynamicColorScheme( darkTheme: Boolean ): ColorScheme =
    throw UnsupportedOperationException("Dynamic color scheme isn't available on JVM")