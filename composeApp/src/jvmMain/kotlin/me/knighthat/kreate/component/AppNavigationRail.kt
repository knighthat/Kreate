package me.knighthat.kreate.component

import Kreate.composeApp.APP_VERSION
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.navigation.compose.currentBackStackEntryAsState
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.app_version
import kreate.composeapp.generated.resources.ic_launcher
import me.knighthat.kreate.CONTENT_SPACING
import me.knighthat.kreate.constant.MainNavigationTab
import me.knighthat.kreate.util.LocalNavController
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val COLLAPSED_WIDTH = 64

@Composable
fun AppNavigationRail( modifier: Modifier = Modifier ) =
    NavigationRail(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        header = {
            Image(
                painter = painterResource(Res.drawable.ic_launcher ),
                contentDescription = "App's icon",
                modifier = Modifier.requiredSize( COLLAPSED_WIDTH.dp )
                                   .padding( 10.dp )
                                   .clip( CircleShape )
            )
        },
        modifier = modifier.widthIn( min = COLLAPSED_WIDTH.dp )
    ) {
        val navController = LocalNavController.current
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val colors = NavigationRailItemDefaults.colors().copy(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            selectedIndicatorColor = Color.Transparent,
            unselectedIconColor = MaterialTheme.colorScheme.outlineVariant,
            unselectedTextColor = MaterialTheme.colorScheme.outlineVariant
        )

        MainNavigationTab.entries.fastForEach { tab ->
            if( tab.isSeparated )
                Spacer( Modifier.height( CONTENT_SPACING.dp ) )

            NavigationRailItem(
                selected = tab.route.isHere( currentBackStackEntry ),
                onClick = { navController.navigate( tab.route ) },
                alwaysShowLabel = false,
                colors = colors,
                icon = {
                    Icon(
                        imageVector = tab.imageVector,
                        contentDescription = stringResource( tab.stringRes )
                    )
                }
            )
        }

        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier.weight( 1f )
        ) {
            Text(
                text = stringResource( Res.string.app_version, APP_VERSION ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    maxFontSize = MaterialTheme.typography.bodySmall.fontSize,
                    minFontSize = MaterialTheme.typography.labelMedium.fontSize
                )
            )
        }
    }