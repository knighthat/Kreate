package me.knighthat.kreate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.knighthat.kreate.component.AppBottomBar
import me.knighthat.kreate.component.AppTopBar
import me.knighthat.kreate.constant.Route


@Composable
actual fun MainContent() {
    val navController = rememberNavController()

    Scaffold(
        topBar = { AppTopBar(navController) },
        bottomBar = { AppBottomBar(navController) },
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .fillMaxSize(),
    ) {
        NavHost(
            navController = navController,
            startDestination = Route.Home,
            modifier = Modifier.padding( it )
        ) {

            composable<Route.Home> {
                HomeScreen()
            }

            composable<Route.Library> {
                LibraryScreen()
            }
        }
    }
}