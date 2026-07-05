package app.kreate.android.themed.common

import android.content.Context
import app.kreate.database.Database
import app.kreate.util.MODIFIED_PREFIX


class ChangeAlbumThumbnail(
    context: Context,
    private val getAlbumId: () -> String
): ChangeThumbnail(context) {

    override fun setThumbnail( url: String ) {
        Database.asyncTransaction {
            val albumId = getAlbumId()
            val url = "$MODIFIED_PREFIX$url"
            albumTable.updateCover( albumId, url )
        }
    }
}