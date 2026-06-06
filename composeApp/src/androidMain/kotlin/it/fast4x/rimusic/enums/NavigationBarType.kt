package it.fast4x.rimusic.enums

import androidx.compose.runtime.Composable
import app.kreate.android.Preferences
import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.icon_and_text
import kreate.resources.generated.resources.only_icon
import org.jetbrains.compose.resources.StringResource


enum class NavigationBarType(
    override val textId: StringResource
): TextView {

    IconAndText(Res.string.icon_and_text),
    IconOnly(Res.string.only_icon);

    companion object {

        fun current(): NavigationBarType = Preferences.NAVIGATION_BAR_TYPE.value
    }

    @Composable
    fun isCurrent(): Boolean = current() == this
}