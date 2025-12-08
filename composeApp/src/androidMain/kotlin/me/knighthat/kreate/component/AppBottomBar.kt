package me.knighthat.kreate.component

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastForEach
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.tab_home
import kreate.composeapp.generated.resources.tab_library
import me.knighthat.kreate.constant.Route
import me.knighthat.kreate.constant.SearchTab
import me.knighthat.kreate.di.SharedSearchProperties
import me.knighthat.kreate.preference.Preferences
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject


private val bottomBarInsets: WindowInsets
    @Composable
    get() {
        val context = LocalContext.current
        val insets = remember( context ) {
            val contentResolver = context.contentResolver
            val navigationMode = Settings.Secure.getInt(contentResolver, "navigation_mode")
            if( navigationMode == 2 )
                WindowInsets(0, 0, 0, 0)
            else
                null
        }

        return insets ?: WindowInsets.navigationBars
    }

@Composable
@NonRestartableComposable
private fun RowScope.Item(
    selected: Boolean,
    isIconOnly: Boolean,
    colors: NavigationBarItemColors,
    labelRes: StringResource,
    imageVector: ImageVector,
    onClick: () -> Unit
) =
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = imageVector,
                contentDescription = stringResource(labelRes )
            )
        },
        label = {
            Text( stringResource(labelRes) )
        },
        alwaysShowLabel = !isIconOnly,
        colors = colors
    )

@Composable
private fun RowScope.HomeBottomBar(
    navController: NavController,
    isIconOnly: Boolean,
    colors: NavigationBarItemColors
) {
    // Home button
    Item(
        selected = Route.Home.isHere( navController ),
        isIconOnly = isIconOnly,
        colors = colors,
        labelRes = Res.string.tab_home,
        imageVector = Icons.Rounded.Home,
        onClick = { navController.navigate(Route.Home) }
    )

    // Library button
    Item(
        selected = Route.Library.isHere( navController ),
        isIconOnly = isIconOnly,
        colors = colors,
        labelRes = Res.string.tab_library,
        imageVector = Icons.Rounded.LocalLibrary,
        onClick = { navController.navigate(Route.Library) }
    )
}

@Composable
private fun RowScope.SearchResultsBottomBar(
    isIconOnly: Boolean,
    colors: NavigationBarItemColors,
    sharedSearchProperties: SharedSearchProperties = koinInject()
) {
    val currentTab by sharedSearchProperties.tab.collectAsState()

    SearchTab.entries.fastForEach { tab ->
        Item(
            selected = currentTab == tab,
            isIconOnly = isIconOnly,
            colors = colors,
            labelRes = tab.stringRes,
            imageVector = tab.imageVector,
            onClick = {
                // Must set not update
                sharedSearchProperties.tab.value = tab
            }
        )
    }
}

@Composable
fun AppBottomBar( navController: NavController ) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val isIconOnly by Preferences.ICON_ONLY.collectAsState()
    val colors = NavigationBarItemDefaults.colors().copy(
        selectedIconColor = MaterialTheme.colorScheme.onSurface,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        unselectedIconColor = MaterialTheme.colorScheme.outlineVariant,
        unselectedTextColor = MaterialTheme.colorScheme.outlineVariant
    )
    val enterTransition = slideInVertically(
        animationSpec = tween( 1_000, 1_000 )
    ) { it }
    val exitTransition = slideOutVertically(
        animationSpec = tween( 1_000 )
    ) { it }


    AnimatedVisibility(
        visible = remember( currentBackStackEntry ) {
            Route.isHere<Route.Home>( currentBackStackEntry )
                    || Route.isHere<Route.Library>( currentBackStackEntry )
                    || Route.isHere<Route.Search.Results>( currentBackStackEntry )
        },
        enter = enterTransition,
        exit = exitTransition
    ) {
        AnimatedContent(
            targetState = currentBackStackEntry,
            transitionSpec = { enterTransition togetherWith exitTransition }
        ) {
            NavigationBar(
                windowInsets = bottomBarInsets
            ) {
                when {
                    Route.isHere<Route.Home>( it ) ||
                    Route.isHere<Route.Library>( it ) ->
                        HomeBottomBar( navController, isIconOnly, colors )

                    Route.isHere<Route.Search.Results>( it ) ->
                        SearchResultsBottomBar( isIconOnly, colors )
                }
            }
        }
    }
}