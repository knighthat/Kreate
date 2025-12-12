package me.knighthat.kreate.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import me.knighthat.kreate.CONTENT_SPACING
import me.knighthat.kreate.constant.Route
import me.knighthat.kreate.di.TopLayoutConfiguration
import me.knighthat.kreate.screen.AlbumsScreen
import me.knighthat.kreate.screen.ArtistsScreen
import me.knighthat.kreate.screen.LibraryScreen
import me.knighthat.kreate.screen.SearchScreen
import me.knighthat.kreate.screen.SongsScreen
import me.knighthat.kreate.util.LocalNavController
import org.koin.compose.koinInject


@Composable
private fun ActualView(
    navController: NavController = LocalNavController.current
) =
    NavHost(
        navController = navController as NavHostController,
        startDestination = Route.Songs
    ) {

        composable<Route.Songs> {
            SongsScreen()
        }

        composable<Route.Albums> {
            AlbumsScreen()
        }

        composable<Route.Artists> {
            ArtistsScreen()
        }

        composable<Route.Library> {
            LibraryScreen()
        }

        composable<Route.Search> {
            SearchScreen()
        }
    }

@Composable
fun MainView(
    containerShape: Shape,
    modifier: Modifier = Modifier,
    topLayoutConfiguration: TopLayoutConfiguration = koinInject()
) =
    Column(
        verticalArrangement = Arrangement.spacedBy( CONTENT_SPACING.dp ),
        modifier = modifier
    ) {
        val currentTitle by topLayoutConfiguration.title.collectAsState()
        AnimatedContent(
            targetState = currentTitle,
            transitionSpec = { topLayoutConfiguration.titleSwapTransition },
            modifier = Modifier.fillMaxWidth( .98f )
                               .align( Alignment.CenterHorizontally )
        ) { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall
            )
        }

        Surface(
            shape = containerShape,
            modifier = Modifier.weight( 1f )
                               .fillMaxSize()
        ) { ActualView() }
    }