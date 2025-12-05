package me.knighthat.kreate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.update
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.tab_library
import me.knighthat.kreate.di.TopLayoutConfiguration
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject


@Composable
fun LibraryScreen() {
    val topLayoutViewModel = koinInject<TopLayoutConfiguration>()
    LaunchedEffect( Unit ) {
        topLayoutViewModel.title.update {
            getString( Res.string.tab_library )
        }
    }
}