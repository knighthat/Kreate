package it.fast4x.rimusic.ui.styling

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color


@Immutable
expect class ColorPalette(
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
    iconButtonPlayer: Color,
) {

    val background0: Color
    val background1: Color
    val background2: Color
    val background3: Color
    val background4: Color
    val accent: Color
    val onAccent: Color
    val red: Color
    val blue: Color
    val text: Color
    val textSecondary: Color
    val textDisabled: Color
    val isDark: Boolean
    val iconButtonPlayer: Color

    fun copy(
        background0: Color = this.background0,
        background1: Color = this.background1,
        background2: Color = this.background2,
        background3: Color = this.background3,
        background4: Color = this.background4,
        accent: Color = this.accent,
        onAccent: Color = this.onAccent,
        red: Color = this.red,
        blue: Color = this.blue,
        text: Color = this.text,
        textSecondary: Color = this.textSecondary,
        textDisabled: Color = this.textDisabled,
        isDark: Boolean = this.isDark,
        iconButtonPlayer: Color = this.iconButtonPlayer
    ): ColorPalette
}

val DefaultDarkColorPalette = ColorPalette(
    background0 = Color(0xff16171d),
    background1 = Color(0xff1f2029),
    background2 = Color(0xff2b2d3b),
    background3 = Color(0xff495057),
    background4 = Color(0xff333333),
    text = Color(0xffe1e1e2),
    textSecondary = Color(0xffa3a4a6),
    textDisabled = Color(0xff6f6f73),
    iconButtonPlayer = Color(0xffe1e1e2),
    accent = Color(0xFF2b9348),
    onAccent = Color.White,
    red = Color(0xffbf4040),
    blue = Color(0xff4472cf),
    isDark = true
)

val DefaultLightColorPalette = ColorPalette(
    background0 = Color(0xfffdfdfe),
    background1 = Color(0xfff8f8fc),
    background2 = Color(0xffeaeaf5),
    background3 = Color(0xffeaeafd),
    background4 = Color(0xffeaeafd),
    text = Color(0xff212121),
    textSecondary = Color(0xff656566),
    textDisabled = Color(0xff9d9d9d),
    iconButtonPlayer = Color(0xff212121),
    accent = Color(0xFF2b9348),
    onAccent = Color.White,
    red = Color(0xffbf4040),
    blue = Color(0xff4472cf),
    isDark = false
)

val PureBlackColorPalette = DefaultDarkColorPalette.copy(
    background0 = Color.Black,
    background1 = Color.Black,
    background2 = Color.Black,
    accent = Color.White,
    onAccent = Color.DarkGray
)

val ModernBlackColorPalette = DefaultDarkColorPalette.copy(
    background0 = Color.Black,
    background1 = Color.Black,
    //background2 = DefaultDarkColorPalette.background2, // Color.Black,
    background2 = Color.Black,
    background3 = DefaultDarkColorPalette.accent
)