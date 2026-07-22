package app.kreate.android.themed.common.screens.settings.general

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.animatedEntry
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import app.kreate.components.settings.DurationEntry
import app.kreate.components.settings.EnumEntry
import app.kreate.components.settings.ListEntry
import app.kreate.components.settings.NumberPickerEntry
import app.kreate.components.settings.SettingComponents
import app.kreate.compose.R
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.enums.AudioQualityFormat
import it.fast4x.rimusic.utils.isAtLeastAndroid6
import it.fast4x.rimusic.utils.rememberEqualizerLauncher
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.millisecond
import kreate.resources.generated.resources.second
import kreate.resources.generated.resources.song
import org.jetbrains.compose.resources.pluralStringResource
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds


@ExperimentalMaterial3Api
@UnstableApi
fun LazyListScope.playerSettingsSection( search: SettingEntrySearch ) {
    header( R.string.player )

    entry( search, R.string.audio_quality_format ) {
        SettingComponents.EnumEntry(
            preference = Preferences.AUDIO_QUALITY,
            title = stringResource( R.string.audio_quality_format ),
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.enable_connection_metered ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.IS_CONNECTION_METERED,
            title = stringResource( R.string.enable_connection_metered ),
            subtitle = stringResource( R.string.info_enable_connection_metered )
        ) {
            if ( it )
                Preferences.AUDIO_QUALITY.update( AudioQualityFormat.Auto )
        }
    }
    entry( search, R.string.setting_entry_smart_rewind ) {
        val seconds by Preferences.SMART_REWIND.collectAsStateWithLifecycle()

        SettingComponents.NumberPickerEntry(
            preferences = Preferences.SMART_REWIND,
            unit = Res.plurals.second,
            title = stringResource( R.string.setting_entry_smart_rewind ),
            subtitle = stringResource(
                R.string.setting_description_smart_rewind,
                seconds,
                pluralStringResource( Res.plurals.second, seconds )
            )
        )
    }
    entry( search, R.string.min_listening_time ) {
        SettingComponents.NumberPickerEntry(
            preferences = Preferences.QUICK_PICKS_MIN_DURATION,
            title = stringResource( R.string.min_listening_time ),
            subtitle = stringResource( R.string.is_min_list_time_for_tips_or_quick_pics ),
            unit = Res.plurals.second
        )
    }
    entry( search, R.string.exclude_songs_with_duration_limit ) {
        SettingComponents.DurationEntry(
            preference = Preferences.LIMIT_SONGS_WITH_DURATION,
            title = stringResource( R.string.exclude_songs_with_duration_limit ),
            subtitle = stringResource( R.string.exclude_songs_with_duration_limit_description )
        )
    }
    entry( search, R.string.pause_between_songs ) {
        SettingComponents.DurationEntry(
            preference = Preferences.PAUSE_BETWEEN_SONGS,
            title = stringResource( R.string.pause_between_songs )
        )
    }
    entry( search, R.string.player_pause_listen_history ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.PAUSE_HISTORY,
            title = stringResource( R.string.player_pause_listen_history ),
            subtitle = stringResource( R.string.player_pause_listen_history_info ),
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.player_pause_on_volume_zero ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.PAUSE_WHEN_VOLUME_SET_TO_ZERO,
            title = stringResource( R.string.player_pause_on_volume_zero ),
            subtitle = stringResource( R.string.info_pauses_player_when_volume_zero )
        )
    }
    entry( search, R.string.effect_fade_audio ) {
        val selected by Preferences.AUDIO_FADE_DURATION.collectAsStateWithLifecycle()

        SettingComponents.NumberPickerEntry(
            numbers = Preferences.AUDIO_FADE_DURATION.range.step(100).toList(),
            selected = selected.inWholeMilliseconds,
            unit = Res.plurals.millisecond,
            title = stringResource( R.string.effect_fade_audio ),
            subtitle = stringResource( R.string.effect_fade_audio_description ),
            onValueApplied = {
                Preferences.AUDIO_FADE_DURATION.update( it.milliseconds )
            }
        )
    }
    entry( search, R.string.player_keep_minimized ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.PLAYER_KEEP_MINIMIZED,
            title = stringResource( R.string.player_keep_minimized ),
            subtitle = stringResource( R.string.when_click_on_a_song_player_start_minimized )
        )
    }
    entry( search, R.string.player_collapsed_disable_swiping_down ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.MINI_DISABLE_SWIPE_DOWN_TO_DISMISS,
            title = stringResource( R.string.player_collapsed_disable_swiping_down ),
            subtitle = stringResource( R.string.avoid_closing_the_player_cleaning_queue_by_swiping_down )
        )
    }
    entry( search, R.string.player_auto_load_songs_in_queue ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.QUEUE_AUTO_APPEND,
            title = stringResource( R.string.player_auto_load_songs_in_queue ),
            subtitle = stringResource( R.string.player_auto_load_songs_in_queue_description ),
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.max_songs_in_queue ) {
        val selected by Preferences.MAX_NUMBER_OF_SONG_IN_QUEUE.collectAsStateWithLifecycle()

        SettingComponents.NumberPickerEntry(
            numbers = Preferences.MAX_NUMBER_OF_SONG_IN_QUEUE.range.step(50).toList(),
            selected = selected,
            unit = Res.plurals.song,
            onValueApplied = Preferences.MAX_NUMBER_OF_SONG_IN_QUEUE::update,
            title = stringResource( R.string.max_songs_in_queue )
        )
    }
    entry( search, R.string.discover ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.ENABLE_DISCOVER,
            title = stringResource( R.string.discover ),
            subtitle = stringResource( R.string.discoverinfo )
        )
    }
    entry( search, R.string.playlistindicator ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.SHOW_PLAYLIST_INDICATOR,
            title = stringResource( R.string.playlistindicator ),
            subtitle = stringResource( R.string.playlistindicatorinfo )
        )
    }
    entry( search, R.string.now_playing_indicator ) {
        SettingComponents.EnumEntry(
            preference = Preferences.NOW_PLAYING_INDICATOR,
            title = stringResource( R.string.now_playing_indicator )
        )
    }
    entry( search, R.string.resume_playback, isAtLeastAndroid6 ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.RESUME_PLAYBACK_WHEN_CONNECT_TO_AUDIO_DEVICE,
            title = stringResource( R.string.resume_playback ),
            subtitle = stringResource( R.string.when_device_is_connected ),
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.persistent_queue ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.ENABLE_PERSISTENT_QUEUE,
            title = stringResource( R.string.persistent_queue ),
            subtitle = stringResource( R.string.save_and_restore_playing_songs ),
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    animatedEntry(
        key = "persistentQueueChildren",
        visibleState = Preferences.ENABLE_PERSISTENT_QUEUE,
        modifier = Modifier.padding( start = SettingComponents.CHILDREN_PADDING.dp )
    ) {
        if( search appearsIn R.string.resume_playback_on_start )
            SettingComponents.BooleanEntry(
                preference = Preferences.RESUME_PLAYBACK_ON_STARTUP,
                title = stringResource( R.string.resume_playback_on_start ),
                subtitle = stringResource( R.string.resume_automatically_when_app_opens ),
                action = SettingComponents.Action.RESTART_PLAYER_SERVICE
            )
    }
    entry( search, R.string.close_app_with_back_button ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.CLOSE_APP_ON_BACK,
            title = stringResource( R.string.close_app_with_back_button ),
            subtitle = stringResource( R.string.when_you_use_the_back_button_from_the_home_page ),
            enabled = Build.VERSION.SDK_INT >= 33,
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.skip_media_on_error ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.PLAYBACK_SKIP_ON_ERROR,
            title = stringResource( R.string.skip_media_on_error ),
            subtitle = stringResource( R.string.skip_media_on_error_description ),
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.skip_silence ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.AUDIO_SKIP_SILENCE,
            title = stringResource( R.string.skip_silence ),
            subtitle = stringResource( R.string.skip_silent_parts_during_playback )
        )
    }
    entry( search, R.string.loudness_normalization ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.AUDIO_VOLUME_NORMALIZATION,
            title = stringResource( R.string.loudness_normalization ),
            subtitle = stringResource( R.string.autoadjust_the_volume )
        )
    }
    animatedEntry(
        key = "audioNormalizationChildren",
        visibleState = Preferences.AUDIO_VOLUME_NORMALIZATION,
        modifier = Modifier.padding( start = SettingComponents.CHILDREN_PADDING.dp )
    ) {
        if( search appearsIn R.string.settings_loudness_base_gain )
            SettingComponents.SliderEntry(
                preference = Preferences.AUDIO_VOLUME_NORMALIZATION_TARGET,
                title = stringResource( R.string.settings_loudness_base_gain ),
                subtitle = stringResource( R.string.settings_target_gain_loudness_info ),
                // Matches -20.0 to 20.0, allows empty string and incomplete decimal (i.e. 11.)
                constraint = "^$|^-?(20(\\.[0]?)?|1\\d(\\.\\d?)?|[1-9](\\.\\d?)?|0(\\.\\d?)?)$",
                valueRange = -20f..20f,
                steps = 79,
                onTextDisplay = { "%.1f dB".format( it ) },
                onValueChangeFinished = { p, v -> p.update(v) }
            )
    }
    entry( search, R.string.settings_audio_bass_boost ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.AUDIO_BASS_BOOSTED,
            title = stringResource( R.string.settings_audio_bass_boost )
        )
    }
    animatedEntry(
        key = "bassBoostChildren",
        visibleState = Preferences.AUDIO_BASS_BOOSTED,
        modifier = Modifier.padding( start = SettingComponents.CHILDREN_PADDING.dp )
    ) {
        if( search appearsIn R.string.settings_bass_boost_level )
            SettingComponents.SliderEntry(
                preference = Preferences.AUDIO_BASS_BOOST_LEVEL,
                title = stringResource( R.string.settings_bass_boost_level ),
                // Accepts 0.0 to 1.0, including empty string and incomplete decimal (i.e. 0.)
                constraint = "^$|^\\.$|^(0?(\\.\\d)?|1(\\.0)?)$",
                valueRange = 0f..1f,
                steps = 9,
                onTextDisplay = { "%.1f".format( it ) },
                onValueChangeFinished = { p, v -> p.update(v) },
                modifier = Modifier.padding( start = 25.dp )
            )
    }
    entry( search, R.string.settings_audio_reverb ) {
        val selected by Preferences.AUDIO_REVERB_PRESET.collectAsStateWithLifecycle()

        SettingComponents.ListEntry(
            entries = (0..6).toList().toTypedArray(),
            selected = selected,
            title = stringResource( R.string.settings_audio_reverb ),
            subtitle = stringResource( R.string.settings_audio_reverb_info_apply_a_depth_effect_to_the_audio ),
            getName = {
                when( it ) {
                    0 -> stringResource( R.string.reverb_preset_none )
                    1 -> stringResource( R.string.reverb_preset_small_room )
                    2 -> stringResource( R.string.reverb_preset_medium_room )
                    3 -> stringResource( R.string.reverb_preset_large_room )
                    4 -> stringResource( R.string.reverb_preset_medium_hall )
                    5 -> stringResource( R.string.reverb_preset_large_hall )
                    6 -> stringResource( R.string.reverb_preset_plate )
                    // Code should never reach this, if it does, something else is wrong
                    else -> error( "Unknown reverb preset $it" )
                }
            },
            onConfirmRequest = Preferences.AUDIO_REVERB_PRESET::update
        )
    }
    entry( search, R.string.settings_audio_focus ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.AUDIO_SMART_PAUSE_DURING_CALLS,
            title = stringResource( R.string.settings_audio_focus ),
            subtitle = stringResource( R.string.settings_audio_focus_info )
        )
    }
    entry( search, R.string.event_volumekeys ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.AUDIO_VOLUME_BUTTONS_CHANGE_SONG,
            title = stringResource( R.string.event_volumekeys ),
            subtitle = stringResource( R.string.event_volumekeysinfo ),
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.event_shake ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.AUDIO_SHAKE_TO_SKIP,
            title = stringResource( R.string.event_shake ),
            subtitle = stringResource( R.string.shake_to_change_song ),
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.settings_enable_pip ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.IS_PIP_ENABLED,
            title = stringResource( R.string.settings_enable_pip ),
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    animatedEntry(
        key = "pipChildren",
        visibleState = Preferences.IS_PIP_ENABLED,
        modifier = Modifier.padding( start = SettingComponents.CHILDREN_PADDING.dp )
    ) {
        Column {
            if( search appearsIn R.string.settings_pip_module )
                SettingComponents.EnumEntry(
                    preference = Preferences.PIP_MODULE,
                    title = stringResource( R.string.settings_pip_module ),
                    action = SettingComponents.Action.RESTART_PLAYER_SERVICE
                )

            if( search appearsIn R.string.settings_enable_pip_auto )
                SettingComponents.BooleanEntry(
                    preference = Preferences.IS_AUTO_PIP_ENABLED,
                    title = stringResource( R.string.settings_enable_pip_auto ),
                    subtitle = stringResource( R.string.pip_info_from_android_12_pip_can_be_automatically_enabled ),
                    action = SettingComponents.Action.RESTART_PLAYER_SERVICE
                )
        }
    }
    entry( search, R.string.settings_enable_autodownload_song ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.AUTO_DOWNLOAD,
            title = stringResource( R.string.settings_enable_autodownload_song )
        )
    }
    animatedEntry(
        key = "autoDownloadChildren",
        visibleState = Preferences.AUTO_DOWNLOAD,
        modifier = Modifier.padding( start = SettingComponents.CHILDREN_PADDING.dp )
    ) {
        Column {
            if( search appearsIn R.string.settings_enable_autodownload_song_when_liked )
                SettingComponents.BooleanEntry(
                    preference = Preferences.AUTO_DOWNLOAD_ON_LIKE,
                    title = stringResource( R.string.settings_enable_autodownload_song_when_liked )
                )

            if( search appearsIn R.string.settings_enable_autodownload_song_when_album_bookmarked )
                SettingComponents.BooleanEntry(
                    preference = Preferences.AUTO_DOWNLOAD_ON_ALBUM_BOOKMARKED,
                    title = stringResource( R.string.settings_enable_autodownload_song_when_album_bookmarked )
                )

            if( search appearsIn R.string.settings_enable_autodownload_lyrics_on_song_download )
                SettingComponents.BooleanEntry(
                    preference = Preferences.AUTO_DOWNLOAD_LYRICS_ON_SONG_DOWNLOAD,
                    title = stringResource( R.string.settings_enable_autodownload_lyrics_on_song_download )
                )
        }
    }
    entry( search, R.string.equalizer ) {
        val player: Player = koinInject()
        val launchEqualizer by rememberEqualizerLauncher( { player.audioSessionId } )

        SettingComponents.Entry(
            title = stringResource( R.string.equalizer ),
            subtitle = stringResource( R.string.interact_with_the_system_equalizer ),
            onClick = launchEqualizer
        )
    }
}