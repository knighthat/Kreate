package app.kreate

import androidx.compose.ui.graphics.painter.Painter


expect object AppIcon {

    fun painter(): Painter

    object Round {

        fun painter(): Painter
    }
}