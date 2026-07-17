package app.kreate.android.themed.common.component.menu

import androidx.media3.common.MediaItem
import app.kreate.android.constant.MenuPage
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.compose.R
import app.kreate.database.Database
import app.kreate.gateway.innertube.YouTube
import app.kreate.utils.Toaster
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.enums.NavRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get


class GoToAlbumButton : MenuButton<MediaItem>(), KoinComponent {

    override val iconId: Int = R.drawable.album
    override val tooltipMessageId: Int = R.string.go_to_album

    override fun onClick( menu: BottomMenu, item: MediaItem ) {
        CoroutineScope(Dispatchers.IO).launch {
            val page =
                Database.albumTable
                        .findBySongId( item.mediaId )
                        .first()
                        ?.id
                        ?.let {
                            MenuPage.NavRedirect(NavRoutes.YT_ALBUM, it)
                        } ?:
                get<YouTube>()
                    .getSongBasicInfo( item.mediaId )
                    .onFailure { err ->
                        Logger.e( "", err, "GoToAlbum" )
                        Toaster.e( R.string.error_failed_to_load_album )
                    }
                    .getOrNull()
                    ?.album
                    ?.navigationEndpoint
                    ?.browseEndpoint
                    ?.let {
                        MenuPage.NavRedirect(NavRoutes.YT_ALBUM, "${it.browseId}?params=${it.params}")
                    }

            if( page != null )
                menu.show( page )
            else
                Toaster.e( R.string.error_failed_to_load_album )
        }
    }
}