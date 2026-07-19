package app.kreate.android.themed.common.component.menu

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.compose.R
import app.kreate.player.Player
import it.fast4x.rimusic.utils.enqueue
import org.koin.java.KoinJavaComponent.get


class SongEnqueueButton : MenuButton<MediaItem>() {

    override val iconId: Int = R.drawable.enqueue
    override val tooltipMessageId: Int = R.string.info_enqueue_songs
    override val title: String
        @Composable
        get() = stringResource( R.string.enqueue )

    @OptIn(UnstableApi::class)
    override fun onClick( menu: BottomMenu, item: MediaItem ) {
        val context: Context = get(Context::class.java)
        val mediaItems = listOf(item)

        get<Player>(Player::class.java).enqueue( mediaItems, context )
    }
}