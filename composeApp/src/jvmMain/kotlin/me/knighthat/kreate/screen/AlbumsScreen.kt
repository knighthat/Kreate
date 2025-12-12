package me.knighthat.kreate.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.update
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.tab_albums
import me.knighthat.kreate.di.TopLayoutConfiguration
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject


@Composable
fun AlbumsScreen(
    topLayoutConfiguration: TopLayoutConfiguration = koinInject()
) {
    LaunchedEffect( Unit ) {
        val title = getString(Res.string.tab_albums )
        topLayoutConfiguration.title.update { title }
    }
}