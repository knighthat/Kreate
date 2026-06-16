package app.kreate.android.themed.common.screens.settings.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.components.settings.ColorPickerEntry
import app.kreate.components.settings.EnumEntry
import app.kreate.components.settings.SettingComponents
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.ColorPaletteMode
import it.fast4x.rimusic.enums.ColorPaletteName
import it.fast4x.rimusic.ui.components.themed.ConfirmationDialog

@Composable
fun ThemeSettings( search: SettingEntrySearch ) {
    val colorPaletteName by Preferences.COLOR_PALETTE.collectAsStateWithLifecycle()

    var resetCustomLightThemeDialog by rememberSaveable { mutableStateOf(false) }
    var resetCustomDarkThemeDialog by rememberSaveable { mutableStateOf(false) }

    if (resetCustomLightThemeDialog) {
        ConfirmationDialog(
            text = stringResource(R.string.do_you_really_want_to_reset_the_custom_light_theme_colors),
            onDismiss = { resetCustomLightThemeDialog = false },
            onConfirm = {
                resetCustomLightThemeDialog = false
                Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_0.reset()
                Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_1.reset()
                Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_2.reset()
                Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_3.reset()
                Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_4.reset()
                Preferences.CUSTOM_LIGHT_TEXT.reset()
                Preferences.CUSTOM_LIGHT_TEXT_SECONDARY.reset()
                Preferences.CUSTOM_LIGHT_TEXT_DISABLED.reset()
                Preferences.CUSTOM_LIGHT_PLAY_BUTTON.reset()
                Preferences.CUSTOM_LIGHT_ACCENT.reset()
            }
        )
    }

    if (resetCustomDarkThemeDialog) {
        ConfirmationDialog(
            text = stringResource(R.string.do_you_really_want_to_reset_the_custom_dark_theme_colors),
            onDismiss = { resetCustomDarkThemeDialog = false },
            onConfirm = {
                resetCustomDarkThemeDialog = false
                Preferences.CUSTOM_DARK_THEME_BACKGROUND_0.reset()
                Preferences.CUSTOM_DARK_THEME_BACKGROUND_1.reset()
                Preferences.CUSTOM_DARK_THEME_BACKGROUND_2.reset()
                Preferences.CUSTOM_DARK_THEME_BACKGROUND_3.reset()
                Preferences.CUSTOM_DARK_THEME_BACKGROUND_4.reset()
                Preferences.CUSTOM_DARK_TEXT.reset()
                Preferences.CUSTOM_DARK_TEXT_SECONDARY.reset()
                Preferences.CUSTOM_DARK_TEXT_DISABLED.reset()
                Preferences.CUSTOM_DARK_PLAY_BUTTON.reset()
                Preferences.CUSTOM_DARK_ACCENT.reset()
            }
        )
    }

    if( search appearsIn R.string.theme  )
        SettingComponents.EnumEntry(
            preference = Preferences.COLOR_PALETTE,
            title = stringResource( R.string.theme )
        ) {
            if( colorPaletteName == ColorPaletteName.ModernBlack )
                Preferences.THEME_MODE.update( ColorPaletteMode.System )
        }

    AnimatedVisibility(visible = colorPaletteName == ColorPaletteName.CustomColor) {
        SettingComponents.ColorPickerEntry(
            preference = Preferences.CUSTOM_COLOR,
            title = stringResource( R.string.customcolor ),
            subtitle = stringResource( R.string.restarting_rimusic_is_required ),
            modifier = Modifier.padding( start = 25.dp )
        )
    }
    AnimatedVisibility(visible = colorPaletteName == ColorPaletteName.Customized) {
        IndividualColorSection( search )
    }
}

@Composable
fun IndividualColorSection( search: SettingEntrySearch ) =
    Column {
        var resetCustomLightThemeDialog by rememberSaveable { mutableStateOf(false) }
        var resetCustomDarkThemeDialog by rememberSaveable { mutableStateOf(false) }

        if (resetCustomLightThemeDialog) {
            ConfirmationDialog(
                text = stringResource(R.string.do_you_really_want_to_reset_the_custom_light_theme_colors),
                onDismiss = { resetCustomLightThemeDialog = false },
                onConfirm = {
                    resetCustomLightThemeDialog = false
                    Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_0.reset()
                    Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_1.reset()
                    Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_2.reset()
                    Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_3.reset()
                    Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_4.reset()
                    Preferences.CUSTOM_LIGHT_TEXT.reset()
                    Preferences.CUSTOM_LIGHT_TEXT_SECONDARY.reset()
                    Preferences.CUSTOM_LIGHT_TEXT_DISABLED.reset()
                    Preferences.CUSTOM_LIGHT_PLAY_BUTTON.reset()
                    Preferences.CUSTOM_LIGHT_ACCENT.reset()
                }
            )
        }

        if (resetCustomDarkThemeDialog) {
            ConfirmationDialog(
                text = stringResource(R.string.do_you_really_want_to_reset_the_custom_dark_theme_colors),
                onDismiss = { resetCustomDarkThemeDialog = false },
                onConfirm = {
                    resetCustomDarkThemeDialog = false
                    Preferences.CUSTOM_DARK_THEME_BACKGROUND_0.reset()
                    Preferences.CUSTOM_DARK_THEME_BACKGROUND_1.reset()
                    Preferences.CUSTOM_DARK_THEME_BACKGROUND_2.reset()
                    Preferences.CUSTOM_DARK_THEME_BACKGROUND_3.reset()
                    Preferences.CUSTOM_DARK_THEME_BACKGROUND_4.reset()
                    Preferences.CUSTOM_DARK_TEXT.reset()
                    Preferences.CUSTOM_DARK_TEXT_SECONDARY.reset()
                    Preferences.CUSTOM_DARK_TEXT_DISABLED.reset()
                    Preferences.CUSTOM_DARK_PLAY_BUTTON.reset()
                    Preferences.CUSTOM_DARK_ACCENT.reset()
                }
            )
        }

        SettingComponents.Header(
            stringResource( R.string.title_customized_light_theme_colors )
        )

        if( search appearsIn R.string.title_reset_customized_light_colors  )
            SettingComponents.Entry(
                title = stringResource( R.string.title_reset_customized_light_colors ),
                subtitle = stringResource( R.string.info_click_to_reset_default_light_colors ),
                onClick = { resetCustomLightThemeDialog = true },
            ) {
                Icon(
                    painter = painterResource( R.drawable.trash ),
                    contentDescription = stringResource( R.string.settings_reset ),
                    tint = colorPalette().text,
                )
            }
        if( search appearsIn R.string.color_background_1  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_0,
                title = stringResource( R.string.color_background_1 )
            )
        if( search appearsIn R.string.color_background_2  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_1,
                title = stringResource( R.string.color_background_2 )
            )
        if( search appearsIn R.string.color_background_3  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_2,
                title = stringResource( R.string.color_background_3 )
            )
        if( search appearsIn R.string.color_background_4  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_3,
                title = stringResource( R.string.color_background_4 )
            )
        if( search appearsIn R.string.color_background_5  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_4,
                title = stringResource( R.string.color_background_5 )
            )
        if( search appearsIn R.string.color_text  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_LIGHT_TEXT,
                title = stringResource( R.string.color_text )
            )
        if( search appearsIn R.string.color_text_secondary  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_LIGHT_TEXT_SECONDARY,
                title = stringResource( R.string.color_text_secondary )
            )
        if( search appearsIn R.string.color_text_disabled  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_LIGHT_TEXT_DISABLED,
                title = stringResource( R.string.color_text_disabled )
            )
        if( search appearsIn R.string.color_icon_button_player  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_LIGHT_PLAY_BUTTON,
                title = stringResource( R.string.color_icon_button_player )
            )
        if( search appearsIn R.string.color_accent  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_LIGHT_ACCENT,
                title = stringResource( R.string.color_accent )
            )

        SettingComponents.Header(
            stringResource( R.string.title_customized_dark_theme_colors )
        )

        if( search appearsIn R.string.title_reset_customized_dark_colors  )
            SettingComponents.Entry(
                title = stringResource( R.string.title_reset_customized_dark_colors ),
                subtitle = stringResource( R.string.click_to_reset_default_dark_colors ),
                onClick = { resetCustomDarkThemeDialog = true },
            ) {
                Icon(
                    painter = painterResource( R.drawable.trash ),
                    contentDescription = stringResource( R.string.settings_reset ),
                    tint = colorPalette().text,
                )
            }
        if( search appearsIn R.string.color_background_1  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_DARK_THEME_BACKGROUND_0,
                title = stringResource( R.string.color_background_1 )
            )
        if( search appearsIn R.string.color_background_2  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_DARK_THEME_BACKGROUND_1,
                title = stringResource( R.string.color_background_2 )
            )
        if( search appearsIn R.string.color_background_3  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_DARK_THEME_BACKGROUND_2,
                title = stringResource( R.string.color_background_3 )
            )
        if( search appearsIn R.string.color_background_4  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_DARK_THEME_BACKGROUND_3,
                title = stringResource( R.string.color_background_4 )
            )
        if( search appearsIn R.string.color_background_5  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_DARK_THEME_BACKGROUND_4,
                title = stringResource( R.string.color_background_5 )
            )
        if( search appearsIn R.string.color_text  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_DARK_TEXT,
                title = stringResource( R.string.color_text )
            )
        if( search appearsIn R.string.color_text_secondary  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_DARK_TEXT_SECONDARY,
                title = stringResource( R.string.color_text_secondary )
            )
        if( search appearsIn R.string.color_text_disabled  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_DARK_TEXT_DISABLED,
                title = stringResource( R.string.color_text_disabled )
            )
        if( search appearsIn R.string.color_icon_button_player  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_DARK_PLAY_BUTTON,
                title = stringResource( R.string.color_icon_button_player )
            )
        if( search appearsIn R.string.color_accent  )
            SettingComponents.ColorPickerEntry(
                preference = Preferences.CUSTOM_DARK_ACCENT,
                title = stringResource( R.string.color_accent )
            )
    }

fun LazyListScope.themeSettingsSection( search: SettingEntrySearch ) {
    entry( search, R.string.theme ) {
        val colorPaletteName by Preferences.COLOR_PALETTE.collectAsStateWithLifecycle()

        SettingComponents.EnumEntry(
            preference = Preferences.COLOR_PALETTE,
            title = stringResource( R.string.theme )
        ) {
            if( colorPaletteName == ColorPaletteName.ModernBlack )
                Preferences.THEME_MODE.update( ColorPaletteMode.System )
        }
    }
    item {
        val colorPaletteName by Preferences.COLOR_PALETTE.collectAsStateWithLifecycle()

        AnimatedContent(
            targetState = colorPaletteName,
            transitionSpec = {
                expandVertically(
                    animationSpec = tween(durationMillis = 300, delayMillis = 300),
                    expandFrom = Alignment.Top
                ) togetherWith shrinkVertically(
                    animationSpec = tween(300),
                    shrinkTowards = Alignment.Top
                )
            },
            modifier = Modifier.padding( start = SettingComponents.CHILDREN_PADDING.dp )
        ) {
            when( it ) {
                ColorPaletteName.PureBlack,
                ColorPaletteName.ModernBlack -> { /* Does nothing */ }

                ColorPaletteName.Customized -> IndividualColorSection( search )

                ColorPaletteName.CustomColor ->
                    if( search appearsIn R.string.customcolor )
                        SettingComponents.ColorPickerEntry(
                            preference = Preferences.CUSTOM_COLOR,
                            title = stringResource( R.string.customcolor ),
                            subtitle = stringResource( R.string.restarting_rimusic_is_required )
                        )

                else ->
                    if( search appearsIn R.string.theme_mode )
                        SettingComponents.EnumEntry(
                            preference = Preferences.THEME_MODE,
                            title = stringResource( R.string.theme_mode )
                        )
            }
        }
    }
}