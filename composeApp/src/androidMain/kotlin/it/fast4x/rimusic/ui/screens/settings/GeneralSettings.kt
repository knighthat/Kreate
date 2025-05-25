package it.fast4x.rimusic.ui.screens.settings

import android.os.Build
import android.text.TextUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.R
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.AudioQualityFormat
import it.fast4x.rimusic.enums.DurationInMilliseconds
import it.fast4x.rimusic.enums.DurationInMinutes
import it.fast4x.rimusic.enums.ExoPlayerMinTimeForEvent
import it.fast4x.rimusic.enums.Languages
import it.fast4x.rimusic.enums.MaxSongs
import it.fast4x.rimusic.enums.MusicAnimationType
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.NavigationBarType
import it.fast4x.rimusic.enums.NotificationType
import it.fast4x.rimusic.enums.PauseBetweenSongs
import it.fast4x.rimusic.enums.PipModule
import it.fast4x.rimusic.enums.PresetsReverb
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import it.fast4x.rimusic.ui.styling.DefaultLightColorPalette
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.RestartActivity
import it.fast4x.rimusic.utils.RestartPlayerService
import it.fast4x.rimusic.utils.audioQualityFormatKey
import it.fast4x.rimusic.utils.audioReverbPresetKey
import it.fast4x.rimusic.utils.autoDownloadSongKey
import it.fast4x.rimusic.utils.autoDownloadSongWhenAlbumBookmarkedKey
import it.fast4x.rimusic.utils.autoDownloadSongWhenLikedKey
import it.fast4x.rimusic.utils.autoLoadSongsInQueueKey
import it.fast4x.rimusic.utils.bassboostEnabledKey
import it.fast4x.rimusic.utils.bassboostLevelKey
import it.fast4x.rimusic.utils.closeWithBackButtonKey
import it.fast4x.rimusic.utils.closebackgroundPlayerKey
import it.fast4x.rimusic.utils.customThemeDark_Background0Key
import it.fast4x.rimusic.utils.customThemeDark_Background1Key
import it.fast4x.rimusic.utils.customThemeDark_Background2Key
import it.fast4x.rimusic.utils.customThemeDark_Background3Key
import it.fast4x.rimusic.utils.customThemeDark_Background4Key
import it.fast4x.rimusic.utils.customThemeDark_TextKey
import it.fast4x.rimusic.utils.customThemeDark_accentKey
import it.fast4x.rimusic.utils.customThemeDark_iconButtonPlayerKey
import it.fast4x.rimusic.utils.customThemeDark_textDisabledKey
import it.fast4x.rimusic.utils.customThemeDark_textSecondaryKey
import it.fast4x.rimusic.utils.customThemeLight_Background0Key
import it.fast4x.rimusic.utils.customThemeLight_Background1Key
import it.fast4x.rimusic.utils.customThemeLight_Background2Key
import it.fast4x.rimusic.utils.customThemeLight_Background3Key
import it.fast4x.rimusic.utils.customThemeLight_Background4Key
import it.fast4x.rimusic.utils.customThemeLight_TextKey
import it.fast4x.rimusic.utils.customThemeLight_accentKey
import it.fast4x.rimusic.utils.customThemeLight_iconButtonPlayerKey
import it.fast4x.rimusic.utils.customThemeLight_textDisabledKey
import it.fast4x.rimusic.utils.customThemeLight_textSecondaryKey
import it.fast4x.rimusic.utils.disableClosingPlayerSwipingDownKey
import it.fast4x.rimusic.utils.discoverKey
import it.fast4x.rimusic.utils.enablePictureInPictureAutoKey
import it.fast4x.rimusic.utils.enablePictureInPictureKey
import it.fast4x.rimusic.utils.excludeSongsWithDurationLimitKey
import it.fast4x.rimusic.utils.exoPlayerMinTimeForEventKey
import it.fast4x.rimusic.utils.handleAudioFocusEnabledKey
import it.fast4x.rimusic.utils.isAtLeastAndroid12
import it.fast4x.rimusic.utils.isAtLeastAndroid6
import it.fast4x.rimusic.utils.isConnectionMeteredEnabledKey
import it.fast4x.rimusic.utils.isPauseOnVolumeZeroEnabledKey
import it.fast4x.rimusic.utils.jumpPreviousKey
import it.fast4x.rimusic.utils.keepPlayerMinimizedKey
import it.fast4x.rimusic.utils.languageAppKey
import it.fast4x.rimusic.utils.languageDestinationName
import it.fast4x.rimusic.utils.loudnessBaseGainKey
import it.fast4x.rimusic.utils.maxSongsInQueueKey
import it.fast4x.rimusic.utils.minimumSilenceDurationKey
import it.fast4x.rimusic.utils.navigationBarPositionKey
import it.fast4x.rimusic.utils.navigationBarTypeKey
import it.fast4x.rimusic.utils.notificationTypeKey
import it.fast4x.rimusic.utils.nowPlayingIndicatorKey
import it.fast4x.rimusic.utils.pauseBetweenSongsKey
import it.fast4x.rimusic.utils.pauseListenHistoryKey
import it.fast4x.rimusic.utils.persistentQueueKey
import it.fast4x.rimusic.utils.pipModuleKey
import it.fast4x.rimusic.utils.playbackFadeAudioDurationKey
import it.fast4x.rimusic.utils.playlistindicatorKey
import it.fast4x.rimusic.utils.rememberEqualizerLauncher
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.resumePlaybackOnStartKey
import it.fast4x.rimusic.utils.resumePlaybackWhenDeviceConnectedKey
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.shakeEventEnabledKey
import it.fast4x.rimusic.utils.skipMediaOnErrorKey
import it.fast4x.rimusic.utils.skipSilenceKey
import it.fast4x.rimusic.utils.useVolumeKeysToChangeSongKey
import it.fast4x.rimusic.utils.volumeNormalizationKey
import me.knighthat.component.dialog.RestartAppDialog
import me.knighthat.component.tab.Search
import me.knighthat.updater.Updater


@ExperimentalAnimationApi
@UnstableApi
@Composable
fun GeneralSettings(
    navController: NavController
) {
    val binder = LocalPlayerServiceBinder.current

    var languageApp  by rememberPreference(languageAppKey, Languages.System)
    val systemLocale = LocaleListCompat.getDefault().get(0).toString()

    var exoPlayerMinTimeForEvent by rememberPreference(
        exoPlayerMinTimeForEventKey,
        ExoPlayerMinTimeForEvent.`20s`
    )
    var persistentQueue by rememberPreference(persistentQueueKey, false)
    var resumePlaybackOnStart by rememberPreference(resumePlaybackOnStartKey, false)
    var closebackgroundPlayer by rememberPreference(closebackgroundPlayerKey, false)
    var closeWithBackButton by rememberPreference(closeWithBackButtonKey, true)
    var resumePlaybackWhenDeviceConnected by rememberPreference(
        resumePlaybackWhenDeviceConnectedKey,
        false
    )

    var skipSilence by rememberPreference(skipSilenceKey, false)
    var skipMediaOnError by rememberPreference(skipMediaOnErrorKey, false)
    var volumeNormalization by rememberPreference(volumeNormalizationKey, false)
    var audioQualityFormat by rememberPreference(audioQualityFormatKey, AudioQualityFormat.Auto)
    var isConnectionMeteredEnabled by rememberPreference(isConnectionMeteredEnabledKey, true)


    var keepPlayerMinimized by rememberPreference(keepPlayerMinimizedKey,   false)

    var disableClosingPlayerSwipingDown by rememberPreference(disableClosingPlayerSwipingDownKey, false)

    var navigationBarPosition by rememberPreference(navigationBarPositionKey, NavigationBarPosition.Bottom)
    var navigationBarType by rememberPreference(navigationBarTypeKey, NavigationBarType.IconAndText)
    var pauseBetweenSongs  by rememberPreference(pauseBetweenSongsKey, PauseBetweenSongs.`0`)
    var maxSongsInQueue  by rememberPreference(maxSongsInQueueKey, MaxSongs.`500`)

    val search = Search()

    var shakeEventEnabled by rememberPreference(shakeEventEnabledKey, false)
    var useVolumeKeysToChangeSong by rememberPreference(useVolumeKeysToChangeSongKey, false)

    var customThemeLight_Background0 by rememberPreference(customThemeLight_Background0Key, DefaultLightColorPalette.background0.hashCode())
    var customThemeLight_Background1 by rememberPreference(customThemeLight_Background1Key, DefaultLightColorPalette.background1.hashCode())
    var customThemeLight_Background2 by rememberPreference(customThemeLight_Background2Key, DefaultLightColorPalette.background2.hashCode())
    var customThemeLight_Background3 by rememberPreference(customThemeLight_Background3Key, DefaultLightColorPalette.background3.hashCode())
    var customThemeLight_Background4 by rememberPreference(customThemeLight_Background4Key, DefaultLightColorPalette.background4.hashCode())
    var customThemeLight_Text by rememberPreference(customThemeLight_TextKey, DefaultLightColorPalette.text.hashCode())
    var customThemeLight_TextSecondary by rememberPreference(customThemeLight_textSecondaryKey, DefaultLightColorPalette.textSecondary.hashCode())
    var customThemeLight_TextDisabled by rememberPreference(customThemeLight_textDisabledKey, DefaultLightColorPalette.textDisabled.hashCode())
    var customThemeLight_IconButtonPlayer by rememberPreference(customThemeLight_iconButtonPlayerKey, DefaultLightColorPalette.iconButtonPlayer.hashCode())
    var customThemeLight_Accent by rememberPreference(customThemeLight_accentKey, DefaultLightColorPalette.accent.hashCode())

    var customThemeDark_Background0 by rememberPreference(customThemeDark_Background0Key, DefaultDarkColorPalette.background0.hashCode())
    var customThemeDark_Background1 by rememberPreference(customThemeDark_Background1Key, DefaultDarkColorPalette.background1.hashCode())
    var customThemeDark_Background2 by rememberPreference(customThemeDark_Background2Key, DefaultDarkColorPalette.background2.hashCode())
    var customThemeDark_Background3 by rememberPreference(customThemeDark_Background3Key, DefaultDarkColorPalette.background3.hashCode())
    var customThemeDark_Background4 by rememberPreference(customThemeDark_Background4Key, DefaultDarkColorPalette.background4.hashCode())
    var customThemeDark_Text by rememberPreference(customThemeDark_TextKey, DefaultDarkColorPalette.text.hashCode())
    var customThemeDark_TextSecondary by rememberPreference(customThemeDark_textSecondaryKey, DefaultDarkColorPalette.textSecondary.hashCode())
    var customThemeDark_TextDisabled by rememberPreference(customThemeDark_textDisabledKey, DefaultDarkColorPalette.textDisabled.hashCode())
    var customThemeDark_IconButtonPlayer by rememberPreference(customThemeDark_iconButtonPlayerKey, DefaultDarkColorPalette.iconButtonPlayer.hashCode())
    var customThemeDark_Accent by rememberPreference(customThemeDark_accentKey, DefaultDarkColorPalette.accent.hashCode())

    var resetCustomLightThemeDialog by rememberSaveable { mutableStateOf(false) }
    var resetCustomDarkThemeDialog by rememberSaveable { mutableStateOf(false) }
    var playbackFadeAudioDuration by rememberPreference(playbackFadeAudioDurationKey, DurationInMilliseconds.Disabled)
    var excludeSongWithDurationLimit by rememberPreference(excludeSongsWithDurationLimitKey, DurationInMinutes.Disabled)
    var playlistindicator by rememberPreference(playlistindicatorKey, false)
    var nowPlayingIndicator by rememberPreference(nowPlayingIndicatorKey, MusicAnimationType.Bubbles)
    var discoverIsEnabled by rememberPreference(discoverKey, false)
    var isPauseOnVolumeZeroEnabled by rememberPreference(isPauseOnVolumeZeroEnabledKey, false)


    val launchEqualizer by rememberEqualizerLauncher(audioSessionId = { binder?.player?.audioSessionId })

    var minimumSilenceDuration by rememberPreference(minimumSilenceDurationKey, 2_000_000L)

    var pauseListenHistory by rememberPreference(pauseListenHistoryKey, false)
    var restartService by rememberSaveable { mutableStateOf(false) }
    var restartActivity by rememberSaveable { mutableStateOf(false) }

    var loudnessBaseGain by rememberPreference(loudnessBaseGainKey, 5.00f)
    var autoLoadSongsInQueue by rememberPreference(autoLoadSongsInQueueKey, true)

    var bassboostEnabled by rememberPreference(bassboostEnabledKey,false)
    var bassboostLevel by rememberPreference(bassboostLevelKey, 0.5f)
    var audioReverb by rememberPreference(audioReverbPresetKey,   PresetsReverb.NONE)
    var audioFocusEnabled by rememberPreference(handleAudioFocusEnabledKey, true)

    var enablePictureInPicture by rememberPreference(enablePictureInPictureKey, false)
    var enablePictureInPictureAuto by rememberPreference(enablePictureInPictureAutoKey, false)
    var pipModule by rememberPreference(pipModuleKey, PipModule.Cover)
    var jumpPrevious by rememberPreference(jumpPreviousKey,"3")
    var notificationType by rememberPreference(notificationTypeKey, NotificationType.Default)
    var autoDownloadSong by rememberPreference(autoDownloadSongKey, false)
    var autoDownloadSongWhenLiked by rememberPreference(autoDownloadSongWhenLikedKey, false)
    var autoDownloadSongWhenAlbumBookmarked by rememberPreference(autoDownloadSongWhenAlbumBookmarkedKey, false)



    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
            .fillMaxHeight()
            .fillMaxWidth(
                if (navigationBarPosition == NavigationBarPosition.Left ||
                    navigationBarPosition == NavigationBarPosition.Top ||
                    navigationBarPosition == NavigationBarPosition.Bottom
                ) 1f
                else Dimensions.contentWidthRightBar
            )
            .verticalScroll(rememberScrollState())
            /*
            .padding(
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                    .asPaddingValues()
            )
             */
    ) {
        HeaderWithIcon(
            title = stringResource(R.string.tab_general),
            iconId = R.drawable.ic_launcher_monochrome,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {}
        )

        search.ToolBarButton()
        search.SearchBar( this )

        if (resetCustomLightThemeDialog) {
            ConfirmationDialog(
                text = stringResource(R.string.do_you_really_want_to_reset_the_custom_light_theme_colors),
                onDismiss = { resetCustomLightThemeDialog = false },
                onConfirm = {
                    resetCustomLightThemeDialog = false
                    customThemeLight_Background0 = DefaultLightColorPalette.background0.hashCode()
                    customThemeLight_Background1 = DefaultLightColorPalette.background1.hashCode()
                    customThemeLight_Background2 = DefaultLightColorPalette.background2.hashCode()
                    customThemeLight_Background3 = DefaultLightColorPalette.background3.hashCode()
                    customThemeLight_Background4 = DefaultLightColorPalette.background4.hashCode()
                    customThemeLight_Text = DefaultLightColorPalette.text.hashCode()
                    customThemeLight_TextSecondary = DefaultLightColorPalette.textSecondary.hashCode()
                    customThemeLight_TextDisabled = DefaultLightColorPalette.textDisabled.hashCode()
                    customThemeLight_IconButtonPlayer = DefaultLightColorPalette.iconButtonPlayer.hashCode()
                    customThemeLight_Accent = DefaultLightColorPalette.accent.hashCode()
                }
            )
        }

        if (resetCustomDarkThemeDialog) {
            ConfirmationDialog(
                text = stringResource(R.string.do_you_really_want_to_reset_the_custom_dark_theme_colors),
                onDismiss = { resetCustomDarkThemeDialog = false },
                onConfirm = {
                    resetCustomDarkThemeDialog = false
                    customThemeDark_Background0 = DefaultDarkColorPalette.background0.hashCode()
                    customThemeDark_Background1 = DefaultDarkColorPalette.background1.hashCode()
                    customThemeDark_Background2 = DefaultDarkColorPalette.background2.hashCode()
                    customThemeDark_Background3 = DefaultDarkColorPalette.background3.hashCode()
                    customThemeDark_Background4 = DefaultDarkColorPalette.background4.hashCode()
                    customThemeDark_Text = DefaultDarkColorPalette.text.hashCode()
                    customThemeDark_TextSecondary = DefaultDarkColorPalette.textSecondary.hashCode()
                    customThemeDark_TextDisabled = DefaultDarkColorPalette.textDisabled.hashCode()
                    customThemeDark_IconButtonPlayer = DefaultDarkColorPalette.iconButtonPlayer.hashCode()
                    customThemeDark_Accent = DefaultDarkColorPalette.accent.hashCode()
                }
            )
        }

        SettingsEntryGroupText( stringResource(R.string.update) )
        if( search.inputValue.isBlank() || stringResource(R.string.update).contains( search.inputValue, true ) )
            Updater.SettingEntry()

        SettingsGroupSpacer()
        SettingsEntryGroupText(title = stringResource(R.string.languages))

        SettingsDescription(text = stringResource(R.string.system_language)+": $systemLocale")

        if (search.inputValue.isBlank() || stringResource(R.string.app_language).contains(search.inputValue,true))
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.app_language),
                selectedValue = languageApp,
                onValueSelected = {
                    languageApp = it

                    RestartAppDialog.showDialog()
                },
                valueText = {
                    languageDestinationName(it)
                }
            )



        SettingsGroupSpacer()
        SettingsEntryGroupText(stringResource(R.string.player))

        if (search.inputValue.isBlank() || stringResource(R.string.notification_type).contains(search.inputValue,true)) {
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.notification_type),
                selectedValue = notificationType,
                onValueSelected = {
                    notificationType = it
                },
                valueText = {
                    it.textName
                }
            )
            SettingsDescription(text = stringResource(R.string.notification_type_info))
            ImportantSettingsDescription(text = stringResource(R.string.restarting_rimusic_is_required))
        }

        if (search.inputValue.isBlank() || stringResource(R.string.audio_quality_format).contains(search.inputValue,true)) {
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.audio_quality_format),
                selectedValue = audioQualityFormat,
                onValueSelected = {
                    audioQualityFormat = it
                    restartService = true
                },
                valueText = {
                    when (it) {
                        AudioQualityFormat.Auto -> stringResource(R.string.audio_quality_automatic)
                        AudioQualityFormat.High -> stringResource(R.string.audio_quality_format_high)
                        AudioQualityFormat.Medium -> stringResource(R.string.audio_quality_format_medium)
                        AudioQualityFormat.Low -> stringResource(R.string.audio_quality_format_low)
                    }
                }
            )

            RestartPlayerService(restartService, onRestart = { restartService = false } )

        }

        if (search.inputValue.isBlank() || stringResource(R.string.enable_connection_metered).contains(search.inputValue,true))
            SwitchSettingEntry(
                title = stringResource(R.string.enable_connection_metered),
                text = stringResource(R.string.info_enable_connection_metered),
                isChecked = isConnectionMeteredEnabled,
                onCheckedChange = {
                    isConnectionMeteredEnabled = it
                    if (it)
                        audioQualityFormat = AudioQualityFormat.Auto
                }
            )

        if (search.inputValue.isBlank() || stringResource(R.string.jump_previous).contains(search.inputValue,true)) {
            BasicText(
                text = stringResource(R.string.jump_previous),
                style = typography().xs.semiBold.copy(color = colorPalette().text),
                modifier = Modifier
                    .padding(start = 12.dp)
                    //.padding(all = 12.dp)
            )
            BasicText(
                text = stringResource(R.string.jump_previous_blank),
                style = typography().xxs.semiBold.copy(color = colorPalette().textDisabled),
                modifier = Modifier
                    .padding(start = 12.dp)
            )
            TextField(
                value = jumpPrevious,
                onValueChange = {
                    if (TextUtils.isDigitsOnly(it))
                    jumpPrevious = it
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    unfocusedTextColor = colorPalette().text,
                    focusedTextColor = colorPalette().text,
                    focusedIndicatorColor = colorPalette().text,
                    unfocusedIndicatorColor = colorPalette().text
                ),
                modifier = Modifier
                    .padding(start = 12.dp)
                    //.padding(all = 12.dp)
            )
        }

        if (search.inputValue.isBlank() || stringResource(R.string.min_listening_time).contains(search.inputValue,true)) {
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.min_listening_time),
                selectedValue = exoPlayerMinTimeForEvent,
                onValueSelected = { exoPlayerMinTimeForEvent = it },
                valueText = {
                    when (it) {
                        ExoPlayerMinTimeForEvent.`10s` -> "10s"
                        ExoPlayerMinTimeForEvent.`15s` -> "15s"
                        ExoPlayerMinTimeForEvent.`20s` -> "20s"
                        ExoPlayerMinTimeForEvent.`30s` -> "30s"
                        ExoPlayerMinTimeForEvent.`40s` -> "40s"
                        ExoPlayerMinTimeForEvent.`60s` -> "60s"
                    }
                }
            )
            SettingsDescription(text = stringResource(R.string.is_min_list_time_for_tips_or_quick_pics))
        }

        if (search.inputValue.isBlank() || stringResource(R.string.min_listening_time).contains(search.inputValue,true)) {
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.exclude_songs_with_duration_limit),
                selectedValue = excludeSongWithDurationLimit,
                onValueSelected = { excludeSongWithDurationLimit = it },
                valueText = {
                    when (it) {
                        DurationInMinutes.Disabled -> stringResource(R.string.vt_disabled)
                        DurationInMinutes.`3` -> "3m"
                        DurationInMinutes.`5` -> "5m"
                        DurationInMinutes.`10` -> "10m"
                        DurationInMinutes.`15` -> "15m"
                        DurationInMinutes.`20` -> "20m"
                        DurationInMinutes.`25` -> "25m"
                        DurationInMinutes.`30` -> "30m"
                        DurationInMinutes.`60` -> "60m"
                    }
                }
            )
            SettingsDescription(text = stringResource(R.string.exclude_songs_with_duration_limit_description))
        }

        if (search.inputValue.isBlank() || stringResource(R.string.pause_between_songs).contains(search.inputValue,true))
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.pause_between_songs),
                selectedValue = pauseBetweenSongs,
                onValueSelected = { pauseBetweenSongs = it },
                valueText = {
                    when (it) {
                        PauseBetweenSongs.`0` -> "0s"
                        PauseBetweenSongs.`5` -> "5s"
                        PauseBetweenSongs.`10` -> "10s"
                        PauseBetweenSongs.`15` -> "15s"
                        PauseBetweenSongs.`20` -> "20s"
                        PauseBetweenSongs.`30` -> "30s"
                        PauseBetweenSongs.`40` -> "40s"
                        PauseBetweenSongs.`50` -> "50s"
                        PauseBetweenSongs.`60` -> "60s"
                    }
                }
            )

        if (search.inputValue.isBlank() || stringResource(R.string.player_pause_listen_history).contains(search.inputValue,true)) {
            SwitchSettingEntry(
                title = stringResource(R.string.player_pause_listen_history),
                text = stringResource(R.string.player_pause_listen_history_info),
                isChecked = pauseListenHistory,
                onCheckedChange = {
                    pauseListenHistory = it
                    restartService = true
                }
            )
            RestartPlayerService(restartService, onRestart = { restartService = false } )
        }

        if (search.inputValue.isBlank() || stringResource(R.string.player_pause_on_volume_zero).contains(search.inputValue,true))
            SwitchSettingEntry(
                title = stringResource(R.string.player_pause_on_volume_zero),
                text = stringResource(R.string.info_pauses_player_when_volume_zero),
                isChecked = isPauseOnVolumeZeroEnabled,
                onCheckedChange = {
                    isPauseOnVolumeZeroEnabled = it
                }
            )

        if (search.inputValue.isBlank() || stringResource(R.string.effect_fade_audio).contains(search.inputValue,true)) {
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.effect_fade_audio),
                selectedValue = playbackFadeAudioDuration,
                onValueSelected = { playbackFadeAudioDuration = it },
                valueText = {
                    when (it) {
                        DurationInMilliseconds.Disabled -> stringResource(R.string.vt_disabled)
                        else -> {
                            it.toString()
                        }
                    }
                }
            )
            SettingsDescription(text = stringResource(R.string.effect_fade_audio_description))
        }

        /*
        if (filter.isNullOrBlank() || stringResource(R.string.effect_fade_songs).contains(filterCharSequence,true))
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.effect_fade_songs),
                selectedValue = playbackFadeDuration,
                onValueSelected = { playbackFadeDuration = it },
                valueText = {
                    when (it) {
                        DurationInSeconds.Disabled -> stringResource(R.string.vt_disabled)
                        DurationInSeconds.`3` -> "3 %s".format(stringResource(R.string.time_seconds))
                        DurationInSeconds.`4` -> "4 %s".format(stringResource(R.string.time_seconds))
                        DurationInSeconds.`5` -> "5 %s".format(stringResource(R.string.time_seconds))
                        DurationInSeconds.`6` -> "6 %s".format(stringResource(R.string.time_seconds))
                        DurationInSeconds.`7` -> "7 %s".format(stringResource(R.string.time_seconds))
                        DurationInSeconds.`8` -> "8 %s".format(stringResource(R.string.time_seconds))
                        DurationInSeconds.`9` -> "9 %s".format(stringResource(R.string.time_seconds))
                        DurationInSeconds.`10` -> "10 %s".format(stringResource(R.string.time_seconds))
                        DurationInSeconds.`11` -> "11 %s".format(stringResource(R.string.time_seconds))
                        DurationInSeconds.`12` -> "12 %s".format(stringResource(R.string.time_seconds))
                    }
                }
            )
         */



            if (search.inputValue.isBlank() || stringResource(R.string.player_keep_minimized).contains(search.inputValue,true))
                SwitchSettingEntry(
                    title = stringResource(R.string.player_keep_minimized),
                    text = stringResource(R.string.when_click_on_a_song_player_start_minimized),
                    isChecked = keepPlayerMinimized,
                    onCheckedChange = {
                        keepPlayerMinimized = it
                    }
                )


        if (search.inputValue.isBlank() || stringResource(R.string.player_collapsed_disable_swiping_down).contains(search.inputValue,true))
            SwitchSettingEntry(
                title = stringResource(R.string.player_collapsed_disable_swiping_down),
                text = stringResource(R.string.avoid_closing_the_player_cleaning_queue_by_swiping_down),
                isChecked = disableClosingPlayerSwipingDown,
                onCheckedChange = {
                    disableClosingPlayerSwipingDown = it
                }
            )

        if (search.inputValue.isBlank() || stringResource(R.string.player_auto_load_songs_in_queue).contains(search.inputValue,true)) {
            SwitchSettingEntry(
                title = stringResource(R.string.player_auto_load_songs_in_queue),
                text = stringResource(R.string.player_auto_load_songs_in_queue_description),
                isChecked = autoLoadSongsInQueue,
                onCheckedChange = {
                    autoLoadSongsInQueue = it
                    restartService = true
                }
            )
            RestartPlayerService(restartService, onRestart = { restartService = false })
        }

        if (search.inputValue.isBlank() || stringResource(R.string.max_songs_in_queue).contains(search.inputValue,true))
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.max_songs_in_queue),
                selectedValue = maxSongsInQueue,
                onValueSelected = { maxSongsInQueue = it },
                valueText = {
                    when (it) {
                        MaxSongs.Unlimited -> stringResource(R.string.unlimited)
                        MaxSongs.`50` -> MaxSongs.`50`.name
                        MaxSongs.`100` -> MaxSongs.`100`.name
                        MaxSongs.`200` -> MaxSongs.`200`.name
                        MaxSongs.`300` -> MaxSongs.`300`.name
                        MaxSongs.`500` -> MaxSongs.`500`.name
                        MaxSongs.`1000` -> MaxSongs.`1000`.name
                        MaxSongs.`2000` -> MaxSongs.`2000`.name
                        MaxSongs.`3000` -> MaxSongs.`3000`.name
                    }
                }
            )

        if (search.inputValue.isBlank() || stringResource(R.string.discover).contains(
                search.inputValue,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.discover),
                text = stringResource(R.string.discoverinfo),
                isChecked = discoverIsEnabled,
                onCheckedChange = { discoverIsEnabled = it }
            )

        if (search.inputValue.isBlank() || stringResource(R.string.playlistindicator).contains(search.inputValue,true))
            SwitchSettingEntry(
                title = stringResource(R.string.playlistindicator),
                text = stringResource(R.string.playlistindicatorinfo),
                isChecked = playlistindicator,
                onCheckedChange = {
                    playlistindicator = it
                }
            )

        if (search.inputValue.isBlank() || stringResource(R.string.now_playing_indicator).contains(search.inputValue,true))
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.now_playing_indicator),
                selectedValue = nowPlayingIndicator,
                onValueSelected = { nowPlayingIndicator = it },
                valueText = { it.text }
            )

        if (search.inputValue.isBlank() || stringResource(R.string.resume_playback).contains(search.inputValue,true)) {
            if (isAtLeastAndroid6) {
                SwitchSettingEntry(
                    title = stringResource(R.string.resume_playback),
                    text = stringResource(R.string.when_device_is_connected),
                    isChecked = resumePlaybackWhenDeviceConnected,
                    onCheckedChange = {
                        resumePlaybackWhenDeviceConnected = it
                        restartService = true
                    }
                )
                RestartPlayerService(restartService, onRestart = { restartService = false })
            }
        }

        if (search.inputValue.isBlank() || stringResource(R.string.persistent_queue).contains(search.inputValue,true)) {
            SwitchSettingEntry(
                title = stringResource(R.string.persistent_queue),
                text = stringResource(R.string.save_and_restore_playing_songs),
                isChecked = persistentQueue,
                onCheckedChange = {
                    persistentQueue = it
                    restartService = true
                }
            )
            RestartPlayerService(restartService, onRestart = { restartService = false })

            AnimatedVisibility(visible = persistentQueue) {
                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    SwitchSettingEntry(
                        title =  stringResource(R.string.resume_playback_on_start),
                        text = stringResource(R.string.resume_automatically_when_app_opens),
                        isChecked = resumePlaybackOnStart,
                        onCheckedChange = {
                            resumePlaybackOnStart = it
                            restartService = true
                        }
                    )
                    RestartPlayerService(restartService, onRestart = { restartService = false } )
                }
            }
        }

        if (search.inputValue.isBlank() || stringResource(R.string.close_app_with_back_button).contains(search.inputValue,true)) {
            SwitchSettingEntry(
                isEnabled = Build.VERSION.SDK_INT >= 33,
                title = stringResource(R.string.close_app_with_back_button),
                text = stringResource(R.string.when_you_use_the_back_button_from_the_home_page),
                isChecked = closeWithBackButton,
                onCheckedChange = {
                    closeWithBackButton = it
                    restartActivity = true
                }
            )
            //ImportantSettingsDescription(text = stringResource(R.string.restarting_rimusic_is_required))
            RestartActivity(restartActivity, onRestart = { restartActivity = false })
        }

        if (search.inputValue.isBlank() || stringResource(R.string.close_background_player).contains(search.inputValue,true)) {
            SwitchSettingEntry(
                title = stringResource(R.string.close_background_player),
                text = stringResource(R.string.when_app_swipe_out_from_task_manager),
                isChecked = closebackgroundPlayer,
                onCheckedChange = {
                    closebackgroundPlayer = it
                    restartService = true
                }
            )
            RestartPlayerService(restartService, onRestart = { restartService = false } )
        }

        if (search.inputValue.isBlank() || stringResource(R.string.skip_media_on_error).contains(search.inputValue,true)) {
            SwitchSettingEntry(
                title = stringResource(R.string.skip_media_on_error),
                text = stringResource(R.string.skip_media_on_error_description),
                isChecked = skipMediaOnError,
                onCheckedChange = {
                    skipMediaOnError = it
                    restartService = true
                }
            )

            RestartPlayerService(restartService, onRestart = { restartService = false } )

        }

        if (search.inputValue.isBlank() || stringResource(R.string.skip_silence).contains(search.inputValue,true)) {
            SwitchSettingEntry(
                title = stringResource(R.string.skip_silence),
                text = stringResource(R.string.skip_silent_parts_during_playback),
                isChecked = skipSilence,
                onCheckedChange = {
                    skipSilence = it
                }
            )

            AnimatedVisibility(visible = skipSilence) {
                val initialValue by remember { derivedStateOf { minimumSilenceDuration.toFloat() / 1000L } }
                var newValue by remember(initialValue) { mutableFloatStateOf(initialValue) }


                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    SliderSettingsEntry(
                        title = stringResource(R.string.minimum_silence_length),
                        text = stringResource(R.string.minimum_silence_length_description),
                        state = newValue,
                        onSlide = { newValue = it },
                        onSlideComplete = {
                            minimumSilenceDuration = newValue.toLong() * 1000L
                            restartService = true
                        },
                        toDisplay = { stringResource(R.string.format_ms, it.toLong()) },
                        range = 1.00f..2000.000f
                    )

                    RestartPlayerService(restartService, onRestart = { restartService = false } )
                }
            }

        }

        if (search.inputValue.isBlank() || stringResource(R.string.loudness_normalization).contains(search.inputValue,true)) {
            SwitchSettingEntry(
                title = stringResource(R.string.loudness_normalization),
                text = stringResource(R.string.autoadjust_the_volume),
                isChecked = volumeNormalization,
                onCheckedChange = {
                    volumeNormalization = it
                }
            )
            AnimatedVisibility(visible = volumeNormalization) {
                val initialValue by remember { derivedStateOf { loudnessBaseGain } }
                var newValue by remember(initialValue) { mutableFloatStateOf(initialValue) }


                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    SliderSettingsEntry(
                        title = stringResource(R.string.settings_loudness_base_gain),
                        text = stringResource(R.string.settings_target_gain_loudness_info),
                        state = newValue,
                        onSlide = { newValue = it },
                        onSlideComplete = {
                            loudnessBaseGain = newValue
                        },
                        toDisplay = { "%.1f dB".format(loudnessBaseGain).replace(",", ".") },
                        range = -20f..20f
                    )
                }
            }
        }

        if (search.inputValue.isBlank() || stringResource(R.string.settings_audio_bass_boost).contains(search.inputValue,true)) {
            SwitchSettingEntry(
                title = stringResource(R.string.settings_audio_bass_boost),
                text = "",
                isChecked = bassboostEnabled,
                onCheckedChange = {
                    bassboostEnabled = it
                }
            )
            AnimatedVisibility(visible = bassboostEnabled) {
                val initialValue by remember { derivedStateOf { bassboostLevel } }
                var newValue by remember(initialValue) { mutableFloatStateOf(initialValue) }


                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    SliderSettingsEntry(
                        title = stringResource(R.string.settings_bass_boost_level),
                        text = "",
                        state = newValue,
                        onSlide = { newValue = it },
                        onSlideComplete = {
                            bassboostLevel = newValue
                        },
                        toDisplay = { "%.1f".format(bassboostLevel).replace(",", ".") },
                        range = 0f..1f
                    )
                }
            }
        }

        if (search.inputValue.isBlank() || stringResource(R.string.settings_audio_reverb).contains(search.inputValue,true)) {
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.settings_audio_reverb),
                text = stringResource(R.string.settings_audio_reverb_info_apply_a_depth_effect_to_the_audio),
                selectedValue = audioReverb,
                onValueSelected = {
                    audioReverb = it
                    restartService = true
                },
                valueText = {
                    it.textName
                }
            )
            RestartPlayerService(restartService, onRestart = { restartService = false } )
        }

        if (search.inputValue.isBlank() || stringResource(R.string.settings_audio_focus).contains(search.inputValue,true)) {
            SwitchSettingEntry(
                title = stringResource(R.string.settings_audio_focus),
                text = stringResource(R.string.settings_audio_focus_info),
                isChecked = audioFocusEnabled,
                onCheckedChange = {
                    audioFocusEnabled = it
                }
            )
        }

        if (search.inputValue.isBlank() || stringResource(R.string.event_volumekeys).contains(search.inputValue,true)) {
            SwitchSettingEntry(
                title = stringResource(R.string.event_volumekeys),
                text = stringResource(R.string.event_volumekeysinfo),
                isChecked = useVolumeKeysToChangeSong,
                onCheckedChange = {
                    useVolumeKeysToChangeSong = it
                    restartService = true
                }
            )
            RestartPlayerService(restartService, onRestart = { restartService = false } )
        }


        if (search.inputValue.isBlank() || stringResource(R.string.event_shake).contains(search.inputValue,true)) {
            SwitchSettingEntry(
                title = stringResource(R.string.event_shake),
                text = stringResource(R.string.shake_to_change_song),
                isChecked = shakeEventEnabled,
                onCheckedChange = {
                    shakeEventEnabled = it
                    restartService = true
                }
            )
            RestartPlayerService(restartService, onRestart = { restartService = false } )
        }

        if (search.inputValue.isBlank() || stringResource(R.string.settings_enable_pip).contains(search.inputValue,true)) {
            SwitchSettingEntry(
                title = stringResource(R.string.settings_enable_pip),
                text = "",
                isChecked = enablePictureInPicture,
                onCheckedChange = {
                    enablePictureInPicture = it
                    restartActivity = true
                }
            )
            RestartActivity(restartActivity, onRestart = { restartActivity = false })
            AnimatedVisibility(visible = enablePictureInPicture) {
                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {

                    EnumValueSelectorSettingsEntry(
                        title = stringResource(R.string.settings_pip_module),
                        selectedValue = pipModule,
                        onValueSelected = {
                            pipModule = it
                            restartActivity = true
                        },
                        valueText = {
                            when (it) {
                                PipModule.Cover -> stringResource(R.string.pipmodule_cover)
                            }
                        }
                    )

                    SwitchSettingEntry(
                        isEnabled = isAtLeastAndroid12,
                        title = stringResource(R.string.settings_enable_pip_auto),
                        text = stringResource(R.string.pip_info_from_android_12_pip_can_be_automatically_enabled),
                        isChecked = enablePictureInPictureAuto,
                        onCheckedChange = {
                            enablePictureInPictureAuto = it
                            restartActivity = true
                        }
                    )
                    RestartActivity(restartActivity, onRestart = { restartActivity = false })
                }

            }
        }

        if (search.inputValue.isBlank() || stringResource(R.string.settings_enable_autodownload_song).contains(search.inputValue,true)) {
            SwitchSettingEntry(
                title = stringResource(R.string.settings_enable_autodownload_song),
                text = "",
                isChecked = autoDownloadSong,
                onCheckedChange = {
                    autoDownloadSong = it
                }
            )
            AnimatedVisibility(visible = autoDownloadSong) {
                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    SwitchSettingEntry(
                        title = stringResource(R.string.settings_enable_autodownload_song_when_liked),
                        text = "",
                        isChecked = autoDownloadSongWhenLiked,
                        onCheckedChange = {
                            autoDownloadSongWhenLiked = it
                        }
                    )
                    SwitchSettingEntry(
                        title = stringResource(R.string.settings_enable_autodownload_song_when_album_bookmarked),
                        text = "",
                        isChecked = autoDownloadSongWhenAlbumBookmarked,
                        onCheckedChange = {
                            autoDownloadSongWhenAlbumBookmarked = it
                        }
                    )
                }

            }
        }

        if (search.inputValue.isBlank() || stringResource(R.string.equalizer).contains(search.inputValue,true))
            SettingsEntry(
                title = stringResource(R.string.equalizer),
                text = stringResource(R.string.interact_with_the_system_equalizer),
                onClick = launchEqualizer
                /*
                onClick = {
                    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, binder?.player?.audioSessionId)
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                    }

                    try {
                        activityResultLauncher.launch(intent)
                    } catch (e: ActivityNotFoundException) {
                        SmartMessage(context.resources.getString(R.string.info_not_find_application_audio), type = PopupType.Warning, context = context)
                    }
                }
                 */
            )

        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )

    }
}
