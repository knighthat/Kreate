package app.kreate.android.themed.common.component.menu

import androidx.media3.common.MediaItem
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.database.Database
import app.kreate.util.MODIFIED_PREFIX


class SongEditThumbnailButton : EditThumbnailDialog<MediaItem>() {

    private lateinit var songId: String

    override fun onClick( menu: BottomMenu, item: MediaItem ) {
        songId = item.mediaId
        super.onClick( menu, item )
    }

    override fun setThumbnail( url: String ) =
        Database.asyncTransaction {
            songTable.updateThumbnail( songId, "$MODIFIED_PREFIX$url" )
        }
}