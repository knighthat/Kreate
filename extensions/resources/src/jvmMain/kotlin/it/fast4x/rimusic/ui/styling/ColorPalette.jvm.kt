package it.fast4x.rimusic.ui.styling

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color


@Immutable
actual class ColorPalette actual constructor(
    actual val background0: Color,
    actual val background1: Color,
    actual val background2: Color,
    actual val background3: Color,
    actual val background4: Color,
    actual val accent: Color,
    actual val onAccent: Color,
    actual val red: Color,
    actual val blue: Color,
    actual val text: Color,
    actual val textSecondary: Color,
    actual val textDisabled: Color,
    actual val isDark: Boolean,
    actual val iconButtonPlayer: Color
) {

    actual fun copy(
        background0: Color,
        background1: Color,
        background2: Color,
        background3: Color,
        background4: Color,
        accent: Color,
        onAccent: Color,
        red: Color,
        blue: Color,
        text: Color,
        textSecondary: Color,
        textDisabled: Color,
        isDark: Boolean,
        iconButtonPlayer: Color
    ) =
        ColorPalette(
            background0 = background0,
            background1 = background1,
            background2 = background2,
            background3 = background3,
            background4 = background4,
            accent = accent,
            onAccent = onAccent,
            red = red,
            blue = blue,
            text = text,
            textSecondary = textSecondary,
            textDisabled = textDisabled,
            isDark = isDark,
            iconButtonPlayer = iconButtonPlayer
        )
}