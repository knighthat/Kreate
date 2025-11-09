package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import app.kreate.component.Drawable

interface Drawable: Drawable {

    @get:DrawableRes
    val androidIconId: Int
        get() = throw NotImplementedError("""
            This setting uses [${this::class.simpleName}#icon] directly 
            or its [${this::class.simpleName}#iconId] hasn't initialized!
        """.trimIndent())

    override val icon: Painter
        @Composable
        get() = painterResource( this.androidIconId )
}