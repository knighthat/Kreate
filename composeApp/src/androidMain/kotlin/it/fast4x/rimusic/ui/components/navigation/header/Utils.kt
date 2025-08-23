package it.fast4x.rimusic.ui.components.navigation.header

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kreate.android.Preferences
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.ColorPaletteMode

@Composable
internal fun HeaderIcon(
    iconId: Int,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
    onClick: () -> Unit
) {
    IconButton( onClick ) {
        Icon(
            imageVector = ImageVector.vectorResource( iconId ),
            contentDescription = null,
            modifier = Modifier.size( size ),
            tint = tint
        )
    }
}

internal class Preference {

    internal companion object {

        @Composable
        fun parentalControl(): Boolean = Preferences.PARENTAL_CONTROL.value

        @Composable
        fun debugLog(): Boolean = Preferences.RUNTIME_LOG.value

        @Composable
        fun colorTheme(): ColorPaletteMode = Preferences.THEME_MODE.value
    }
}

internal class AppBar {

    internal companion object {

        @Composable
        fun contentColor(): Color =
             when(it.fast4x.rimusic.ui.components.navigation.header.Preference.colorTheme()) {
                ColorPaletteMode.Light, ColorPaletteMode.System -> colorPalette().text
                else -> Color.White
            }
    }
}