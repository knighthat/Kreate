package it.fast4x.rimusic.enums

import androidx.annotation.AnyThread
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NavRoutes {
    home,
    games,
    gamePacman,
    gameSnake,
    history,
    localPlaylist,
    mood,
    queue,
    search,
    searchResults,
    settings,
    statistics,
    newAlbums,
    moodsPage,
    podcast,
    artistAlbums,
    YT_PLAYLIST,
    YT_ARTIST,
    YT_ALBUM,
    LICENSES,
    SONG_DETAILS;

    companion object {

        private val ESCAPED_SEQUENCES_REGEX = Regex("^((\\[ntrbf])+)|((\\[ntrbf])+\$)")

        fun current( navController: NavController ) = navController.currentBackStackEntry?.destination?.route
    }

    fun isHere( navController: NavController ) = current( navController )?.startsWith( this.name ) ?: false

    fun isNotHere( navController: NavController ) = !isHere( navController )

    /**
     * Launch a non-blocking task and take user to currently selected route.
     *
     * **NOTE:** This function ensures [NavController.navigate] is run on
     * main thread, so you can safely call it from other threads
     */
    @AnyThread
    fun navigateHere( navController: NavController, path: Any? = null ) {
        CoroutineScope( Dispatchers.Default ).launch {
            val cleanPath: String = path?.toString()
                                        .orEmpty()
                                        .trim()
                                        .replace( ESCAPED_SEQUENCES_REGEX, "" )
            val fullPath = "$name%s".format( if( cleanPath.isBlank() ) "" else "/$cleanPath" )

            withContext( Dispatchers.Main ) {
                navController.navigate( fullPath )
            }
        }
    }
}
