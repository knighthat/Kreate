package app.kreate.android.themed.common.screens.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.BooleanEntry
import app.kreate.android.themed.common.component.settings.EnumEntry
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch

@Composable
fun SwipeActionSettings( search: SettingEntrySearch ) {

    if( search appearsIn R.string.swipe_to_action )
        SettingComponents.BooleanEntry(
            preference = app.kreate.preferences.Preferences.ENABLE_SWIPE_ACTION,
            title = stringResource( R.string.swipe_to_action ),
            subtitleId = R.string.activate_the_action_menu_by_swiping_the_song_left_or_right
        )

    val isVisible by app.kreate.preferences.Preferences.ENABLE_SWIPE_ACTION.collectAsStateWithLifecycle()
    AnimatedVisibility( isVisible ) {
        Column(
            modifier = Modifier.padding(start = 25.dp)
        ) {
            if( search appearsIn R.string.queue_and_local_playlists_left_swipe )
                SettingComponents.EnumEntry(
                    preference = app.kreate.preferences.Preferences.QUEUE_SWIPE_LEFT_ACTION,
                    title = stringResource( R.string.queue_and_local_playlists_left_swipe )
                )
            if( search appearsIn R.string.queue_and_local_playlists_right_swipe )
                SettingComponents.EnumEntry(
                    preference = app.kreate.preferences.Preferences.QUEUE_SWIPE_RIGHT_ACTION,
                    title = stringResource( R.string.queue_and_local_playlists_right_swipe )
                )
            if( search appearsIn R.string.playlist_left_swipe )
                SettingComponents.EnumEntry(
                    preference = app.kreate.preferences.Preferences.PLAYLIST_SWIPE_LEFT_ACTION,
                    title = stringResource( R.string.playlist_left_swipe )
                )
            if( search appearsIn R.string.playlist_right_swipe )
                SettingComponents.EnumEntry(
                    preference = app.kreate.preferences.Preferences.PLAYLIST_SWIPE_RIGHT_ACTION,
                    title = stringResource( R.string.playlist_right_swipe )
                )
            if( search appearsIn R.string.album_left_swipe )
                SettingComponents.EnumEntry(
                    preference = app.kreate.preferences.Preferences.ALBUM_SWIPE_LEFT_ACTION,
                    title = stringResource( R.string.album_left_swipe )
                )
            if( search appearsIn R.string.album_right_swipe )
                SettingComponents.EnumEntry(
                    preference = app.kreate.preferences.Preferences.ALBUM_SWIPE_RIGHT_ACTION,
                    title = stringResource( R.string.album_right_swipe )
                )
        }
    }
}