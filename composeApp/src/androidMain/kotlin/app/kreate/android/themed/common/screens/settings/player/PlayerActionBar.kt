package app.kreate.android.themed.common.screens.settings.player

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.BooleanEntry
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header

fun LazyListScope.playerActionBarSection( search: SettingEntrySearch, isLandscape: Boolean ) {
    header( R.string.player_action_bar )

    entry( search, R.string.action_bar_transparent_background ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.PLAYER_TRANSPARENT_ACTIONS_BAR,
            title = stringResource( R.string.action_bar_transparent_background )
        )
    }
    entry( search, R.string.actionspacedevenly ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.PLAYER_ACTION_BUTTONS_SPACED_EVENLY,
            title = stringResource( R.string.actionspacedevenly )
        )
    }
    entry( search, R.string.tapqueue ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.PLAYER_ACTIONS_BAR_TAP_TO_OPEN_QUEUE,
            title = stringResource( R.string.tapqueue )
        )
    }
    entry( search, R.string.swipe_up_to_open_the_queue ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.PLAYER_ACTIONS_BAR_SWIPE_UP_TO_OPEN_QUEUE,
            title = stringResource( R.string.swipe_up_to_open_the_queue )
        )
    }
    entry( search, R.string.action_bar_show_video_button ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.PLAYER_ACTION_TOGGLE_VIDEO,
            title = stringResource( R.string.action_bar_show_video_button )
        )
    }
    entry( search, R.string.action_bar_show_discover_button ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.PLAYER_ACTION_DISCOVER,
            title = stringResource( R.string.action_bar_show_discover_button )
        )
    }
    entry( search, R.string.action_bar_show_download_button ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.PLAYER_ACTION_DOWNLOAD,
            title = stringResource( R.string.action_bar_show_download_button )
        )
    }
    entry( search, R.string.action_bar_show_add_to_playlist_button ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.PLAYER_ACTION_ADD_TO_PLAYLIST,
            title = stringResource( R.string.action_bar_show_add_to_playlist_button )
        )
    }
    entry( search, R.string.action_bar_show_loop_button ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.PLAYER_ACTION_LOOP,
            title = stringResource( R.string.action_bar_show_loop_button )
        )
    }
    entry( search, R.string.action_bar_show_shuffle_button ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.PLAYER_ACTION_SHUFFLE,
            title = stringResource( R.string.action_bar_show_shuffle_button )
        )
    }
    entry( search, R.string.action_bar_show_lyrics_button ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.PLAYER_ACTION_SHOW_LYRICS,
            title = stringResource( R.string.action_bar_show_lyrics_button )
        )
    }
    item {
        val showThumbnail by app.kreate.preferences.Preferences.PLAYER_SHOW_THUMBNAIL.collectAsStateWithLifecycle()
        val lyricsShowThumbnail by app.kreate.preferences.Preferences.LYRICS_SHOW_THUMBNAIL.collectAsStateWithLifecycle()

        if( search appearsIn R.string.expandedplayer
            && (!isLandscape || !showThumbnail) && !lyricsShowThumbnail
        )
            SettingComponents.BooleanEntry(
                preference = app.kreate.preferences.Preferences.PLAYER_ACTION_TOGGLE_EXPAND,
                title = stringResource( R.string.expandedplayer )
            )
    }
    entry( search, R.string.action_bar_show_sleep_timer_button ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.PLAYER_ACTION_SLEEP_TIMER,
            title = stringResource( R.string.action_bar_show_sleep_timer_button )
        )
    }
    entry( search, R.string.show_equalizer ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.PLAYER_ACTION_OPEN_EQUALIZER,
            title = stringResource( R.string.show_equalizer )
        )
    }
    entry( search, R.string.action_bar_show_arrow_button_to_open_queue ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.PLAYER_ACTION_OPEN_QUEUE_ARROW,
            title = stringResource( R.string.action_bar_show_arrow_button_to_open_queue )
        )
    }
    entry( search, R.string.action_bar_show_start_radio_button ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.PLAYER_ACTION_START_RADIO,
            title = stringResource( R.string.action_bar_show_start_radio_button )
        )
    }
    entry( search, R.string.action_bar_show_menu_button ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.PLAYER_ACTION_SHOW_MENU,
            title = stringResource( R.string.action_bar_show_menu_button )
        )
    }
    entry( search, R.string.title_playback_speed ) {
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.AUDIO_SPEED,
            title = stringResource( R.string.title_playback_speed ),
            subtitleId = R.string.description_playback_speed
        )
    }
}