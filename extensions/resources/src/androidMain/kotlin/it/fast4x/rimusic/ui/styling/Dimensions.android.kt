package it.fast4x.rimusic.ui.styling

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


@Composable
actual fun getSongDimension(): Dp =
    with(LocalConfiguration.current) {
        minOf(screenHeightDp, screenWidthDp)
    }.dp