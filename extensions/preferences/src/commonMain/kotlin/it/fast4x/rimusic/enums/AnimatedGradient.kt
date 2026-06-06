package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.animated_gradient_black_cherry_cosmo
import kreate.resources.generated.resources.animated_gradient_flow
import kreate.resources.generated.resources.animated_gradient_fluid_song_cover
import kreate.resources.generated.resources.animated_gradient_fluid_system_theme
import kreate.resources.generated.resources.animated_gradient_glossy
import kreate.resources.generated.resources.animated_gradient_golden_magma
import kreate.resources.generated.resources.animated_gradient_ice_reflection
import kreate.resources.generated.resources.animated_gradient_ink_flow
import kreate.resources.generated.resources.animated_gradient_linear
import kreate.resources.generated.resources.animated_gradient_mesh
import kreate.resources.generated.resources.animated_gradient_mesmerizing_lens
import kreate.resources.generated.resources.animated_gradient_oil_flow
import kreate.resources.generated.resources.animated_gradient_purple_liquid
import kreate.resources.generated.resources.animated_gradient_stage
import kreate.resources.generated.resources.word_random
import org.jetbrains.compose.resources.StringResource

enum class AnimatedGradient(
    override val textId: StringResource
): TextView {

    FluidThemeColorGradient(Res.string.animated_gradient_fluid_system_theme),

    FluidCoverColorGradient(Res.string.animated_gradient_fluid_song_cover),

    Linear(Res.string.animated_gradient_linear),

    Mesh(Res.string.animated_gradient_mesh),

    MesmerizingLens(Res.string.animated_gradient_mesmerizing_lens),

    GlossyGradients(Res.string.animated_gradient_glossy),

    GradientFlow(Res.string.animated_gradient_flow),

    PurpleLiquid(Res.string.animated_gradient_purple_liquid),

    InkFlow(Res.string.animated_gradient_ink_flow),

    OilFlow(Res.string.animated_gradient_oil_flow),

    IceReflection(Res.string.animated_gradient_ice_reflection),

    Stage(Res.string.animated_gradient_stage),

    GoldenMagma(Res.string.animated_gradient_golden_magma),

    BlackCherryCosmos(Res.string.animated_gradient_black_cherry_cosmo),

    Random(Res.string.word_random);
}