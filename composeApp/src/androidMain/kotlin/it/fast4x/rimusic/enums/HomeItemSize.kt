package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import app.kreate.component.Drawable
import it.fast4x.rimusic.ui.styling.px
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.arrow_shape_up
import kreate.resources.generated.resources.arrow_shape_up_stack
import kreate.resources.generated.resources.arrow_shape_up_stack_2
import me.knighthat.enums.TextView
import org.jetbrains.compose.resources.DrawableResource

enum class HomeItemSize (
    val size: Int,
    @field:StringRes override val androidTextId: Int,
    override val iconId: DrawableResource
): TextView, Drawable {

    SMALL(104, R.string.small, Res.drawable.arrow_shape_up),
    MEDIUM(132,R.string.medium, Res.drawable.arrow_shape_up_stack),
    BIG(162, R.string.big, Res.drawable.arrow_shape_up_stack_2);

    val dp: Dp = this.size.dp
    val px: Int
        @Composable
        get() = this.dp.px
}