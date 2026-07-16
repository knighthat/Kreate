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
import app.kreate.compose.R
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.components.settings.EnumEntry
import app.kreate.components.settings.SettingComponents
import app.kreate.preferences.Preferences

@Composable
fun SwipeActionSettings( search: SettingEntrySearch ) {

    if( search appearsIn R.string.swipe_to_action )
        SettingComponents.BooleanEntry(
            preference = Preferences.ENABLE_SWIPE_ACTION,
            title = stringResource( R.string.swipe_to_action ),
            subtitle = stringResource( R.string.activate_the_action_menu_by_swiping_the_song_left_or_right )
        )

    val isVisible by Preferences.ENABLE_SWIPE_ACTION.collectAsStateWithLifecycle()
    AnimatedVisibility( isVisible ) {
        Column(
            modifier = Modifier.padding(start = 25.dp)
        ) {
            if( search appearsIn R.string.queue_and_local_playlists_left_swipe )
                SettingComponents.EnumEntry(
                    preference = Preferences.QUEUE_SWIPE_LEFT_ACTION,
                    title = stringResource( R.string.queue_and_local_playlists_left_swipe )
                )
            if( search appearsIn R.string.queue_and_local_playlists_right_swipe )
                SettingComponents.EnumEntry(
                    preference = Preferences.QUEUE_SWIPE_RIGHT_ACTION,
                    title = stringResource( R.string.queue_and_local_playlists_right_swipe )
                )
            if( search appearsIn R.string.playlist_left_swipe )
                SettingComponents.EnumEntry(
                    preference = Preferences.PLAYLIST_SWIPE_LEFT_ACTION,
                    title = stringResource( R.string.playlist_left_swipe )
                )
            if( search appearsIn R.string.playlist_right_swipe )
                SettingComponents.EnumEntry(
                    preference = Preferences.PLAYLIST_SWIPE_RIGHT_ACTION,
                    title = stringResource( R.string.playlist_right_swipe )
                )
            if( search appearsIn R.string.album_left_swipe )
                SettingComponents.EnumEntry(
                    preference = Preferences.ALBUM_SWIPE_LEFT_ACTION,
                    title = stringResource( R.string.album_left_swipe )
                )
            if( search appearsIn R.string.album_right_swipe )
                SettingComponents.EnumEntry(
                    preference = Preferences.ALBUM_SWIPE_RIGHT_ACTION,
                    title = stringResource( R.string.album_right_swipe )
                )
        }
    }
}