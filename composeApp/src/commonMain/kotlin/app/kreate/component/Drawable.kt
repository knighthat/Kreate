package app.kreate.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource


interface Drawable {

    val iconId: DrawableResource
        get() = throw NotImplementedError("""
                This setting uses [${this::class.simpleName}#text] directly 
                or its [${this::class.simpleName}#textId] hasn't initialized!
        """.trimIndent())

    val icon: Painter
        @Composable
        get() = painterResource( iconId )
}