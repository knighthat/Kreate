package it.fast4x.rimusic.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.screens.album.YouTubeAlbum
import app.kreate.android.themed.common.screens.artist.YouTubeArtist
import app.kreate.android.themed.rimusic.screen.artist.ArtistAlbums
import app.kreate.android.themed.rimusic.screen.playlist.YouTubePlaylist
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.HomeScreenTabs
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.StatisticsType
import it.fast4x.rimusic.enums.TransitionEffect
import it.fast4x.rimusic.extensions.games.pacman.Pacman
import it.fast4x.rimusic.extensions.games.snake.SnakeGame
import it.fast4x.rimusic.models.Mood
import it.fast4x.rimusic.models.SearchQuery
import it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import it.fast4x.rimusic.ui.screens.history.HistoryScreen
import it.fast4x.rimusic.ui.screens.home.HomeScreen
import it.fast4x.rimusic.ui.screens.localplaylist.LocalPlaylistScreen
import it.fast4x.rimusic.ui.screens.mood.MoodScreen
import it.fast4x.rimusic.ui.screens.mood.MoodsPageScreen
import it.fast4x.rimusic.ui.screens.newreleases.NewreleasesScreen
import it.fast4x.rimusic.ui.screens.player.Queue
import it.fast4x.rimusic.ui.screens.podcast.PodcastScreen
import it.fast4x.rimusic.ui.screens.search.SearchScreen
import it.fast4x.rimusic.ui.screens.searchresult.SearchResultScreen
import it.fast4x.rimusic.ui.screens.settings.SettingsScreen
import it.fast4x.rimusic.ui.screens.statistics.StatisticsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.knighthat.utils.Toaster
import kotlin.system.exitProcess

private val BROWSE_ID_ARG = navArgument( "browseId" ) {
    type = NavType.StringType
}
private val PARAM_ARG = navArgument( "params" ) {
    type = NavType.StringType
    // Use default value to make it optional
    nullable = true
}

@androidx.annotation.OptIn()
@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class,
    ExperimentalTextApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalMaterial3Api::class,
)
@UnstableApi
@Composable
fun AppNavigation(
    navController: NavHostController,
    startPage: HomeScreenTabs,
    miniPlayer: @Composable () -> Unit = {}
) {
    val startDestination = remember( startPage ) {
        Preferences.HOME_TAB_INDEX.value =
            if( startPage == HomeScreenTabs.Search ) Preferences.STARTUP_SCREEN.value.index else startPage.index

        return@remember if( startPage == HomeScreenTabs.Search )
            NavRoutes.search
        else
            NavRoutes.home
    }

    val transitionEffect by Preferences.TRANSITION_EFFECT

    @Composable
    fun modalBottomSheetPage(content: @Composable () -> Unit) {
        var showSheet by rememberSaveable { mutableStateOf(true) }
        val thumbnailRoundness by Preferences.THUMBNAIL_BORDER_RADIUS

        CustomModalBottomSheet(
            showSheet = showSheet,
            onDismissRequest = {
                if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED)
                    navController.popBackStack()
            },
            containerColor = Color.Transparent,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = {
                Surface(
                    modifier = Modifier.padding(vertical = 0.dp),
                    color = Color.Transparent,
                    //shape = thumbnailShape
                ) {}
            },
            shape = thumbnailRoundness.shape
        ) {
            content()
        }
    }

    val enterTransition: (@JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition) =
        {
            when (transitionEffect) {
                TransitionEffect.None -> EnterTransition.None
                TransitionEffect.Expand -> expandIn(animationSpec = tween(350, easing = LinearOutSlowInEasing), expandFrom = Alignment.TopStart)
                TransitionEffect.Fade -> fadeIn(animationSpec = tween(350))
                TransitionEffect.Scale -> scaleIn(animationSpec = tween(350))
                TransitionEffect.SlideVertical -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up)
                TransitionEffect.SlideHorizontal -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left)
            }
        }
    val exitTransition: (@JvmSuppressWildcards AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition) =
        {
            when (transitionEffect) {
                TransitionEffect.None -> ExitTransition.None
                TransitionEffect.Expand -> shrinkOut(animationSpec = tween(350, easing = FastOutSlowInEasing),shrinkTowards = Alignment.TopStart)
                TransitionEffect.Fade -> fadeOut(animationSpec = tween(350))
                TransitionEffect.Scale -> scaleOut(animationSpec = tween(350))
                TransitionEffect.SlideVertical -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down)
                TransitionEffect.SlideHorizontal -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right)
            }
        }

    NavHost(
        navController = navController,
        startDestination = startDestination.name,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = enterTransition,
        popExitTransition = exitTransition
    ) {
        val navigateToPlaylist =
            { browseId: String -> NavRoutes.YT_PLAYLIST.navigateHere( navController, browseId ) }

        composable(route = NavRoutes.home.name) {
            HomeScreen(
                navController = navController,
                onPlaylistUrl = navigateToPlaylist,
                miniPlayer = miniPlayer
            )
        }

        composable(route = NavRoutes.gamePacman.name) {
            modalBottomSheetPage {
                Pacman()
            }

        }

        composable(route = NavRoutes.gameSnake.name) {
            modalBottomSheetPage {
                SnakeGame()
            }

        }

        composable(route = NavRoutes.queue.name) {
            modalBottomSheetPage {
                Queue(
                    navController = navController,
                    onDismiss = {},
                    onDiscoverClick = {}
                )
            }
        }

        composable(
            route = "${NavRoutes.YT_ARTIST}/{browseId}?params={params}",
            arguments = listOf( BROWSE_ID_ARG, PARAM_ARG )
        ) {
            // browseId must not be empty or null in any case
            val browseId = it.arguments!!.getString( "browseId" )!!
            val params = it.arguments!!.getString( "params" )

            YouTubeArtist( navController, browseId, params, miniPlayer )
        }

        composable(
            route = "${NavRoutes.YT_ALBUM}/{browseId}?params={params}",
            arguments = listOf( BROWSE_ID_ARG, PARAM_ARG )
        ) {
            // browseId must not be empty or null in any case
            val browseId = it.arguments!!.getString( "browseId" )!!
            val params = it.arguments!!.getString( "params" )

            YouTubeAlbum( navController, browseId, params, miniPlayer )
        }

        composable(
            route = "${NavRoutes.YT_PLAYLIST}/{browseId}?params={params}",
            arguments = listOf( BROWSE_ID_ARG, PARAM_ARG )
        ) {
            // browseId must not be empty or null in any case
            val browseId = it.arguments!!.getString( "browseId" )!!
            val params = it.arguments!!.getString( "params" )

            YouTubePlaylist( navController, browseId, params, miniPlayer )
        }

        composable(
            route = "${NavRoutes.podcast.name}/{id}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id") ?: ""
            PodcastScreen(
                navController = navController,
                browseId = id,
                params = null,
                miniPlayer = miniPlayer,
            )
        }

        composable(route = NavRoutes.settings.name) {
            SettingsScreen(
                navController = navController,
                miniPlayer = miniPlayer,
            )
        }

        composable(route = NavRoutes.statistics.name) {
            StatisticsScreen(
                navController = navController,
                statisticsType = StatisticsType.Today,
                miniPlayer = miniPlayer,
            )
        }

        composable(route = NavRoutes.history.name) {
            HistoryScreen(
                navController = navController,
                miniPlayer = miniPlayer,

                )
        }

        composable(
            route = "${NavRoutes.search.name}?text={text}",
            arguments = listOf(
                navArgument(
                    name = "text",
                    builder = {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            )
        ) { navBackStackEntry ->
            val text = navBackStackEntry.arguments?.getString("text") ?: ""

            SearchScreen(
                navController = navController,
                miniPlayer = miniPlayer,
                initialTextInput = text,
                onViewPlaylist = {},
                onSearch = { query ->
                    println("onSearch: $query")

                    NavRoutes.searchResults.navigateHere(
                        navController,
                        Uri.encode( query )
                    )

                    if ( !Preferences.PAUSE_SEARCH_HISTORY.value )
                        Database.asyncTransaction {
                            // Must ignore to prevent "UNIQUE constraint" exception
                            searchTable.insertIgnore( SearchQuery(query = query) )
                        }
                },
            )
        }

        composable(
            route = "${NavRoutes.searchResults.name}/{query}",
            arguments = listOf(
                navArgument(
                    name = "query",
                    builder = { type = NavType.StringType }
                )
            )
        ) { navBackStackEntry ->
            val query = navBackStackEntry.arguments?.getString("query") ?: ""

            SearchResultScreen(
                navController = navController,
                miniPlayer = miniPlayer,
                query = query,
                onSearchAgain = {}
            )
        }

        composable(
            route = "${NavRoutes.localPlaylist.name}/{id}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.LongType }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getLong("id") ?: 0L

            LocalPlaylistScreen(
                navController = navController,
                playlistId = id,
                miniPlayer = miniPlayer
            )
        }

        composable(
            route = NavRoutes.mood.name,
        ) { navBackStackEntry ->
            val mood: Mood? = navController.previousBackStackEntry?.savedStateHandle?.get("mood")
            if (mood != null) {
                MoodScreen(
                    navController = navController,
                    mood = mood,
                    miniPlayer = miniPlayer,
                )
            }
        }

        composable(
            route = NavRoutes.moodsPage.name
        ) { navBackStackEntry ->
            MoodsPageScreen(
                navController = navController
            )
        }

        composable(
            route = NavRoutes.newAlbums.name
        ) { navBackStackEntry ->
            NewreleasesScreen(
                navController = navController,
                miniPlayer = miniPlayer,
            )
        }

        composable(
            route = "${NavRoutes.artistAlbums.name}/{id}?params={params}",
            arguments = listOf(
                navArgument(
                    name = "id",
                    builder = { type = NavType.StringType }
                ),
                navArgument(
                    name = "params",
                    builder = {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id").orEmpty()
            val params = navBackStackEntry.arguments?.getString("params").orEmpty()

            ArtistAlbums( navController, id, params, miniPlayer )
        }
    }

    // Exit app when user uses back
    val context = LocalContext.current
    var confirmCount by remember { mutableIntStateOf( 0 ) }
    BackHandler {
        // Prevent this from being applied when user is not on HomeScreen
        if( NavRoutes.home.isNotHere( navController ) )  {
            if ( navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED )
                navController.popBackStack()

            return@BackHandler
        }

        if( confirmCount == 0 ) {
            Toaster.i( R.string.press_once_again_to_exit )
            confirmCount++

            // Reset confirmCount after 5s
            CoroutineScope( Dispatchers.Default ).launch {
                delay( 5000L )
                confirmCount = 0
            }
        } else {
            val activity = context as? Activity
            activity?.finishAffinity()

            if( Preferences.CLOSE_APP_ON_BACK.value )
                // Close app with exit 0 notify that no problem occurred
                exitProcess( 0 )
        }
    }
}