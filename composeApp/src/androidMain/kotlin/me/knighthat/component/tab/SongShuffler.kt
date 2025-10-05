package me.knighthat.component.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import app.kreate.database.models.Song
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.utils.playShuffled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

@UnstableApi
class SongShuffler private constructor(
    private val binder: PlayerServiceModern.Binder?,
    private val songs: () -> List<Song>
): MenuIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke( songs: () -> List<Song> ) =
            SongShuffler( LocalPlayerServiceBinder.current, songs )

        @Composable
        operator fun invoke(
            databaseCall: (Int) -> Flow<List<Song>>,
            vararg key: Any?
        ): SongShuffler {
            val songsToShuffle by remember( key ) {
                databaseCall( Int.MAX_VALUE )
            }.collectAsState( emptyList(), Dispatchers.IO )

            return SongShuffler { songsToShuffle }
        }

        /**
         * Play songs with order shuffled.
         */
        fun playShuffled(
            binder: PlayerServiceModern.Binder,
            songs: List<Song>
        ) {
            binder.stopRadio()
            songs.also( binder.player::playShuffled )
        }
    }

    override val iconId: Int = R.drawable.shuffle
    override val messageId: Int = R.string.info_shuffle
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.shuffle )

    override fun onShortClick() {
        playShuffled(
            this.binder ?: return,      // Ensure that [binder] isn't null
            this.songs()
        )
    }
}