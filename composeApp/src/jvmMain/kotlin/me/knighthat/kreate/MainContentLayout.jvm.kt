package me.knighthat.kreate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.app_name
import kreate.composeapp.generated.resources.button_close_app
import me.knighthat.kreate.component.AppNavigationRail
import me.knighthat.kreate.component.MAIN_TITLE_TYPOGRAPHY
import me.knighthat.kreate.component.MainView
import me.knighthat.kreate.component.MiniPlayer
import me.knighthat.kreate.component.WindowControlButtons
import me.knighthat.kreate.util.LocalNavController
import org.jetbrains.compose.resources.stringResource


const val CONTENT_SPACING = 16

@Composable
private fun Content( paddingValues: PaddingValues ) =
    Row(
        horizontalArrangement = Arrangement.spacedBy( CONTENT_SPACING.dp ),
        modifier = Modifier.padding( paddingValues )
    ) {
        val surfaceShape = RoundedCornerShape(10.dp)

        AppNavigationRail()

        MainView(
            containerShape = surfaceShape,
            modifier = Modifier.weight( 1f )
                               .fillMaxSize()
        )


        Column(
            verticalArrangement = Arrangement.spacedBy( CONTENT_SPACING.dp ),
            modifier = Modifier.fillMaxWidth( .3f )
                               .fillMaxHeight()
        ) {
            WindowControlButtons()

            Surface(
                shape = surfaceShape,
                modifier = Modifier.weight( 1f )
                                   .fillMaxWidth()
            ) {

            }

            MiniPlayer( surfaceShape )
        }
    }

@Composable
actual fun MainContent() =
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets(CONTENT_SPACING.dp, CONTENT_SPACING.dp, CONTENT_SPACING.dp, CONTENT_SPACING.dp)
    ) { paddingValues ->
        val navController = rememberNavController()
        CompositionLocalProvider(
            LocalNavController provides navController
        ) { Content( paddingValues ) }
    }