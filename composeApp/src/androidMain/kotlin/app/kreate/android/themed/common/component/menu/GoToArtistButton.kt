package app.kreate.android.themed.common.component.menu

import androidx.media3.common.MediaItem
import app.kreate.android.R
import app.kreate.android.constant.MenuPage
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.NavRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.innertube.Innertube
import me.knighthat.utils.Toaster


class GoToArtistButton : MenuButton<MediaItem>() {

    override val iconId: Int = R.drawable.people
    override val tooltipMessageId: Int = R.string.go_to_artist

    override fun onClick( menu: BottomMenu, item: MediaItem ) {
        CoroutineScope(Dispatchers.IO).launch {
            val page =
                Database.artistTable
                        .findBySongId( item.mediaId )
                        .first()
                        .firstOrNull()
                        ?.id
                        ?.let {
                            MenuPage.NavRedirect(NavRoutes.YT_ARTIST, it)
                        } ?:
                Innertube.songBasicInfo( item.mediaId, CURRENT_LOCALE )
                         .onFailure { err ->
                             Logger.e( "", err, "GoToArtist" )
                         }
                         .getOrNull()
                         ?.artists
                         ?.firstOrNull()
                         ?.navigationEndpoint
                         ?.browseEndpoint
                         ?.let {
                             MenuPage.NavRedirect(NavRoutes.YT_ARTIST, "${it.browseId}?params=${it.params}")
                         }

            if( page != null )
                menu.show( page )
            else
                Toaster.e( R.string.error_failed_to_load_artist )
        }
    }
}