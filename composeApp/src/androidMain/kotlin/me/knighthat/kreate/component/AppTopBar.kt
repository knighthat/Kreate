package me.knighthat.kreate.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.app_icon_inverted
import me.knighthat.kreate.di.TopLayoutConfiguration
import me.knighthat.kreate.viewmodel.AppTopBarViewModel
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun AppTopBar(
    navController: NavController,
    viewModel: AppTopBarViewModel = koinViewModel()
) {
    TopAppBar(
        title = {
            AnimatedContent(
                targetState = viewModel.title,
                transitionSpec = { viewModel.titleSwapTransition },
                modifier = Modifier.fillMaxWidth()
            ) { target ->
                Text(
                    text = target,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        MaterialTheme.typography.titleSmall.fontSize,
                        MaterialTheme.typography.headlineSmall.fontSize
                    )
                )
            }
        },
        actions = {
            IconButton(
                // TODO: Add search route
                onClick = { }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search"
                )
            }

            IconButton(
                // TODO: Add menu
                onClick = { }
            ) {
                Icon(
                    painter = painterResource( Res.drawable.app_icon_inverted ),
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        // Use transparent so [TopLayoutConfiguration.background] is visible
        colors = TopAppBarDefaults.topAppBarColors().copy(
            containerColor = Color.Transparent
        )
    )

    // Show content once TopBar is loaded
    viewModel.topLayoutConfiguration.showContent()
}
