package app.kreate.android.themed.common.component.menu

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.database.models.PlaylistPreview
import app.kreate.database.models.Song
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.enqueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get


class PlaylistEnqueueButton : MenuButton<PlaylistPreview>() {

    override val iconId: Int = R.drawable.enqueue
    override val tooltipMessageId: Int = R.string.info_enqueue_songs
    override val title: String
        @Composable
        get() = stringResource( R.string.enqueue )

    @OptIn(UnstableApi::class)
    override fun onClick( menu: BottomMenu, item: PlaylistPreview ) {
        // Start new coroutine on Main so that calls to Player won't throw error.
        // Other calls to retrieve data from database or
        // to convert items will be done on designated threads.
        CoroutineScope(Dispatchers.Main).launch {
            val songs =
                Database.songPlaylistMapTable
                        .allSongsOf( item.playlist.id )
                        .flowOn( Dispatchers.IO )
                        .map { it.map(Song::asMediaItem) }
                        .flowOn( Dispatchers.Default )
                        .first()

            get<StatefulPlayer>(StatefulPlayer::class.java).enqueue( songs )
        }
    }
}