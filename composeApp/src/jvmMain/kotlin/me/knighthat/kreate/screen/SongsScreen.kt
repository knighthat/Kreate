package me.knighthat.kreate.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.update
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.tab_songs
import me.knighthat.kreate.di.TopLayoutConfiguration
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject


@Composable
fun SongsScreen(
    topLayoutConfiguration: TopLayoutConfiguration = koinInject()
) {
    LaunchedEffect( Unit ) {
        val title = getString(Res.string.tab_songs )
        topLayoutConfiguration.title.update { title }
    }
}