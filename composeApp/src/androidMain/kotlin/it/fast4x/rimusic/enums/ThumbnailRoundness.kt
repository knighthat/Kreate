package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class ThumbnailRoundness(
    @field:StringRes override val textId: Int
): TextView {

    None( R.string.none ),

    Light( R.string.light ),

    Medium( R.string.medium ),

    Heavy( R.string.heavy );

    fun shape(): Shape {
        return when (this) {
            None -> RectangleShape
            Light -> RoundedCornerShape(8.dp)
            Medium -> RoundedCornerShape(12.dp)
            Heavy -> RoundedCornerShape(16.dp)
        }
    }

}
