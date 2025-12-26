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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.dialog.CrashReportDialog
import app.kreate.android.themed.common.component.dialog.Dialog
import app.kreate.android.themed.common.screens.album.YouTubeAlbum
import app.kreate.android.themed.common.screens.artist.YouTubeArtist
import app.kreate.android.themed.common.screens.details.SongDetailsScreen
import app.kreate.android.themed.common.screens.settings.about.Licenses
import app.kreate.android.themed.rimusic.screen.artist.ArtistAlbums
import app.kreate.android.themed.rimusic.screen.playlist.YouTubePlaylist
import app.kreate.database.models.SearchQuery
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.HomeScreenTabs
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.StatisticsType
import it.fast4x.rimusic.enums.TransitionEffect
import it.fast4x.rimusic.extensions.games.pacman.Pacman
import it.fast4x.rimusic.extensions.games.snake.SnakeGame
import it.fast4x.rimusic.models.Mood
import it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import it.fast4x.rimusic.ui.screens.history.HistoryScreen
import it.fast4x.rimusic.ui.screens.home.HomeScreen
import it.fast4x.rimusic.ui.screens.localplaylist.LocalPlaylistScreen
import it.fast4x.rimusic.ui.screens.mood.MoodScreen
import it.fast4x.rimusic.ui.screens.mood.MoodsPageScreen
import it.fast4x.rimusic.ui.screens.newreleases.NewreleasesScreen
import it.fast4x.rimusic.ui.screens.player.Queue
import it.fast4x.rimusic.ui.screens.podcast.PodcastScreen
import it.fast4x.rimusic.ui.screens.profiles.ProfileScreen
import it.fast4x.rimusic.ui.screens.search.SearchScreen
import it.fast4x.rimusic.ui.screens.searchresult.SearchResultScreen
import it.fast4x.rimusic.ui.screens.settings.SettingsScreen
import it.fast4x.rimusic.ui.screens.statistics.StatisticsScreen
import kotlinx.coroutines.delay
import me.knighthat.updater.ChangelogsDialog
import me.knighthat.updater.UpdateHandler
import me.knighthat.utils.Toaster

private val BROWSE_ID_ARG = navArgument( "browseId" ) {
    type = NavType.StringType
}
private val PARAM_ARG = navArgument( "params" ) {
    type = NavType.StringType
    // Allow nullable, therefore, assigns null by default
    nullable = true
}
private val USE_LOGIN_ARG = navArgument( "useLogin" ) {
    type = NavType.BoolType
    // Use default value to make it optional
    defaultValue = false
}
private val ID = navArgument( "id" ) {
    type = NavType.StringType
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
    val context = LocalContext.current

    // Flavor-specific implementation
    UpdateHandler()

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
            route = "${NavRoutes.YT_PLAYLIST}/{browseId}?params={params}&useLogin={useLogin}",
            arguments = listOf( BROWSE_ID_ARG, PARAM_ARG, USE_LOGIN_ARG )
        ) {
            // browseId must not be empty or null in any case
            val browseId = it.arguments!!.getString( "browseId" )!!
            val params = it.arguments!!.getString( "params" )
            val useLogin = it.arguments!!.getBoolean( "useLogin" )

            YouTubePlaylist( navController, browseId, params, useLogin, miniPlayer )
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
                        nullable = true
                    }
                )
            )
        ) { navBackStackEntry ->
            val text = navBackStackEntry.arguments?.getString("text").orEmpty()

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

        composable( NavRoutes.LICENSES.name ) {
            Licenses( navController, miniPlayer )
        }

        composable(
            route = "${NavRoutes.SONG_DETAILS}/{id}",
            arguments = listOf( ID )
        ) { navBackStackEntry ->
            val id = navBackStackEntry.arguments?.getString("id").orEmpty()

            SongDetailsScreen( navController, id )
        }

        composable( NavRoutes.PROFILES.name ) {
            ProfileScreen( navController, miniPlayer)
        }
    }

    val crashReportDialog = remember( context ) {
        CrashReportDialog(context).apply( Dialog::showDialog )
    }
    crashReportDialog.Render()

    if( Preferences.SEEN_CHANGELOGS_VERSION.value != BuildConfig.VERSION_NAME ) {
        val changelogs = remember {
            object: ChangelogsDialog(context) {
                // Automatically enable dialog when this class is init
                override var isActive: Boolean by mutableStateOf( !crashReportDialog.isActive )

                override fun hideDialog() {
                    super.hideDialog()
                    Preferences.SEEN_CHANGELOGS_VERSION.value = BuildConfig.VERSION_NAME
                }
            }
        }
        changelogs.Render()
    }

    // Exit app when user uses back
    var isWarned by remember { mutableStateOf( false ) }
    LaunchedEffect( isWarned ) {
        if( !isWarned ) return@LaunchedEffect

        // Reset [isWarned] after 5s
        delay( 5000L )
        isWarned = false
    }
    BackHandler {
        if( navController.previousBackStackEntry != null ) {
            navController.popBackStack()
            return@BackHandler
        }

        val activity = context as? Activity
        if( Preferences.CLOSE_APP_ON_BACK.value ) {
            if( !isWarned ) {
                Toaster.i( R.string.press_once_again_to_exit )
                isWarned = true
            } else
                activity?.finishAffinity()
        } else
            // Use `false` because compose app is single activity
            activity?.moveTaskToBack( false )
    }
}