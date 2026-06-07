package it.fast4x.rimusic.ui.styling

import android.content.Context
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.enums.ColorPaletteMode


fun customColorPalette(colorPalette: ColorPalette, context: Context, isSystemInDarkTheme: Boolean): ColorPalette {
    val customThemeLight = colorPalette.copy(
        background0 = Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_0.value,
        background1 = Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_1.value,
        background2 = Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_2.value,
        background3 = Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_3.value,
        background4 = Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_4.value,
        text = Preferences.CUSTOM_LIGHT_TEXT.value,
        textSecondary = Preferences.CUSTOM_LIGHT_TEXT_SECONDARY.value,
        textDisabled = Preferences.CUSTOM_LIGHT_TEXT_DISABLED.value,
        iconButtonPlayer = Preferences.CUSTOM_LIGHT_PLAY_BUTTON.value,
        accent = Preferences.CUSTOM_LIGHT_ACCENT.value
    )

    val customThemeDark = colorPalette.copy(
        background0 = Preferences.CUSTOM_DARK_THEME_BACKGROUND_0.value,
        background1 = Preferences.CUSTOM_DARK_THEME_BACKGROUND_1.value,
        background2 = Preferences.CUSTOM_DARK_THEME_BACKGROUND_2.value,
        background3 = Preferences.CUSTOM_DARK_THEME_BACKGROUND_3.value,
        background4 = Preferences.CUSTOM_DARK_THEME_BACKGROUND_4.value,
        text = Preferences.CUSTOM_DARK_TEXT.value,
        textSecondary = Preferences.CUSTOM_DARK_TEXT_SECONDARY.value,
        textDisabled = Preferences.CUSTOM_DARK_TEXT_DISABLED.value,
        iconButtonPlayer = Preferences.CUSTOM_DARK_PLAY_BUTTON.value,
        accent = Preferences.CUSTOM_DARK_ACCENT.value
    )

    val colorPaletteMode = Preferences.THEME_MODE.value
    return when (colorPaletteMode) {
        ColorPaletteMode.Dark, ColorPaletteMode.PitchBlack -> customThemeDark
        ColorPaletteMode.Light -> customThemeLight
        ColorPaletteMode.System -> when (isSystemInDarkTheme) {
            true -> customThemeDark
            false -> customThemeLight
        }
    }
}
