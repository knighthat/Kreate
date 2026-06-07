package app.kreate.android.themed.common.screens.settings.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.BooleanEntry
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.animatedEntry
import app.kreate.android.themed.common.component.settings.header
import app.kreate.constant.Type


fun LazyListScope.playerFullscreenLyrics( search: SettingEntrySearch ) {
    header( R.string.full_screen_lyrics_components )

    animatedEntry(
        key = "showLyricsThumbnailFalse",
        visible = true
    ) {
        Column {
            val showTotalDuration by app.kreate.preferences.Preferences.PLAYER_SHOW_TOTAL_QUEUE_TIME.collectAsStateWithLifecycle()
            if ( showTotalDuration && search appearsIn R.string.show_total_time_of_queue )
                SettingComponents.BooleanEntry(
                    preference = app.kreate.preferences.Preferences.PLAYER_IS_QUEUE_DURATION_EXPANDED,
                    title = stringResource( R.string.show_total_time_of_queue )
                )

            if ( search appearsIn R.string.titleartist )
                SettingComponents.BooleanEntry(
                    preference = app.kreate.preferences.Preferences.PLAYER_IS_TITLE_EXPANDED,
                    title = stringResource( R.string.titleartist )
                )

            if ( search appearsIn R.string.timeline )
                SettingComponents.BooleanEntry(
                    preference = app.kreate.preferences.Preferences.PLAYER_IS_TIMELINE_EXPANDED,
                    title = stringResource( R.string.timeline )
                )

            if ( search appearsIn R.string.controls )
                SettingComponents.BooleanEntry(
                    preference = app.kreate.preferences.Preferences.PLAYER_IS_CONTROLS_EXPANDED,
                    title = stringResource( R.string.controls )
                )

            val statsForNerd by app.kreate.preferences.Preferences.PLAYER_STATS_FOR_NERDS.collectAsStateWithLifecycle()
            val showThumbnail by app.kreate.preferences.Preferences.PLAYER_SHOW_THUMBNAIL.collectAsStateWithLifecycle()
            if( statsForNerd
                && (!(showThumbnail && app.kreate.preferences.Preferences.PLAYER_TYPE.value === Type.LEGACY))
                && search appearsIn R.string.statsfornerds
            )
                SettingComponents.BooleanEntry(
                    preference = app.kreate.preferences.Preferences.PLAYER_IS_STATS_FOR_NERDS_EXPANDED,
                    title = stringResource( R.string.statsfornerds )
                )

            val addToPlaylist by app.kreate.preferences.Preferences.PLAYER_ACTION_ADD_TO_PLAYLIST.collectAsStateWithLifecycle()
            val openQueue by app.kreate.preferences.Preferences.PLAYER_ACTION_OPEN_QUEUE_ARROW.collectAsStateWithLifecycle()
            val download by app.kreate.preferences.Preferences.PLAYER_ACTION_DOWNLOAD.collectAsStateWithLifecycle()
            val repeat by app.kreate.preferences.Preferences.PLAYER_ACTION_LOOP.collectAsStateWithLifecycle()
            val lyrics by app.kreate.preferences.Preferences.PLAYER_ACTION_SHOW_LYRICS.collectAsStateWithLifecycle()
            val expand by app.kreate.preferences.Preferences.PLAYER_ACTION_TOGGLE_EXPAND.collectAsStateWithLifecycle()
            val shuffle by app.kreate.preferences.Preferences.PLAYER_ACTION_SHUFFLE.collectAsStateWithLifecycle()
            val sleepTimer by app.kreate.preferences.Preferences.PLAYER_ACTION_SLEEP_TIMER.collectAsStateWithLifecycle()
            val showMenu by app.kreate.preferences.Preferences.PLAYER_ACTION_SHOW_MENU.collectAsStateWithLifecycle()
            val equalizer by app.kreate.preferences.Preferences.PLAYER_ACTION_OPEN_EQUALIZER.collectAsStateWithLifecycle()
            val discovery by app.kreate.preferences.Preferences.PLAYER_ACTION_DISCOVER.collectAsStateWithLifecycle()
            val toggleVideo by app.kreate.preferences.Preferences.PLAYER_ACTION_TOGGLE_VIDEO.collectAsStateWithLifecycle()
            if (
                (addToPlaylist
                        || openQueue
                        || download
                        || repeat
                        || lyrics
                        || expand
                        || shuffle
                        || sleepTimer
                        || showMenu
                        || equalizer
                        || discovery
                        || toggleVideo)
                && search appearsIn R.string.actionbar
            )
                SettingComponents.BooleanEntry(
                    preference = app.kreate.preferences.Preferences.PLAYER_IS_ACTIONS_BAR_EXPANDED,
                    title = stringResource( R.string.actionbar )
                )

            val nextInQueue by app.kreate.preferences.Preferences.PLAYER_SHOW_NEXT_IN_QUEUE.collectAsStateWithLifecycle()
            val isExpanded by app.kreate.preferences.Preferences.PLAYER_IS_ACTIONS_BAR_EXPANDED.collectAsStateWithLifecycle()
            if( nextInQueue
                && isExpanded
                && search appearsIn R.string.miniqueue
            )
                SettingComponents.BooleanEntry(
                    preference = app.kreate.preferences.Preferences.PLAYER_IS_NEXT_IN_QUEUE_EXPANDED,
                    title = stringResource( R.string.miniqueue )
                )
        }
    }
}
