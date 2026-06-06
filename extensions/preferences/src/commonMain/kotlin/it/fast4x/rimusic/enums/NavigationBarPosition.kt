package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import app.kreate.preferences.Preferences
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.position_bottom
import kreate.resources.generated.resources.position_left
import kreate.resources.generated.resources.position_right
import kreate.resources.generated.resources.position_top
import org.jetbrains.compose.resources.StringResource

enum class NavigationBarPosition(
    override val textId: StringResource
): TextView {

    Left(Res.string.position_left),
    Right(Res.string.position_right),
    Top(Res.string.position_top),
    Bottom(Res.string.position_bottom);

    companion object {

        fun current() = Preferences.NAVIGATION_BAR_POSITION.value
    }

    fun isCurrent(): Boolean = current() == this
}