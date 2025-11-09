package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import app.kreate.android.Preferences
import app.kreate.android.R
import me.knighthat.enums.TextView


enum class NavigationBarType(
    @field:StringRes override val androidTextId: Int
): TextView {

    IconAndText( R.string.icon_and_text ),
    IconOnly( R.string.only_icon );

    companion object {

        fun current(): NavigationBarType = Preferences.NAVIGATION_BAR_TYPE.value
    }

    @Composable
    fun isCurrent(): Boolean = current() == this
}