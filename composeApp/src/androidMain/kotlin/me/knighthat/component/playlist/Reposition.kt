package me.knighthat.component.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.kreate.android.R
import app.kreate.database.models.SongPlaylistMap
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.MenuState
import it.fast4x.rimusic.ui.components.tab.toolbar.ConfirmDialog
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.utils.isAtLeastAndroid14
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.knighthat.utils.Toaster

class Reposition private constructor(
    activeState: MutableState<Boolean>,
    private val menuState: MenuState,
    private val playlistId: Long
): ConfirmDialog, MenuIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke( playlistId: Long ): Reposition =
            Reposition(
                remember { mutableStateOf( false ) },
                LocalMenuState.current,
                playlistId
            )
    }

    override val messageId: Int = R.string.renumber_songs_positions
    override val iconId: Int = R.drawable.position
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.do_you_really_want_to_renumbering_positions_in_this_playlist )
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var isActive: Boolean by activeState

    override fun onShortClick() = super.onShortClick()

    override fun onConfirm() {
        Database.asyncTransaction {
            if( isAtLeastAndroid14 )
                songPlaylistMapTable.shufflePositions( playlistId )
            else
                // This is a slower version, kept for backward-compatibility
                runBlocking( Dispatchers.Default ) {
                    songPlaylistMapTable.allSongsOf( playlistId )
                                        .first()
                                        .shuffled()
                                        .mapIndexed { index, song ->
                                            SongPlaylistMap( song.id, playlistId, index )
                                        }
                }.also( songPlaylistMapTable::updateReplace )

            Toaster.done()
        }

        onDismiss()
        menuState.hide()
    }
}