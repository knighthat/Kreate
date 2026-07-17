package app.kreate.android.themed.common.component.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.compose.R
import app.kreate.database.Database
import app.kreate.database.models.Playlist
import app.kreate.database.models.PlaylistPreview
import app.kreate.utils.Toaster
import it.fast4x.rimusic.ui.components.tab.toolbar.ConfirmDialog


class DeletePlaylistButton : MenuButton<PlaylistPreview>(), ConfirmDialog {

    private lateinit var playlist: Playlist
    private lateinit var menu: BottomMenu

    override val iconId: Int = R.drawable.trash
    override val tooltipMessageId: Int = R.string.dialog_title_delete_playlist
    override val title: String
        @Composable
        get() = stringResource( R.string.delete )
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.dialog_title_delete_playlist )

    override var isActive: Boolean by mutableStateOf( false )

    override fun onClick( menu: BottomMenu, item: PlaylistPreview ) {
        playlist = item.playlist
        this.menu = menu
        super.onShortClick()
    }

    override fun onConfirm() {
        onDismiss()
        menu.hide()

        Database.asyncTransaction {
            playlistTable.delete( playlist )

            Toaster.s( R.string.success_playlist_deleted, playlist.name )
        }
    }
}