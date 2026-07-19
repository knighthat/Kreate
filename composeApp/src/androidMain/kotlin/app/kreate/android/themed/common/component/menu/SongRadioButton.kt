package app.kreate.android.themed.common.component.menu

import androidx.media3.common.MediaItem
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.compose.R
import app.kreate.player.Player
import org.koin.java.KoinJavaComponent.get


class SongRadioButton : MenuButton<MediaItem>() {

    override val iconId: Int = R.drawable.radio
    override val tooltipMessageId: Int = R.string.info_start_radio

    override fun onClick( menu: BottomMenu, item: MediaItem ) {
        get<Player>(Player::class.java).startRadio( item )
    }
}