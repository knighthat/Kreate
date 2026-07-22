package app.kreate.android.themed.common.component.menu

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.compose.R
import app.kreate.player.Player
import org.koin.java.KoinJavaComponent.get


class SongPlayNextButton : MenuButton<MediaItem>() {

    override val iconId: Int = R.drawable.play_skip_forward
    override val tooltipMessageId: Int = R.string.play_next

    @OptIn(UnstableApi::class)
    override fun onClick( menu: BottomMenu, item: MediaItem ) {
        val mediaItems = listOf(item)
        get<Player>(Player::class.java).addNext( mediaItems )
    }
}