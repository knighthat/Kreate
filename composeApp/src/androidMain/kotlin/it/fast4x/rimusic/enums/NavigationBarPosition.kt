package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import it.fast4x.rimusic.R
import it.fast4x.rimusic.utils.navigationBarPositionKey
import it.fast4x.rimusic.utils.rememberPreference
import me.knighthat.enums.TextView

enum class NavigationBarPosition(
    @field:StringRes override val textId: Int
): TextView {

    Left( R.string.direction_left ),
    Right( R.string.direction_right ),
    Top( R.string.direction_top ),
    Bottom( R.string.direction_bottom );

    companion object {

        @Composable
        fun current() = rememberPreference( navigationBarPositionKey, Bottom ).value
    }

    @Composable
    fun isCurrent(): Boolean = current() == this
}