package me.knighthat.kreate.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.navigation.NavController
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.app_icon_inverted
import me.knighthat.kreate.component.topbar.TopBarSearchBox
import me.knighthat.kreate.constant.Route
import me.knighthat.kreate.viewmodel.AppTopBarViewModel
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.ExperimentalTime


@Composable
private fun MenuIcon( enter: EnterTransition, exit: ExitTransition ) =
    AnimatedVisibility(
        visible = Route.Search.isNotHere,
        enter = enter,
        exit = exit
    ) {
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
    }

@Composable
@NonRestartableComposable
private fun SearchIcon( searchTextField: TextFieldValue, navController: NavController ) =
    IconButton(
        onClick = {
            val isSearching = Route.Search.isHere( navController )
            if( !isSearching ) {
                // If [Route.Search] exists in the stack, then go back to it
                // instead of adding another one to the stack
                navController.navigate( Route.Search ) {
                    popUpTo( Route.Search ) {
                        inclusive = false
                    }

                    launchSingleTop = true
                }
            } else if( searchTextField.text.isBlank() )
                // Cancel & pop back when search box is empty
                navController.popBackStack()
            else
                navController.navigate(
                    Route.Search.Results(searchTextField.text)
                )
        }
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = "Search"
        )
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun AppTopBar(
    navController: NavController,
    viewModel: AppTopBarViewModel = koinViewModel()
) {
    val searchTextField by viewModel.searchProperties.input.collectAsState()

    TopAppBar(
        title = {
            AnimatedContent(
                targetState = viewModel.title to (Route.Search.isHere || Route.isHere<Route.Search.Results>( navController )),
                transitionSpec = { viewModel.topLayoutConfiguration.titleSwapTransition },
                modifier = Modifier.fillMaxWidth()
            ) { (title, isSearching) ->
                if( isSearching )
                    TopBarSearchBox(
                        value = searchTextField,
                        onSearch = {
                            navController.navigate(
                                Route.Search.Results(searchTextField.text)
                            )
                        },
                        onValueChange = {
                            // [MutableStateFlow.update] delays the update process,
                            // which makes the field unchanged
                            viewModel.searchProperties.input.value = it
                        }
                    )
                else
                    Text(
                        text = title,
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
            SearchIcon( searchTextField, navController )
            MenuIcon( viewModel.menuIconEnterTransition, viewModel.menuIconExitTransition )
        },
        // Use transparent so [TopLayoutConfiguration.background] is visible
        colors = TopAppBarDefaults.topAppBarColors().copy(
            containerColor = Color.Transparent
        )
    )

    // Show content once TopBar is loaded
    viewModel.topLayoutConfiguration.showContent()
}
