package app.kreate.android.themed.common.screens.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import app.kreate.android.themed.common.screens.settings.player.playerActionBarSection
import app.kreate.android.themed.common.screens.settings.player.playerAppearanceSection
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.WallpaperType
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.isAtLeastAndroid7
import it.fast4x.rimusic.utils.isLandscape
import me.knighthat.utils.Toaster

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(paddingValues: PaddingValues) {
    val scrollState = rememberLazyListState()
    val isLandscapeMode = isLandscape

    val search = remember {
        SettingEntrySearch(scrollState, R.string.player_appearance, R.drawable.color_palette)
    }

    val wallpaperResetAlertVisible = remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.background( colorPalette().background0 )
                           .padding( paddingValues )
                           .fillMaxHeight()
                           .fillMaxWidth(
                               if ( NavigationBarPosition.Right.isCurrent() )
                                   Dimensions.contentWidthRightBar
                               else
                                   1f
                           )
    ) {
        search.ToolBarButton()

        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer)
        ) {
            playerAppearanceSection( search, isLandscapeMode )
            playerActionBarSection( search, isLandscapeMode )

            header( R.string.notification_player )
            entry( search, R.string.notificationPlayerFirstIcon ) {
                SettingComponents.EnumEntry(
                    Preferences.MEDIA_NOTIFICATION_FIRST_ICON,
                    R.string.notificationPlayerFirstIcon,
                    action = SettingComponents.Action.RESTART_PLAYER_SERVICE
                )
            }
            entry( search, R.string.notificationPlayerSecondIcon ) {
                SettingComponents.EnumEntry(
                    Preferences.MEDIA_NOTIFICATION_SECOND_ICON,
                    R.string.notificationPlayerSecondIcon,
                    action = SettingComponents.Action.RESTART_PLAYER_SERVICE
                )
            }

            header( R.string.wallpaper )

            entry( search, R.string.setting_entry_live_wallpaper ) {
                SettingComponents.EnumEntry(
                    preference = Preferences.LIVE_WALLPAPER,
                    title = stringResource( R.string.setting_entry_live_wallpaper ),
                    subtitle = stringResource( R.string.setting_description_live_wallpaper ),
                    action = SettingComponents.Action.RESTART_PLAYER_SERVICE
                ) {
                    if( it == WallpaperType.LOCKSCREEN && !isAtLeastAndroid7 )
                        Toaster.w(
                            R.string.warning_only_available_on_android_and_above,
                            "7",
                            Build.VERSION_CODES.N,
                            Toast.LENGTH_SHORT
                        )
                }
            }
            entry(search, R.string.settings_entry_live_wallpaper_reset_duration) {
                SettingComponents.SliderEntry(
                    preference = Preferences.LIVE_WALLPAPER_RESET_DURATION,
                    title = stringResource(R.string.settings_entry_live_wallpaper_reset_duration),
                    subtitle = stringResource(R.string.settings_description_live_wallpaper_reset_duration),
                    constraints = "",
                    valueRange = -1f..60000f,
                    steps = 61,
                    onValueChangeFinished = { pref, value ->
                        if (pref.value == -1L && value > -1L) {
                            wallpaperResetAlertVisible.value = true
                        }
                        pref.value = value.toLong() },
                    onTextDisplay = {
                        when (it) {
                            -1f -> stringResource(R.string.disabled)
                            else -> (it / 1000).toString()
                        }
                    }
                )
            }
        }
    }
    if (wallpaperResetAlertVisible.value){
        BasicAlertDialog(
            onDismissRequest = {wallpaperResetAlertVisible.value = false},
        ) {
            Surface(
                modifier = Modifier.wrapContentWidth().wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_live_wallpaper_reset_warning))
                    TextButton(onClick = { wallpaperResetAlertVisible.value = false }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}