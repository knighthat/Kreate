package me.knighthat.kreate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.navigation.NavController
import androidx.navigation.NavController.OnDestinationChangedListener
import androidx.navigation.NavDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.SavedState
import coil3.compose.AsyncImage
import me.knighthat.kreate.component.AppBottomBar
import me.knighthat.kreate.component.AppTopBar
import me.knighthat.kreate.constant.Route
import me.knighthat.kreate.di.TopLayoutConfiguration
import me.knighthat.kreate.screen.HomeScreen
import me.knighthat.kreate.screen.LibraryScreen
import me.knighthat.kreate.screen.SearchResults
import me.knighthat.kreate.screen.SearchScreen
import me.knighthat.kreate.util.LocalNavController
import org.koin.compose.koinInject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


@Composable
private fun Background(
    topLayoutConfiguration: TopLayoutConfiguration = koinInject()
) {
    // Coil3 has mechanism to prevent the app from crashing if empty string
    // or null value is passed to it. But it'll throw error, which is
    // not really useful for debugging purposes.
    if( topLayoutConfiguration.background.isNullOrBlank() ) return

    AsyncImage(
        model = topLayoutConfiguration.background,
        contentDescription = "main background",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth()
                           .fillMaxHeight( .5f )
    )

    val containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    Box(
        Modifier.fillMaxWidth()
                .fillMaxHeight( .5f )
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to containerColor.copy( .01f ),
                            1f to containerColor
                        )
                    )
                )
    )
}

@Composable
actual fun MainContent() =
    Box(
        Modifier.fillMaxSize()
                .background( MaterialTheme.colorScheme.surfaceContainerLowest )
    ) {
        val navController = rememberNavController()

        CompositionLocalProvider(
            LocalNavController provides navController
        ) {
            Background()

            Scaffold(
                topBar = { AppTopBar(navController) },
                bottomBar = { AppBottomBar(navController) },
                containerColor = Color.Transparent
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Route.Home,
                    modifier = Modifier.padding( padding )
                ) {

                    composable<Route.Home> {
                        HomeScreen()
                    }

                    composable<Route.Library> {
                        LibraryScreen()
                    }

                    composable<Route.Search> {
                        SearchScreen()
                    }

                    composable<Route.Search.Results> {
                        SearchResults()
                    }
                }
            }
        }

        DisposableEffect( navController ) {
            navController.addOnDestinationChangedListener( NavControllerDestinationListener )

            onDispose {
                navController.removeOnDestinationChangedListener( NavControllerDestinationListener )
            }
        }
    }

/**
 * A list of things to do when user navigates to another place
 */
private object NavControllerDestinationListener: OnDestinationChangedListener, KoinComponent {

    private val topLayoutConfiguration by inject<TopLayoutConfiguration>()

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: SavedState?
    ) {
        // Always reset background on destination change
        topLayoutConfiguration.background = ""
    }
}