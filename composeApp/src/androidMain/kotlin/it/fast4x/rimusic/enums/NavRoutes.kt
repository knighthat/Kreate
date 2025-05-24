package it.fast4x.rimusic.enums

import androidx.annotation.AnyThread
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class NavRoutes {
    home,
    album,
    artist,
    games,
    gamePacman,
    gameSnake,
    history,
    localPlaylist,
    mood,
    playlist,
    queue,
    search,
    searchResults,
    settings,
    statistics,
    newAlbums,
    moodsPage,
    podcast,
    artistAlbums;

    companion object {

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
    fun navigateHere( navController: NavController, path: String ) {
        CoroutineScope( Dispatchers.Main ).launch {
            navController.navigate( "$name/$path" )
        }
    }
}
