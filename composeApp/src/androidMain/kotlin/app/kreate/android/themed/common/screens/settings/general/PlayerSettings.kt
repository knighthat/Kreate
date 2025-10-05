package app.kreate.android.themed.common.screens.settings.general

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.animatedEntry
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.enums.AudioQualityFormat
import it.fast4x.rimusic.utils.rememberEqualizerLauncher
import me.knighthat.component.dialog.InputDialogConstraints

@ExperimentalMaterial3Api
@UnstableApi
fun LazyListScope.playerSettingsSection( search: SettingEntrySearch ) {
    header( R.string.player )

    entry( search, R.string.audio_quality_format ) {
        SettingComponents.EnumEntry(
            preference = Preferences.AUDIO_QUALITY,
            titleId = R.string.audio_quality_format,
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.enable_connection_metered ) {
        SettingComponents.BooleanEntry(
            Preferences.IS_CONNECTION_METERED,
            R.string.enable_connection_metered,
            R.string.info_enable_connection_metered
        ) {
            if ( it )
                Preferences.AUDIO_QUALITY.value = AudioQualityFormat.Auto
        }
    }
    entry( search, R.string.setting_entry_smart_rewind ) {
        val smartRewindInt by remember {derivedStateOf {
            Preferences.SMART_REWIND.value.toInt()
        }}
        SettingComponents.InputDialogEntry(
            preference = Preferences.SMART_REWIND,
            title = stringResource( R.string.setting_entry_smart_rewind ),
            constraint = InputDialogConstraints.POSITIVE_DECIMAL,
            keyboardOption = KeyboardOptions.Default.copy( keyboardType = KeyboardType.Number ),
            subtitle = stringResource(
                R.string.setting_description_smart_rewind,
                pluralStringResource(
                    R.plurals.second,
                    smartRewindInt ,
                    smartRewindInt
                )
            )
        )
    }
    entry( search, R.string.min_listening_time ) {
        SettingComponents.EnumEntry(
            Preferences.QUICK_PICKS_MIN_DURATION,
            titleId = R.string.min_listening_time,
            subtitleId = R.string.is_min_list_time_for_tips_or_quick_pics
        )
    }
    entry( search, R.string.exclude_songs_with_duration_limit ) {
        SettingComponents.EnumEntry(
            Preferences.LIMIT_SONGS_WITH_DURATION,
            titleId = R.string.exclude_songs_with_duration_limit,
            subtitleId = R.string.exclude_songs_with_duration_limit_description
        )
    }
    entry( search, R.string.pause_between_songs ) {
        SettingComponents.EnumEntry(
            Preferences.PAUSE_BETWEEN_SONGS,
            R.string.pause_between_songs
        )
    }
    entry( search, R.string.player_pause_listen_history ) {
        SettingComponents.BooleanEntry(
            Preferences.PAUSE_HISTORY,
            R.string.player_pause_listen_history,
            R.string.player_pause_listen_history_info,
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.player_pause_on_volume_zero ) {
        SettingComponents.BooleanEntry(
            Preferences.PAUSE_WHEN_VOLUME_SET_TO_ZERO,
            R.string.player_pause_on_volume_zero,
            R.string.info_pauses_player_when_volume_zero
        )
    }
    entry( search, R.string.effect_fade_audio ) {
        SettingComponents.EnumEntry(
            Preferences.AUDIO_FADE_DURATION,
            titleId = R.string.effect_fade_audio,
            subtitleId = R.string.effect_fade_audio_description
        )
    }
    entry( search, R.string.player_keep_minimized ) {
        SettingComponents.BooleanEntry(
            Preferences.PLAYER_KEEP_MINIMIZED,
            R.string.player_keep_minimized,
            R.string.when_click_on_a_song_player_start_minimized
        )
    }
    entry( search, R.string.player_collapsed_disable_swiping_down ) {
        SettingComponents.BooleanEntry(
            Preferences.MINI_DISABLE_SWIPE_DOWN_TO_DISMISS,
            R.string.player_collapsed_disable_swiping_down,
            R.string.avoid_closing_the_player_cleaning_queue_by_swiping_down
        )
    }
    entry( search, R.string.player_auto_load_songs_in_queue ) {
        SettingComponents.BooleanEntry(
            Preferences.QUEUE_AUTO_APPEND,
            R.string.player_auto_load_songs_in_queue,
            R.string.player_auto_load_songs_in_queue_description,
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.max_songs_in_queue ) {
        SettingComponents.EnumEntry(
            Preferences.MAX_NUMBER_OF_SONG_IN_QUEUE,
            R.string.max_songs_in_queue
        )
    }
    entry( search, R.string.discover ) {
        SettingComponents.BooleanEntry(
            Preferences.ENABLE_DISCOVER,
            R.string.discover,
            R.string.discoverinfo
        )
    }
    entry( search, R.string.playlistindicator ) {
        SettingComponents.BooleanEntry(
            Preferences.SHOW_PLAYLIST_INDICATOR,
            R.string.playlistindicator,
            R.string.playlistindicatorinfo
        )
    }
    entry( search, R.string.now_playing_indicator ) {
        SettingComponents.EnumEntry(
            Preferences.NOW_PLAYING_INDICATOR,
            R.string.now_playing_indicator
        )
    }
    entry( search, R.string.resume_playback ) {
        SettingComponents.BooleanEntry(
            Preferences.RESUME_PLAYBACK_WHEN_CONNECT_TO_AUDIO_DEVICE,
            R.string.resume_playback,
            R.string.when_device_is_connected,
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.persistent_queue ) {
        SettingComponents.BooleanEntry(
            Preferences.ENABLE_PERSISTENT_QUEUE,
            R.string.persistent_queue,
            R.string.save_and_restore_playing_songs,
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    animatedEntry(
        key = "persistentQueueChildren",
        visible = Preferences.ENABLE_PERSISTENT_QUEUE.value,
        modifier = Modifier.padding( start = SettingComponents.CHILDREN_PADDING.dp )
    ) {
        if( search appearsIn R.string.resume_playback_on_start )
            SettingComponents.BooleanEntry(
                Preferences.RESUME_PLAYBACK_ON_STARTUP,
                R.string.resume_playback_on_start,
                R.string.resume_automatically_when_app_opens,
                action = SettingComponents.Action.RESTART_PLAYER_SERVICE
            )
    }
    entry( search, R.string.close_app_with_back_button ) {
        SettingComponents.BooleanEntry(
            Preferences.CLOSE_APP_ON_BACK,
            R.string.close_app_with_back_button,
            R.string.when_you_use_the_back_button_from_the_home_page,
            isEnabled = Build.VERSION.SDK_INT >= 33,
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.close_background_player ) {
        SettingComponents.BooleanEntry(
            Preferences.CLOSE_BACKGROUND_JOB_IN_TASK_MANAGER,
            R.string.close_background_player,
            R.string.when_app_swipe_out_from_task_manager,
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.skip_media_on_error ) {
        SettingComponents.BooleanEntry(
            Preferences.PLAYBACK_SKIP_ON_ERROR,
            R.string.skip_media_on_error,
            R.string.skip_media_on_error_description,
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.skip_silence ) {
        SettingComponents.BooleanEntry(
            Preferences.AUDIO_SKIP_SILENCE,
            R.string.skip_silence,
            R.string.skip_silent_parts_during_playback
        )
    }
    animatedEntry(
        key = "skipSilenceChildren",
        visible = Preferences.AUDIO_SKIP_SILENCE.value,
        modifier = Modifier.padding( start = SettingComponents.CHILDREN_PADDING.dp )
    ) {
        if( search appearsIn R.string.minimum_silence_length )
            SettingComponents.SliderEntry(
                preference = Preferences.AUDIO_SKIP_SILENCE_LENGTH,
                titleId = R.string.minimum_silence_length,
                subtitleId = R.string.minimum_silence_length_description,
                // Allow positive numbers from 0 to 20_000 and empty string
                constraints = "^(20000|1?\\d{1,4}|[1-9]?\\d{0,3}|0)?$",
                valueRange = 0f..20_000f,
                steps = 199,     // 100ms per step
                onTextDisplay = {
                    // Float calculation is inaccurate, therefore, when
                    // converted to Long, some value is imperfect (i.e. 9999)
                    // This will ceil the value to make it truly 100ms per step
                    val longValue = (it.toLong() + 99) / 100 * 100
                    stringResource( R.string.format_ms, longValue )
                },
                onValueChangeFinished = { p, v -> p.value = v.toLong() },
                action = SettingComponents.Action.RESTART_PLAYER_SERVICE
            )
    }
    entry( search, R.string.loudness_normalization ) {
        SettingComponents.BooleanEntry(
            Preferences.AUDIO_VOLUME_NORMALIZATION,
            R.string.loudness_normalization,
            R.string.autoadjust_the_volume
        )
    }
    animatedEntry(
        key = "audioNormalizationChildren",
        visible = Preferences.AUDIO_VOLUME_NORMALIZATION.value,
        modifier = Modifier.padding( start = SettingComponents.CHILDREN_PADDING.dp )
    ) {
        if( search appearsIn R.string.settings_loudness_base_gain )
            SettingComponents.SliderEntry(
                preference = Preferences.AUDIO_VOLUME_NORMALIZATION_TARGET,
                titleId = R.string.settings_loudness_base_gain,
                subtitleId = R.string.settings_target_gain_loudness_info,
                // Matches -20.0 to 20.0, allows empty string and incomplete decimal (i.e. 11.)
                constraints = "^$|^-?(20(\\.[0]?)?|1\\d(\\.\\d?)?|[1-9](\\.\\d?)?|0(\\.\\d?)?)$",
                valueRange = -20f..20f,
                steps = 79,
                onTextDisplay = { "%.1f dB".format( it ) },
                onValueChangeFinished = { p, v -> p.value = v }
            )
    }
    entry( search, R.string.settings_audio_bass_boost ) {
        SettingComponents.BooleanEntry(
            Preferences.AUDIO_BASS_BOOSTED,
            R.string.settings_audio_bass_boost
        )
    }
    animatedEntry(
        key = "bassBoostChildren",
        visible = Preferences.AUDIO_BASS_BOOSTED.value,
        modifier = Modifier.padding( start = SettingComponents.CHILDREN_PADDING.dp )
    ) {
        if( search appearsIn R.string.settings_bass_boost_level )
            SettingComponents.SliderEntry(
                preference = Preferences.AUDIO_BASS_BOOST_LEVEL,
                title = stringResource( R.string.settings_bass_boost_level ),
                // Accepts 0.0 to 1.0, including empty string and incomplete decimal (i.e. 0.)
                constraints = "^$|^\\.$|^(0?(\\.\\d)?|1(\\.0)?)$",
                valueRange = 0f..1f,
                steps = 9,
                onTextDisplay = { "%.1f".format( it ) },
                onValueChangeFinished = { p, v -> p.value = v },
                modifier = Modifier.padding( start = 25.dp )
            )
    }
    entry( search, R.string.settings_audio_reverb ) {
        SettingComponents.EnumEntry(
            Preferences.AUDIO_REVERB_PRESET,
            titleId = R.string.settings_audio_reverb,
            subtitleId = R.string.settings_audio_reverb_info_apply_a_depth_effect_to_the_audio,
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.settings_audio_focus ) {
        SettingComponents.BooleanEntry(
            Preferences.AUDIO_SMART_PAUSE_DURING_CALLS,
            R.string.settings_audio_focus,
            R.string.settings_audio_focus_info
        )
    }
    entry( search, R.string.event_volumekeys ) {
        SettingComponents.BooleanEntry(
            Preferences.AUDIO_VOLUME_BUTTONS_CHANGE_SONG,
            R.string.event_volumekeys,
            R.string.event_volumekeysinfo,
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.event_shake ) {
        SettingComponents.BooleanEntry(
            Preferences.AUDIO_SHAKE_TO_SKIP,
            R.string.event_shake,
            R.string.shake_to_change_song,
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    entry( search, R.string.settings_enable_pip ) {
        SettingComponents.BooleanEntry(
            Preferences.IS_PIP_ENABLED,
            R.string.settings_enable_pip,
            action = SettingComponents.Action.RESTART_PLAYER_SERVICE
        )
    }
    animatedEntry(
        key = "pipChildren",
        visible = Preferences.IS_PIP_ENABLED.value,
        modifier = Modifier.padding( start = SettingComponents.CHILDREN_PADDING.dp )
    ) {
        Column {
            if( search appearsIn R.string.settings_pip_module )
                SettingComponents.EnumEntry(
                    Preferences.PIP_MODULE,
                    R.string.settings_pip_module,
                    action = SettingComponents.Action.RESTART_PLAYER_SERVICE
                )

            if( search appearsIn R.string.settings_enable_pip_auto )
                SettingComponents.BooleanEntry(
                    Preferences.IS_AUTO_PIP_ENABLED,
                    R.string.settings_enable_pip_auto,
                    R.string.pip_info_from_android_12_pip_can_be_automatically_enabled,
                    action = SettingComponents.Action.RESTART_PLAYER_SERVICE
                )
        }
    }
    entry( search, R.string.settings_enable_autodownload_song ) {
        SettingComponents.BooleanEntry(
            Preferences.AUTO_DOWNLOAD,
            R.string.settings_enable_autodownload_song
        )
    }
    animatedEntry(
        key = "autoDownloadChildren",
        visible = Preferences.AUTO_DOWNLOAD.value,
        modifier = Modifier.padding( start = SettingComponents.CHILDREN_PADDING.dp )
    ) {
        Column {
            if( search appearsIn R.string.settings_enable_autodownload_song_when_liked )
                SettingComponents.BooleanEntry(
                    Preferences.AUTO_DOWNLOAD_ON_LIKE,
                    R.string.settings_enable_autodownload_song_when_liked
                )

            if( search appearsIn R.string.settings_enable_autodownload_song_when_album_bookmarked )
                SettingComponents.BooleanEntry(
                    Preferences.AUTO_DOWNLOAD_ON_ALBUM_BOOKMARKED,
                    R.string.settings_enable_autodownload_song_when_album_bookmarked
                )
        }
    }
    entry( search, R.string.equalizer ) {
        val binder = LocalPlayerServiceBinder.current
        val launchEqualizer by rememberEqualizerLauncher( { binder?.player?.audioSessionId } )

        SettingComponents.Text(
            title = stringResource( R.string.equalizer ),
            subtitle = stringResource( R.string.interact_with_the_system_equalizer ),
            onClick = launchEqualizer
        )
    }
}