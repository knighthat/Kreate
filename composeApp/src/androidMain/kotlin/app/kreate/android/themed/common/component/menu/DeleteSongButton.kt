package app.kreate.android.themed.common.component.menu

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import app.kreate.android.R
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.di.CacheType
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.ui.components.tab.toolbar.ConfirmDialog
import it.fast4x.rimusic.utils.asSong
import me.knighthat.utils.Toaster
import org.koin.java.KoinJavaComponent.get


class DeleteSongButton : MenuButton<MediaItem>(), ConfirmDialog {

    private lateinit var song: MediaItem
    private lateinit var menu: BottomMenu

    override val iconId: Int = R.drawable.trash
    override val tooltipMessageId: Int = R.string.delete
    override val dialogTitle: String
        @Composable
        get() = title

    override var isActive: Boolean by mutableStateOf( false )

    override fun onClick( menu: BottomMenu, item: MediaItem ) {
        song = item
        this.menu = menu
        super.onShortClick()
    }

    @OptIn(UnstableApi::class)
    override fun onConfirm() {
        menu.hide()

        Database.asyncTransaction {
            get<Cache>(Cache::class.java, CacheType.CACHE).removeResource( song.mediaId )
            // FIXME: This is unsafe, use [DownloadService.sendRemoveDownload] instead
            get<Cache>(Cache::class.java, CacheType.DOWNLOAD).removeResource( song.mediaId )
            songPlaylistMapTable.deleteBySongId( song.mediaId )
            formatTable.deleteBySongId( song.mediaId )
            songTable.delete( song.asSong )
        }

        Toaster.i( R.string.deleted )

        onDismiss()
    }
}