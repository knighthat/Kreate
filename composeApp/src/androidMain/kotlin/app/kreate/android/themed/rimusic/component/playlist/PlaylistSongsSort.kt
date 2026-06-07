package app.kreate.android.themed.rimusic.component.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.kreate.android.themed.rimusic.component.tab.Sort
import app.kreate.constant.PlaylistSongSortBy
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.MenuState
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.CoroutineScope

class PlaylistSongsSort(
    coroutineScope: CoroutineScope,
    override val menuState: MenuState,
): Sort<PlaylistSongSortBy>(menuState, Preferences.PLAYLIST_SONGS_SORT_BY, Preferences.PLAYLIST_SONGS_SORT_ORDER, coroutineScope) {

    override fun onLongClick() { /* Does nothing */ }

    @Composable
    override fun ToolBarButton() {
        super.ToolBarButton()

        BasicText(
            text = this.sortBy.text,
            style = typography().s.semiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { super.onLongClick() }
        )
    }
}