package me.knighthat.kreate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import me.knighthat.kreate.di.TopLayoutConfiguration
import org.koin.compose.koinInject


@Composable
fun HomeScreen() {
    val topLayoutConfiguration = koinInject<TopLayoutConfiguration>()
    LaunchedEffect( Unit ) {
        topLayoutConfiguration.title = ""
    }
}
