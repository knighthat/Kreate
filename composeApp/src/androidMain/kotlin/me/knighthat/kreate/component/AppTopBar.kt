package me.knighthat.kreate.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.app_icon_inverted
import kreate.composeapp.generated.resources.good_afternoon
import kreate.composeapp.generated.resources.good_evening
import kreate.composeapp.generated.resources.good_morning
import kreate.composeapp.generated.resources.good_night
import me.knighthat.kreate.di.TopLayoutConfiguration
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun AppTopBar( navController: NavController ) {
    val topLayoutConfiguration = koinInject<TopLayoutConfiguration>()
    topLayoutConfiguration.showContent()

    TopAppBar(
        title = {
            var title by remember { mutableStateOf("") }
            var isFirstLaunch by remember { mutableStateOf(true) }
            LaunchedEffect( topLayoutConfiguration.title ) {
                if( isFirstLaunch ) {
                    // Add a small artificial delay to make the title
                    // appear after the transition from splashscreen to
                    // content is finished.
                    delay( 200.milliseconds )
                    isFirstLaunch = false
                }

                if( topLayoutConfiguration.title.isNotBlank() ) {
                    title = topLayoutConfiguration.title
                    return@LaunchedEffect
                }

                val now =
                    Clock.System.now().toLocalDateTime( TimeZone.currentSystemDefault() ).hour
                val strRes = when( now ) {
                    in 5..11 -> Res.string.good_morning
                    in 12..16 -> Res.string.good_afternoon
                    in 17..20 -> Res.string.good_evening
                    else -> Res.string.good_night
                }
                title = getString( strRes )
            }

            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    val inSpec =
                        slideInVertically(
                            animationSpec = tween( durationMillis = 800 )
                        ) { it } + fadeIn(
                            animationSpec = tween( durationMillis = 1000 )
                        )
                    val outputSpec =
                        slideOutVertically(
                            animationSpec = tween( durationMillis = 800 )
                        ) { -it } + fadeOut(
                            animationSpec = tween( durationMillis = 2000 )
                        )

                    inSpec togetherWith outputSpec
                },
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
        }
    )
}
