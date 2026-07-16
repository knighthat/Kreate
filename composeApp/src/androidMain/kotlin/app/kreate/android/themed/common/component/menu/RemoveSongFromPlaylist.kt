package app.kreate.android.themed.common.component.menu

import android.content.Context
import androidx.media3.common.MediaItem
import app.kreate.compose.R
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.database.Database
import app.kreate.database.models.Playlist
import app.kreate.util.cleanPrefix
import org.koin.java.KoinJavaComponent.get


class RemoveSongFromPlaylist(
    private val playlist: Playlist
) : MenuButton<MediaItem>() {

    override val iconId: Int = R.drawable.playlist_remove
    override val tooltipMessageId: Int = R.string.remove_from_playlist

    override fun onClick( menu: BottomMenu, item: MediaItem ) {
        // Force closing to discard old metadata
        menu.hide()

        val context: Context = get(Context::class.java)
        Database.asyncTransaction {
            songPlaylistMapTable.deleteBySongId( item.mediaId, playlist.id )

            val title = item.mediaMetadata.title?.toString()?.let( ::cleanPrefix ).orEmpty()
            val artist = item.mediaMetadata.artist?.toString()?.let( ::cleanPrefix ).orEmpty()
            context.getString(
                R.string.success_removed_song_from_playlist,
                "$artist - $title",
                playlist.name
            )
        }
    }
}