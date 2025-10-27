package me.knighthat.component.song

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import app.kreate.android.R
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.innertube.Innertube
import me.knighthat.utils.Toaster
import timber.log.Timber
import java.util.Optional

class GoToAlbum(
    private val navController: NavController,
    private val song: Song
): MenuIcon, Descriptive {

    override val iconId: Int = R.drawable.album
    override val messageId: Int = R.string.go_to_album
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    private var albumId: Optional<String> = Optional.empty()

    init {
        CoroutineScope( Dispatchers.IO ).launch {
            Database.albumTable
                    .findBySongId( song.id )
                    .first()
                    ?.id
                    ?.let { albumId = Optional.of( it ) }
        }
    }


    override fun onShortClick() {
        albumId.ifPresentOrElse(
            { NavRoutes.YT_ALBUM.navigateHere( navController, it ) },
            {
                Toaster.n( R.string.looking_up_album_from_the_internet )

                CoroutineScope( Dispatchers.IO ).launch {
                    Innertube.songBasicInfo( song.id, CURRENT_LOCALE )
                             .fold(
                                 onSuccess = { song ->
                                     song.album
                                         ?.navigationEndpoint
                                         ?.browseEndpoint
                                         ?.also {
                                             NavRoutes.YT_ALBUM.navigateHere(
                                                 navController,
                                                 "${it.browseId}?params=${it.params}"
                                             )
                                         }
                                 },
                                 onFailure = { err ->
                                     Timber.tag( "GoToAlbum" ).e( err )
                                     Toaster.e( R.string.error_failed_to_load_album )
                                 }
                             )
                }
            }
        )
    }
}