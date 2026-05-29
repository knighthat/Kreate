package app.kreate.android.themed.common.component.menu

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.kreate.android.R
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.database.models.PlaylistPreview
import it.fast4x.rimusic.Database
import org.koin.core.component.KoinComponent
import org.koin.core.component.get


class PinPlaylistButton(preview: PlaylistPreview) : MenuButton<PlaylistPreview>(), KoinComponent {

    private var _title by mutableStateOf( "" )

    override var iconId: Int by mutableIntStateOf( R.drawable.keep_off )
    override var tooltipMessageId: Int by mutableIntStateOf( R.string.info_pin_unpin_playlist )
    override val title: String
        @Composable
        get() = _title

    init {
        val isPinned = preview.playlist.isPinned

        _title = get<Context>().getString( if(isPinned) R.string.word_unpin else R.string.word_pin )
        if( isPinned )
            iconId = R.drawable.keep
    }

    override fun onClick( menu: BottomMenu, item: PlaylistPreview ) {
        val isPinned = !item.playlist.isPinned
        _title = get<Context>().getString( if(isPinned) R.string.word_unpin else R.string.word_pin )
        iconId = if( isPinned ) R.drawable.keep else R.drawable.keep_off

        Database.asyncTransaction {
            playlistTable.togglePin( item.playlist.id )
        }
    }
}