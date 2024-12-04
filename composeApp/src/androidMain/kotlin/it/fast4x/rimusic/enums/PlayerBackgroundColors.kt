package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class PlayerBackgroundColors(
    @field:StringRes override val textId: Int
): TextView {

    CoverColor( R.string.bg_colors_background_from_cover ),

    ThemeColor( R.string.bg_colors_background_from_theme ),

    CoverColorGradient( R.string.bg_colors_gradient_background_from_cover ),

    ThemeColorGradient( R.string.bg_colors_gradient_background_from_theme ),

    BlurredCoverColor( R.string.bg_colors_blurred_cover_background ),

    ColorPalette( R.string.colorpalette ),

    AnimatedGradient( R.string.animatedgradient );
}