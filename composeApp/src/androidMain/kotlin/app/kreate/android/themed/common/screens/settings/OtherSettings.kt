package app.kreate.android.themed.common.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.animatedEntry
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import app.kreate.android.themed.common.screens.settings.other.debugSection
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.ui.screens.settings.StringListValueSelectorSettingsEntry
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.isAtLeastAndroid10
import it.fast4x.rimusic.utils.isIgnoringBatteryOptimizations
import me.knighthat.component.dialog.InputDialogConstraints
import me.knighthat.utils.Toaster
import java.io.File

@Composable
fun OtherSettings( paddingValues: PaddingValues ) {
    val context = LocalContext.current
    val scrollState = rememberLazyListState()

    val search = remember {
        SettingEntrySearch( scrollState, R.string.tab_miscellaneous, R.drawable.equalizer )
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
            header( R.string.on_device )
            entry( search, R.string.blacklisted_folders ) {
                var blackListedPaths by remember {
                    val file = File(context.filesDir, "Blacklisted_paths.txt")
                    if (file.exists()) {
                        mutableStateOf(file.readLines())
                    } else {
                        mutableStateOf(emptyList())
                    }
                }

                StringListValueSelectorSettingsEntry(
                    title = stringResource(R.string.blacklisted_folders),
                    text = stringResource(R.string.edit_blacklist_for_on_device_songs),
                    addTitle = stringResource(R.string.add_folder),
                    addPlaceholder = if (isAtLeastAndroid10) {
                        "Android/media/com.whatsapp/WhatsApp/Media"
                    } else {
                        "/storage/emulated/0/Android/media/com.whatsapp/"
                    },
                    conflictTitle = stringResource(R.string.this_folder_already_exists),
                    removeTitle = stringResource(R.string.are_you_sure_you_want_to_remove_this_folder_from_the_blacklist),
                    context = LocalContext.current,
                    list = blackListedPaths,
                    add = { newPath ->
                        blackListedPaths = blackListedPaths + newPath
                        val file = File(context.filesDir, "Blacklisted_paths.txt")
                        file.writeText(blackListedPaths.joinToString("\n"))
                    },
                    remove = { path ->
                        blackListedPaths = blackListedPaths.filter { it != path }
                        val file = File(context.filesDir, "Blacklisted_paths.txt")
                        file.writeText(blackListedPaths.joinToString("\n"))
                    }
                )
            }
            entry( search, R.string.folders ) {
                SettingComponents.BooleanEntry(
                    Preferences.HOME_SONGS_ON_DEVICE_SHOW_FOLDERS,
                    R.string.folders,
                    R.string.show_folders_in_on_device_page
                )
            }
            animatedEntry(
                key = "showFolderChildren",
                visible = Preferences.HOME_SONGS_ON_DEVICE_SHOW_FOLDERS.value,
                modifier = Modifier.padding( start = 25.dp )
            ) {
                if( search appearsIn R.string.folder_that_will_show_when_you_open_on_device_page )
                    SettingComponents.InputDialogEntry(
                        Preferences.LOCAL_SONGS_FOLDER,
                        R.string.folder_that_will_show_when_you_open_on_device_page,
                        InputDialogConstraints.ANDROID_FILE_PATH
                    )
            }

            header( R.string.androidheadunit )
            entry( search, R.string.extra_space ) {
                SettingComponents.BooleanEntry(
                    Preferences.PLAYER_EXTRA_SPACE,
                    R.string.extra_space
                )
            }

            header(
                titleId = R.string.service_lifetime,
                subtitle = { stringResource( R.string.battery_optimizations_applied ) }
            )
            entry( search, R.string.keep_screen_on ) {
                SettingComponents.BooleanEntry(
                    Preferences.KEEP_SCREEN_ON,
                    R.string.keep_screen_on,
                    R.string.prevents_screen_timeout
                )
            }
            entry(
                search = search,
                titleId = R.string.ignore_battery_optimizations
            ) {
                var isIgnoringBatteryOptimizations by remember {
                    mutableStateOf( context.isIgnoringBatteryOptimizations )
                }
                val activityResultLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) {
                        isIgnoringBatteryOptimizations = context.isIgnoringBatteryOptimizations
                    }
                val subtitle by remember { derivedStateOf {
                    if (isIgnoringBatteryOptimizations)
                        context.getString( R.string.already_unrestricted )
                    else
                        context.getString( R.string.disable_background_restrictions )
                }}

                SettingComponents.Text(
                    title = stringResource( R.string.ignore_battery_optimizations ),
                    subtitle = subtitle,
                    onClick = {
                        try {
                            activityResultLauncher.launch(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = "package:${context.packageName}".toUri()
                                }
                            )
                        } catch (e: ActivityNotFoundException) {
                            try {
                                activityResultLauncher.launch(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                )
                            } catch (e: ActivityNotFoundException) {
                                Toaster.i( R.string.not_find_battery_optimization_settings )
                            }
                        }
                    }
                )

                // TODO: ADD this to comment of "ignore optimization"
                // SettingComponents.Description( R.string.is_android12 )
            }

            header( R.string.parental_control )
            entry( search, R.string.parental_control ) {
                SettingComponents.BooleanEntry(
                    Preferences.PARENTAL_CONTROL,
                    R.string.parental_control,
                    R.string.info_prevent_play_songs_with_age_limitation
                )
            }

            debugSection( search )
        }
    }
}