package app.kreate.android.themed.common.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.service.player.LiveWallpaperEngine
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.animatedEntry
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import app.kreate.android.themed.common.screens.settings.player.playerActionBarSection
import app.kreate.android.themed.common.screens.settings.player.playerAppearanceSection
import coil3.compose.AsyncImage
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.isAtLeastAndroid7
import it.fast4x.rimusic.utils.isLandscape
import kotlin.math.roundToInt


@Composable
private fun SettingComponents.WallpaperRestore( title: String, internalFilename: String ) {
    val context = LocalContext.current
    val file = remember( internalFilename ) {
        context.filesDir.resolve( internalFilename )
    }
    var fileExists by remember( internalFilename ) {
        mutableStateOf(file.exists())
    }
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if( uri == null ) return@rememberLauncherForActivityResult

        context.contentResolver.openInputStream( uri )?.use { inStream ->
            file.outputStream().use( inStream::copyTo )
        }

        fileExists = true
    }

    this.Text(
        title = title,
        subtitle = stringResource(
            if( fileExists ) R.string.setting_description_remove_image else R.string.setting_description_choose_image
        ),
        onClick = {
            if( fileExists ) {
                file.delete()
                fileExists = false
            } else
                pickMedia.launch( PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly) )
        },
        trailingContent = {
            if( fileExists )
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    modifier = Modifier.height( 50.dp )
                )
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(paddingValues: PaddingValues) {
    val scrollState = rememberLazyListState()
    val isLandscapeMode = isLandscape

    val search = remember {
        SettingEntrySearch(scrollState, R.string.player_appearance, R.drawable.color_palette)
    }

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
                SettingComponents.ListEntry(
                    preference = Preferences.LIVE_WALLPAPER,
                    title = stringResource( R.string.setting_entry_live_wallpaper ),
                    subtitle = stringResource( R.string.setting_description_live_wallpaper ),
                    getName = {
                        when( it ) {
                            0 -> stringResource( R.string.word_disable )
                            1 -> stringResource( R.string.live_wallpaper_home_screen )
                            2 -> stringResource( R.string.live_wallpaper_lock_screen )
                            3 -> stringResource( R.string.live_wallpaper_both )
                            // This part shouldn't reach this point. If it does, something else is wrong
                            else -> throw IllegalStateException("Unknown live wallpaper value $it")
                        }
                    },
                    getList = {
                        val base = mutableListOf(0, 1, 2, 3)
                        if( !isAtLeastAndroid7 ) {      // Android -7 can only have on and off state
                            base.remove( 1 )
                            base.remove( 2 )
                        }
                        base.toTypedArray()
                    }
                )
            }
            animatedEntry(
                key = "liveWallpaperChildren",
                visible = Preferences.LIVE_WALLPAPER.value > 0
            ) {
                Column {
                    if( search appearsIn R.string.settings_entry_live_wallpaper_reset_duration )
                        SettingComponents.SliderEntry(
                            preference = Preferences.LIVE_WALLPAPER_RESET_DURATION,
                            title = stringResource( R.string.settings_entry_live_wallpaper_reset_duration ),
                            subtitle = stringResource( R.string.settings_description_live_wallpaper_reset_duration ),
                            // Allow integer from -1 to 60 with exceptions like empty value and '-'
                            constraints = "^(?:-1|0|[1-5]?[0-9]|60|-|)$",
                            valueRange = -1f..60000f,
                            steps = 59,
                            onValueChangeFinished = { pref, value ->
                                pref.value = value.toLong()
                            },
                            onTextDisplay = {
                                val seconds = (it / 1000).roundToInt().coerceIn(-1, 60)
                                when( seconds ) {
                                    -1      -> stringResource( R.string.word_disabled )
                                    0       -> stringResource( R.string.word_instantly )
                                    else    -> pluralStringResource( R.plurals.second, seconds, seconds )
                                }
                            }
                        )
                    if( !isAtLeastAndroid7 ) {
                        if( search appearsIn R.string.setting_entry_restore_wallpaper )
                            SettingComponents.WallpaperRestore(
                                title = stringResource( R.string.setting_entry_restore_wallpaper ),
                                internalFilename = LiveWallpaperEngine.ORIGINAL_WALLPAPER_FILENAME
                            )
                    } else {
                        if( search appearsIn R.string.setting_entry_restore_home_screen_wallpaper )
                            SettingComponents.WallpaperRestore(
                                title = stringResource( R.string.setting_entry_restore_home_screen_wallpaper ),
                                internalFilename = "${LiveWallpaperEngine.ORIGINAL_WALLPAPER_FILENAME}_1"
                            )
                        if( search appearsIn R.string.setting_entry_restore_lock_screen_wallpaper )
                            SettingComponents.WallpaperRestore(
                                title = stringResource( R.string.setting_entry_restore_lock_screen_wallpaper ),
                                internalFilename = "${LiveWallpaperEngine.ORIGINAL_WALLPAPER_FILENAME}_2"
                            )
                    }
                }
            }
        }
    }
}