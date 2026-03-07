package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.Preferences
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class NavigationBarPosition(
    @field:StringRes override val androidTextId: Int
): TextView {

    Left( R.string.direction_left ),
    Right( R.string.direction_right ),
    Top( R.string.direction_top ),
    Bottom( R.string.direction_bottom );

    companion object {

        fun current() = Preferences.NAVIGATION_BAR_POSITION.value
    }

    fun isCurrent(): Boolean = current() == this
}