package me.knighthat.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import app.kreate.database.models.Song
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.ui.components.tab.toolbar.ConfirmDialog
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import me.knighthat.utils.Toaster

@UnstableApi
class ResetCache private constructor(
    activeState: MutableState<Boolean>,
    private val binder: PlayerServiceModern.Binder?,
    private val getSongs: () -> List<Song>
): MenuIcon, Descriptive, ConfirmDialog {

    companion object {
        @Composable
        operator fun invoke( getSongs: () -> List<Song> ) =
            ResetCache(
                remember { mutableStateOf(false) },
                LocalPlayerServiceBinder.current,
                getSongs
            )
    }

    override val iconId: Int = R.drawable.refresh_circle
    override val messageId: Int = R.string.info_clean_cached_congs
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.title_reset_cache )
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.are_you_sure )

    override var isActive: Boolean by activeState

    override fun onShortClick() = super.onShortClick()

    override fun onConfirm() {
        Database.asyncTransaction {
            getSongs().forEach { song ->
                binder?.cache?.removeResource( song.id )
                binder?.downloadCache?.removeResource( song.id )
                formatTable.deleteBySongId( song.id )
                formatTable.updateContentLengthOf( song.id )
            }

            Toaster.done()
        }
    }
}