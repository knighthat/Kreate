package app.kreate.android.themed.common.component.menu

import androidx.media3.common.MediaItem
import app.kreate.compose.R
import app.kreate.android.constant.MenuPage
import app.kreate.android.themed.common.component.BottomMenu
import it.fast4x.rimusic.enums.NavRoutes


class ViewSongDetailsButton : MenuButton<MediaItem>() {

    override val iconId: Int = R.drawable.information
    override val tooltipMessageId: Int = R.string.description_view_song_info

    override fun onClick( menu: BottomMenu, item: MediaItem ) {
        val page = MenuPage.NavRedirect(NavRoutes.SONG_DETAILS, item.mediaId)
        menu.show( page )
    }
}