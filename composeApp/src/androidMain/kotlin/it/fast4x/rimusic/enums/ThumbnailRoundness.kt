package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class ThumbnailRoundness(
    val shape: Shape,
    @field:StringRes override val androidTextId: Int
): TextView {

    None( RoundedCornerShape(0.dp), R.string.none ),

    Light( RoundedCornerShape(8.dp), R.string.light ),

    Medium( RoundedCornerShape(12.dp), R.string.medium ),

    Heavy( RoundedCornerShape(16.dp), R.string.heavy );
}
