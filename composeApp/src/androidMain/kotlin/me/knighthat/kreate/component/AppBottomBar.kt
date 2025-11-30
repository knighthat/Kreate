package me.knighthat.kreate.component

import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.tab_home
import kreate.composeapp.generated.resources.tab_library
import me.knighthat.kreate.constant.Route
import me.knighthat.kreate.preference.Preferences
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource


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
fun AppBottomBar( navController: NavController ) = NavigationBar {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    navBackStackEntry?.destination?.hierarchy?.any {
        it.hasRoute<Route.Home>()
    }

    val isIconOnly by Preferences.ICON_ONLY.collectAsState()
    val colors = NavigationBarItemDefaults.colors().copy(
        selectedIconColor = MaterialTheme.colorScheme.onSurface,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        unselectedIconColor = MaterialTheme.colorScheme.outlineVariant,
        unselectedTextColor = MaterialTheme.colorScheme.outlineVariant
    )

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
