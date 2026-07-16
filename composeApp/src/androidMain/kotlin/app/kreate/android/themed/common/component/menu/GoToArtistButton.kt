package app.kreate.android.themed.common.component.menu

import androidx.media3.common.MediaItem
import app.kreate.android.constant.MenuPage
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.compose.R
import app.kreate.database.Database
import app.kreate.gateway.innertube.YouTube
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.enums.NavRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.utils.Toaster
import org.koin.core.component.KoinComponent
import org.koin.core.component.get


class GoToArtistButton : MenuButton<MediaItem>(), KoinComponent {

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
                get<YouTube>()
                    .getSongBasicInfo( item.mediaId )
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