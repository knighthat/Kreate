package me.knighthat.component.song

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import app.kreate.compose.R
import app.kreate.database.Database
import app.kreate.database.models.Song
import app.kreate.gateway.innertube.YouTube
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.utils.Toaster
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.Optional

class GoToAlbum(
    private val navController: NavController,
    private val song: Song
): MenuIcon, Descriptive, KoinComponent {

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
                    get<YouTube>()
                        .getSongBasicInfo( song.id )
                        .onFailure { err ->
                            Logger.e( "", err, "GoToAlbum" )
                            Toaster.e( R.string.error_failed_to_load_album )
                        }
                        .onSuccess { song ->
                            song.album
                                ?.navigationEndpoint
                                ?.browseEndpoint
                                ?.also {
                                    NavRoutes.YT_ALBUM.navigateHere(
                                        navController,
                                        "${it.browseId}?params=${it.params}"
                                    )
                                }
                        }
                }
            }
        )
    }
}