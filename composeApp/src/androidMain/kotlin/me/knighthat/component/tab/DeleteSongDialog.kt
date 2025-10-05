package me.knighthat.component.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import app.kreate.database.models.Song
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.MenuState
import it.fast4x.rimusic.ui.components.themed.DeleteDialog
import me.knighthat.utils.Toaster
import java.util.Optional

@UnstableApi
open class DeleteSongDialog(
    activeState: MutableState<Boolean>,
    menuState: MenuState,
    private val binder: PlayerServiceModern.Binder?
) : DeleteDialog(activeState, menuState) {

    companion object {
        @Composable
        operator fun invoke() = DeleteSongDialog(
            remember { mutableStateOf(false) },
            LocalMenuState.current,
            LocalPlayerServiceBinder.current
        )
    }

    var song = Optional.empty<Song>()

    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.delete_song )

    override fun onDismiss() {
        // Always override current value with empty Optional
        // to prevent unwanted outcomes
        song = Optional.empty()
        super.onDismiss()
    }

    override fun onConfirm() {
        song.ifPresent {
            Database.asyncTransaction {
                menuState.hide()
                binder?.cache?.removeResource( it.id )
                binder?.downloadCache?.removeResource( it.id )
                songPlaylistMapTable.deleteBySongId( it.id )
                formatTable.deleteBySongId( it.id )
                songTable.delete( it )
            }

            Toaster.i( R.string.deleted )
        }

        onDismiss()
    }
}