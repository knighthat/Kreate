package me.knighthat.component.playlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.kreate.android.R
import app.kreate.database.models.Playlist
import app.kreate.util.PINNED_PREFIX
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.DynamicColor
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon

class PinPlaylist(
    val playlist: Playlist?,
): MenuIcon, DynamicColor, Descriptive {

    companion object {
        @Composable
        operator fun invoke( playlist: Playlist ) = PinPlaylist(playlist)
    }

    override val iconId: Int = R.drawable.pin_filled
    override val messageId: Int = R.string.info_pin_unpin_playlist
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var isFirstColor: Boolean
        get() = playlist?.name?.startsWith( PINNED_PREFIX, true ) == true
        set(value) =
            throw UnsupportedOperationException("Please use Database.playlistTable.togglePin(playlistId) instead!")

    override fun onShortClick() = Database.asyncTransaction {
        val playlistId = playlist?.id ?: return@asyncTransaction
        playlistTable.togglePin( playlistId )
    }
}