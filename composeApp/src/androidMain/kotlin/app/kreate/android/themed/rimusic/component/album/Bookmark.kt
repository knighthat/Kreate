package app.kreate.android.themed.rimusic.component.album

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.media3.common.util.UnstableApi
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.database.models.Song
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.DualIcon
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.utils.asMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class Bookmark(
    private val albumId: String,
): MenuIcon, Descriptive, DualIcon {

    override val iconId: Int = R.drawable.bookmark
    override val secondIconId: Int = R.drawable.bookmark_outline
    override val messageId: Int = R.string.info_bookmark_album
    override val color: Color
        @Composable
        get() = colorPalette().accent
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var isFirstIcon: Boolean by mutableStateOf( false )

    @OptIn(UnstableApi::class)
    private fun downloadOnBookmark() = CoroutineScope( Dispatchers.IO ).launch {
        Database.songAlbumMapTable
                .allSongsOf( albumId )
                .first()
                .fastMap( Song::asMediaItem )
                .fastForEach {
                    MyDownloadHelper.autoDownload( it )
                }
    }

    override fun onShortClick() = Database.asyncTransaction {
        albumTable.toggleBookmark( albumId )
    }

    @Composable
    override fun ToolBarButton() {
        LaunchedEffect( Unit ) {
            var isInit by mutableStateOf( true )

            Database.albumTable
                    .isBookmarked( albumId )
                    .flowOn( Dispatchers.IO )
                    .distinctUntilChanged()
                    .collectLatest {
                        isFirstIcon = it

                        if( !it ) return@collectLatest

                        if( isInit )
                            isInit = false
                        else if( Preferences.AUTO_DOWNLOAD_ON_ALBUM_BOOKMARKED.value )
                            downloadOnBookmark()
                    }
        }

        super<MenuIcon>.ToolBarButton()
    }
}