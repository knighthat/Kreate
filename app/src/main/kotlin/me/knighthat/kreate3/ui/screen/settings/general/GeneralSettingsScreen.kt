/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.ui.screen.settings.general

import android.content.res.Resources
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.LogLevel
import me.knighthat.kreate3.constant.PlaylistButton
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.database.repository.UserRepository
import me.knighthat.kreate3.logging.LogActions
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.preferences.GlobalPreferences
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Profile
import me.knighthat.kreate3.preferences.ProfileManager
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.labelRes
import me.knighthat.kreate3.ui.component.labelsOf
import me.knighthat.kreate3.ui.screen.player.PICTURE_IN_PICTURE_TEXT_SIZES
import me.knighthat.kreate3.ui.screen.settings.ChoiceSetting
import me.knighthat.kreate3.ui.screen.settings.CountSetting
import me.knighthat.kreate3.ui.screen.settings.NumberSetting
import me.knighthat.kreate3.ui.screen.settings.PercentSetting
import me.knighthat.kreate3.ui.screen.settings.SecondSetting
import me.knighthat.kreate3.ui.screen.settings.SettingsItem
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import me.knighthat.kreate3.ui.screen.settings.SettingsSummary
import me.knighthat.kreate3.ui.screen.settings.SwitchSetting
import org.koin.compose.koinInject
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlow

/**
 * How the app behaves: scanning the device, leaving the app, opening it from the media
 * notification, the button of album and artist pages and the buttons of playlist pages, then
 * logging. What is kept on the device
 * is on the Data page. The logging settings are [GlobalPreferences], only the default profile
 * changes them; exporting and copying the log are open to every profile.
 */
@Composable
fun GeneralSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    profileManager: ProfileManager = koinInject(),
    userRepository: UserRepository = koinInject(),
    notifier: Notifier = koinInject(),
) {
    val scanInterval by Preferences.LOCAL_SCAN_INTERVAL.collectAsStateWithLifecycle()
    val exitOnBack by Preferences.EXIT_ON_BACK.collectAsStateWithLifecycle()
    val exitTimeout by Preferences.EXIT_CONFIRMATION_TIMEOUT.collectAsStateWithLifecycle()
    val notificationOpensPlayer by Preferences.NOTIFICATION_OPENS_PLAYER.collectAsStateWithLifecycle()
    val albumPageAction by Preferences.ALBUM_PAGE_ACTION.collectAsStateWithLifecycle()
    val artistPageAction by Preferences.ARTIST_PAGE_ACTION.collectAsStateWithLifecycle()
    val playlistFirstButton by Preferences.PLAYLIST_FIRST_BUTTON.collectAsStateWithLifecycle()
    val playlistSecondButton by Preferences.PLAYLIST_SECOND_BUTTON.collectAsStateWithLifecycle()
    val pictureInPicture by Preferences.PICTURE_IN_PICTURE.collectAsStateWithLifecycle()
    val pictureInPictureTextSize by
    Preferences.PICTURE_IN_PICTURE_LYRICS_TEXT_SIZE.collectAsStateWithLifecycle()

    val logLevel by GlobalPreferences.LOG_LEVEL.collectAsStateWithLifecycle()
    val logRolloverSize by GlobalPreferences.LOG_ROLLOVER_SIZE.collectAsStateWithLifecycle()
    val logKeep by GlobalPreferences.LOG_KEEP.collectAsStateWithLifecycle()
    val crashLogDays by GlobalPreferences.CRASH_LOG_RETENTION_DAYS.collectAsStateWithLifecycle()

    val activeId by profileManager.activeId.collectFlow()
    val defaultUser by remember(userRepository) { userRepository.find(Profile.DEFAULT_ID) }
        .collectFlow(null)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportTo = rememberLauncherForActivityResult(CreateDocument("application/zip")) { uri ->
        uri?.let { scope.launch { LogActions.export(context, it, notifier) } }
    }

    GeneralSettingsContent(
        scanInterval = scanInterval,
        exitOnBack = exitOnBack,
        exitTimeout = exitTimeout,
        notificationOpensPlayer = notificationOpensPlayer,
        albumPageAction = albumPageAction,
        artistPageAction = artistPageAction,
        playlistFirstButton = playlistFirstButton,
        playlistSecondButton = playlistSecondButton,
        pictureInPicture = pictureInPicture,
        pictureInPictureTextSize = pictureInPictureTextSize,
        logLevel = logLevel,
        logRolloverSize = logRolloverSize,
        logKeep = logKeep,
        crashLogDays = crashLogDays,
        appWide = activeId == Profile.DEFAULT_ID,
        defaultProfileName = defaultUser?.name,
        onExportLogs = { exportTo.launch(LogActions.exportName()) },
        onCopyLog = { scope.launch { LogActions.copy(context, notifier) } },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * @param logRolloverSize in bytes
 * @param crashLogDays how long crash logs are kept
 * @param appWide whether the profile may change the logging, which is [GlobalPreferences]; shown
 * read-only otherwise
 * @param defaultProfileName named in the note of what only the default profile changes, `null`
 * while it loads and the note stays hidden
 * @param onExportLogs asks where to save the log files
 * @param onCopyLog copies the latest part of the log
 */
@Composable
private fun GeneralSettingsContent(
    scanInterval: Int,
    exitOnBack: Boolean,
    exitTimeout: Int,
    notificationOpensPlayer: Boolean,
    albumPageAction: QueueAction,
    artistPageAction: QueueAction,
    playlistFirstButton: PlaylistButton,
    playlistSecondButton: PlaylistButton,
    pictureInPicture: Boolean,
    pictureInPictureTextSize: Int,
    logLevel: LogLevel,
    logRolloverSize: Long,
    logKeep: Int,
    crashLogDays: Int,
    appWide: Boolean,
    defaultProfileName: String?,
    onExportLogs: () -> Unit,
    onCopyLog: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current

    SettingsPage(stringResource(R.string.screen_title_general), onBack, modifier) {
        SettingsSection(stringResource(R.string.setting_header_library))
        NumberSetting(
            title = stringResource(R.string.setting_title_scan_interval),
            value = scanInterval.toLong(),
            min = 15,
            max = 24 * 60,
            onSave = { Preferences.LOCAL_SCAN_INTERVAL.update(it.toInt()) },
            note = {
                stringResource(
                    R.string.setting_description_scan_interval,
                    pluralStringResource(R.plurals.minute, it.toInt(), it)
                )
            },
        )

        SettingsSection(stringResource(R.string.setting_header_navigation))
        SwitchSetting(
            title = stringResource(R.string.setting_title_exit_on_back),
            checked = exitOnBack,
            onCheckedChange = { Preferences.EXIT_ON_BACK.update(it) },
            summary = stringResource(
                if (exitOnBack)
                    R.string.setting_description_exit_on_back_on
                else
                    R.string.setting_description_exit_on_back_off
            ),
        )
        if (exitOnBack) {
            val off = stringResource(R.string.status_off)
            // 0 seconds closes the app at once
            SecondSetting(
                title = stringResource(R.string.setting_title_confirm_exit),
                value = exitTimeout,
                range = 0..10,
                onSelect = { Preferences.EXIT_CONFIRMATION_TIMEOUT.update(it) },
                summary = if (exitTimeout == 0) {
                    stringResource(R.string.setting_description_confirm_exit_status_off)
                } else {
                    stringResource(
                        R.string.setting_description_confirm_exit_status_on,
                        pluralStringResource(R.plurals.second, exitTimeout, exitTimeout)
                    )
                },
                number = { if (it == 0) off else it.toString() },
            )
        }
        SwitchSetting(
            title = stringResource(R.string.setting_title_notification_opens_player),
            checked = notificationOpensPlayer,
            onCheckedChange = { Preferences.NOTIFICATION_OPENS_PLAYER.update(it) },
            summary = stringResource(R.string.setting_description_notification_opens_player),
        )

        val actionLabels = labelsOf(QueueAction.entries) { it.labelRes }
        SettingsSection(stringResource(R.string.setting_header_albums))
        ChoiceSetting(
            title = stringResource(R.string.setting_title_page_button),
            value = albumPageAction,
            options = QueueAction.entries,
            label = actionLabels,
            onSelect = { Preferences.ALBUM_PAGE_ACTION.update(it) },
        )

        SettingsSection(stringResource(R.string.setting_header_artists))
        ChoiceSetting(
            title = stringResource(R.string.setting_title_page_button),
            value = artistPageAction,
            options = QueueAction.entries,
            label = actionLabels,
            onSelect = { Preferences.ARTIST_PAGE_ACTION.update(it) },
        )

        val buttonLabels = labelsOf(PlaylistButton.entries) { it.labelRes }
        SettingsSection(stringResource(R.string.setting_header_playlists))
        // The other button's choice is left out, both can't do the same
        ChoiceSetting(
            title = stringResource(R.string.setting_title_first_button),
            value = playlistFirstButton,
            options = PlaylistButton.entries - playlistSecondButton,
            label = buttonLabels,
            onSelect = { Preferences.PLAYLIST_FIRST_BUTTON.update(it) },
        )
        ChoiceSetting(
            title = stringResource(R.string.setting_title_second_button),
            value = playlistSecondButton,
            options = PlaylistButton.entries - playlistFirstButton,
            label = buttonLabels,
            onSelect = { Preferences.PLAYLIST_SECOND_BUTTON.update(it) },
        )

        // The window needs Android 11, older versions don't get the setting
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SettingsSection(stringResource(R.string.setting_header_picture_in_picture))
            SwitchSetting(
                title = stringResource(R.string.setting_header_picture_in_picture),
                checked = pictureInPicture,
                onCheckedChange = { Preferences.PICTURE_IN_PICTURE.update(it) },
                summary = stringResource(R.string.setting_description_picture_in_picture),
            )
            PercentSetting(
                title = stringResource(R.string.setting_title_text_size),
                value = pictureInPictureTextSize,
                range = PICTURE_IN_PICTURE_TEXT_SIZES,
                onSelect = { Preferences.PICTURE_IN_PICTURE_LYRICS_TEXT_SIZE.update(it) },
                summary = stringResource(R.string.setting_description_picture_in_picture_text_size),
                enabled = pictureInPicture,
                leadingIcon = {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.format_size), null, Modifier.size(18.dp))
                    }
                },
                trailingIcon = {
                    Icon(painterResource(R.drawable.format_size), null, Modifier.size(28.dp))
                },
            )
        }

        SettingsSection(stringResource(R.string.setting_header_logging))
        if (!appWide && defaultProfileName != null)
            SettingsSummary(
                stringResource(
                    R.string.setting_description_default_profile_only,
                    stringResource(R.string.quoted, defaultProfileName)
                )
            )
        ChoiceSetting(
            title = stringResource(R.string.setting_title_log_level),
            value = logLevel,
            options = LogLevel.entries,
            label = labelsOf(LogLevel.entries) { it.labelRes },
            onSelect = { GlobalPreferences.LOG_LEVEL.update(it) },
            enabled = appWide,
        )
        ChoiceSetting(
            title = stringResource(R.string.setting_title_log_rollover_size),
            value = logRolloverSize,
            options = ROLLOVER_SIZES,
            label = { sizeLabel(resources, it) },
            onSelect = { GlobalPreferences.LOG_ROLLOVER_SIZE.update(it) },
            summary = stringResource(R.string.setting_description_log_rollover_size) +
                    "\n" + sizeLabel(resources, logRolloverSize),
            enabled = appWide,
        )
        CountSetting(
            title = stringResource(R.string.setting_title_log_keep),
            value = logKeep,
            range = 1..20,
            unit = { pluralStringResource(R.plurals.file, it, it) },
            onSelect = { GlobalPreferences.LOG_KEEP.update(it) },
            summary = stringResource(R.string.setting_description_log_keep),
            enabled = appWide,
        )
        NumberSetting(
            title = stringResource(R.string.setting_title_crash_log_retention),
            value = crashLogDays.toLong(),
            min = 1,
            max = 365,
            onSave = { GlobalPreferences.CRASH_LOG_RETENTION_DAYS.update(it.toInt()) },
            note = {
                stringResource(
                    R.string.setting_description_crash_log_retention,
                    pluralStringResource(R.plurals.day, it.toInt(), it.toInt())
                )
            },
            enabled = appWide,
        )
        SettingsItem(
            title = stringResource(R.string.setting_title_log_export),
            summary = stringResource(R.string.setting_description_log_export),
            onClick = onExportLogs,
        )
        SettingsItem(
            title = stringResource(R.string.setting_title_log_copy),
            summary = stringResource(R.string.setting_description_log_copy),
            onClick = onCopyLog,
        )
    }
}

private fun sizeLabel(resources: Resources, bytes: Long): String =
    if (bytes < MIB)
        resources.getString(R.string.kib, "${bytes / KIB}")
    else
        resources.getString(R.string.mib, "${bytes / MIB}")

private const val KIB = 1024L
private const val MIB = 1024L * KIB

/** The sizes a log file may grow to before it rolls over. */
private val ROLLOVER_SIZES = listOf(256 * KIB, 512 * KIB, MIB, 2 * MIB, 5 * MIB, 10 * MIB)

@ScreenPreviews
@Composable
private fun GeneralSettingsPreview() = PreviewTheme {
    GeneralSettingsContent(
        scanInterval = 30,
        exitOnBack = true,
        exitTimeout = 2,
        notificationOpensPlayer = true,
        albumPageAction = QueueAction.PLAY,
        artistPageAction = QueueAction.SHUFFLE,
        playlistFirstButton = PlaylistButton.PLAY,
        playlistSecondButton = PlaylistButton.SHUFFLE,
        pictureInPicture = false,
        pictureInPictureTextSize = 50,
        logLevel = LogLevel.WARNING,
        logRolloverSize = MIB,
        logKeep = 5,
        crashLogDays = 30,
        appWide = true,
        defaultProfileName = "Default",
        onExportLogs = {},
        onCopyLog = {},
        onBack = {},
    )
}
