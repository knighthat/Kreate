package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class AnimatedGradient(
    @field:StringRes override val androidTextId: Int
): TextView {

    FluidThemeColorGradient( R.string.bg_colors_fluid_gradient_background_from_theme ),

    FluidCoverColorGradient( R.string.bg_colors_fluid_gradient_background_from_cover ),

    Linear( R.string.linear ),

    Mesh( R.string.mesh ),

    MesmerizingLens( R.string.mesmerizinglens ),

    GlossyGradients( R.string.glossygradient ),

    GradientFlow( R.string.gradientflow ),

    PurpleLiquid( R.string.purpleliquid ),

    InkFlow( R.string.inkflow ),

    OilFlow( R.string.oilflow ),

    IceReflection( R.string.icereflection ),

    Stage( R.string.stage ),

    GoldenMagma( R.string.goldenmagma ),

    BlackCherryCosmos( R.string.blackcherrycosmos ),

    Random( R.string.random );
}