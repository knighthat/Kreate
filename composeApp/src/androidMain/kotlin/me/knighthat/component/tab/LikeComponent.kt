package me.knighthat.component.tab

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.kreate.android.R
import app.kreate.database.models.Song
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.MenuState
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import me.knighthat.utils.Toaster

class LikeComponent private constructor(
    private val menuState: MenuState,
    private val getSongs: () -> List<Song>
): MenuIcon, Descriptive{

    companion object {
        @Composable
        operator fun invoke( getSongs: () -> List<Song> ) =
            LikeComponent(LocalMenuState.current, getSongs)
    }

    override val iconId: Int = R.drawable.heart
    override val messageId: Int = R.string.add_to_favorites
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        Database.asyncTransaction {
            getSongs().forEach {
                songTable.likeState( it.id, true )
            }

            Toaster.done()
        }

        /*
        This part is explicitly placed outside
        [Database.asyncTransaction] to ensure
        user can continue using the app while songs
        are getting modified
        */
        menuState.hide()
    }
}