package app.kreate.android.themed.common.component.menu

import androidx.media3.common.MediaItem
import app.kreate.android.R
import app.kreate.android.constant.MenuPage
import app.kreate.android.themed.common.component.BottomMenu


class AddSongToPlaylistButton : MenuButton<MediaItem>() {

    override val iconId: Int = R.drawable.add_in_playlist
    override val tooltipMessageId: Int = R.string.add_to_playlist

    override fun onClick( menu: BottomMenu, item: MediaItem ) {
        val page = MenuPage.AddToPlaylist(item)
        menu.show( page )
    }
}