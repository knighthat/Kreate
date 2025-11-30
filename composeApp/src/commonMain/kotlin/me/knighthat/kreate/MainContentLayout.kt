package me.knighthat.kreate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.knighthat.kreate.preference.Preferences
import me.knighthat.kreate.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
expect fun MainContent()

@Composable
@Preview
fun MainContentLayout() {
    val colorScheme by Preferences.COLOR_SCHEME.collectAsState()
    val dynamicColor by Preferences.USE_DYNAMIC_COLOR.collectAsState()

    AppTheme(
        darkTheme = colorScheme.eval(),
        dynamicColor = dynamicColor,
        content = { MainContent() }
    )
}
