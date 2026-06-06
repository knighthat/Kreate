package it.fast4x.rimusic.ui.styling

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle


@Immutable
expect class Typography(
    xxxs: TextStyle,
    xxs: TextStyle,
    xs: TextStyle,
    s: TextStyle,
    m: TextStyle,
    l: TextStyle,
    xl: TextStyle,
    xxl: TextStyle,
    xxxl: TextStyle,
    xlxl: TextStyle
)