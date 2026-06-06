package it.fast4x.rimusic.enums

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kreate.component.Drawable
import app.kreate.component.TextView
import it.fast4x.rimusic.ui.styling.px
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.arrow_shape_up
import kreate.resources.generated.resources.arrow_shape_up_stack
import kreate.resources.generated.resources.arrow_shape_up_stack_2
import kreate.resources.generated.resources.size_big
import kreate.resources.generated.resources.size_medium
import kreate.resources.generated.resources.size_small
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

enum class HomeItemSize (
    val size: Int,
    override val textId: StringResource,
    override val iconId: DrawableResource
): TextView, Drawable {

    SMALL(104, Res.string.size_small, Res.drawable.arrow_shape_up),
    MEDIUM(132, Res.string.size_medium, Res.drawable.arrow_shape_up_stack),
    BIG(162, Res.string.size_big, Res.drawable.arrow_shape_up_stack_2);

    val dp: Dp = this.size.dp
    val px: Int
        @Composable
        get() = this.dp.px
}