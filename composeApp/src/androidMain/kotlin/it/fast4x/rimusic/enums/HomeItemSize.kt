package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.fast4x.rimusic.R
import it.fast4x.rimusic.ui.styling.px
import me.knighthat.enums.TextView

enum class HomeItemSize (
    val size: Int,
    @field:StringRes override val textId: Int
): TextView, Drawable {

    SMALL( R.string.small, 104 ),
    MEDIUM( R.string.medium,132 ),
    BIG( R.string.big, 162 );

    @field:DrawableRes
    override val iconId = R.drawable.arrow_forward

    val dp: Dp = this.size.dp
    val px: Int
        @Composable
        get() = this.dp.px
}