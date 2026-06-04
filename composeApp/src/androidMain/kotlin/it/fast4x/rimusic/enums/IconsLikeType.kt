package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import app.kreate.android.R
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.favorite
import kreate.resources.generated.resources.favorite_filled
import kreate.resources.generated.resources.heart_apple
import kreate.resources.generated.resources.heart_apple_outline
import kreate.resources.generated.resources.heart_breaked_no
import kreate.resources.generated.resources.heart_breaked_yes
import kreate.resources.generated.resources.heart_brilliant
import kreate.resources.generated.resources.heart_brilliant_outline
import kreate.resources.generated.resources.heart_dislike
import kreate.resources.generated.resources.heart_gift
import kreate.resources.generated.resources.heart_gift_outline
import kreate.resources.generated.resources.heart_shape
import kreate.resources.generated.resources.heart_shape_outline
import kreate.resources.generated.resources.heart_striped
import kreate.resources.generated.resources.heart_striped_outline
import me.knighthat.enums.TextView
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

enum class IconLikeType(
    val likedIconId: DrawableResource,
    val neutralIconId: DrawableResource,
    @field:StringRes override val androidTextId: Int,
): TextView {

    Apple(Res.drawable.heart_apple, Res.drawable.heart_apple_outline, R.string.icon_like_apple),

    Breaked(Res.drawable.heart_breaked_no, Res.drawable.heart_breaked_yes, R.string.icon_like_breaked),

    Brilliant(Res.drawable.heart_brilliant, Res.drawable.heart_brilliant_outline, R.string.icon_like_brilliant),

    Essential(Res.drawable.favorite_filled, Res.drawable.favorite, R.string.pcontrols_essential),

    Gift(Res.drawable.heart_gift, Res.drawable.heart_gift_outline, R.string.icon_like_gift),

    Shape(Res.drawable.heart_shape, Res.drawable.heart_shape_outline, R.string.icon_like_shape),

    Striped(Res.drawable.heart_striped, Res.drawable.heart_striped_outline, R.string.icon_like_striped);

    val likedIcon: Painter
        @Composable
        get() = painterResource( this.likedIconId )
    val neutralIcon: Painter
        @Composable
        get() = painterResource( this.neutralIconId )
    val dislikeIconId: DrawableResource = Res.drawable.heart_dislike
}