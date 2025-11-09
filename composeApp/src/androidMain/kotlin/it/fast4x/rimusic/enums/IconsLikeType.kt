package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class IconLikeType(
    @field:DrawableRes val likedIconId: Int,
    @field:DrawableRes val neutralIconId: Int,
    @field:StringRes override val androidTextId: Int,
): TextView, Drawable {

    Apple( R.drawable.heart_apple, R.drawable.heart_apple_outline, R.string.icon_like_apple ),

    Breaked( R.drawable.heart_breaked_no, R.drawable.heart_breaked_yes, R.string.icon_like_breaked ),

    Brilliant( R.drawable.heart_brilliant, R.drawable.heart_brilliant_outline, R.string.icon_like_brilliant ),

    Essential( R.drawable.heart, R.drawable.heart_outline, R.string.pcontrols_essential ),

    Gift( R.drawable.heart_gift, R.drawable.heart_gift_outline, R.string.icon_like_gift ),

    Shape( R.drawable.heart_shape, R.drawable.heart_shape_outline, R.string.icon_like_shape ),

    Striped( R.drawable.heart_striped, R.drawable.heart_striped_outline, R.string.icon_like_striped );

    val likedIcon: Painter
        @Composable
        get() = painterResource( this.likedIconId )
    val neutralIcon: Painter
        @Composable
        get() = painterResource( this.neutralIconId )
    @field: DrawableRes
    val dislikeIconId: Int = R.drawable.heart_dislike
}