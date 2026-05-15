package me.knighthat.component.tab

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.database.models.Song
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.MenuState
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import org.koin.java.KoinJavaComponent.inject

@UnstableApi
class Radio private constructor(
    private val menuState: MenuState,
    private val songs: () -> List<Song>
): MenuIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke( songs: () -> List<Song> ): Radio =
            Radio(
                LocalMenuState.current,
                songs
            )
    }

    override val iconId: Int = R.drawable.radio
    override val messageId: Int = R.string.info_start_radio
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        val player: StatefulPlayer by inject(StatefulPlayer::class.java)
        val songs = songs()
        if( songs.isNotEmpty() )
            player.startRadio( songs.random() )

        menuState.hide()
    }
}