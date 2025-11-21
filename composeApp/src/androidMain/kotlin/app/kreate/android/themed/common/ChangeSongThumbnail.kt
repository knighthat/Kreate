package app.kreate.android.themed.common

import android.content.Context
import app.kreate.util.MODIFIED_PREFIX
import it.fast4x.rimusic.Database


class ChangeSongThumbnail(
    context: Context,
    private val getSongId: () -> String
): ChangeThumbnail(context) {

    override fun setThumbnail( url: String ) {
        Database.asyncTransaction {
            val songId = getSongId()
            val url = "$MODIFIED_PREFIX$url"
            songTable.updateThumbnail( songId, url )
        }
    }
}