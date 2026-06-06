package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.animated_gradient
import kreate.resources.generated.resources.blurred_song_cover
import kreate.resources.generated.resources.color_palette
import kreate.resources.generated.resources.gradient_from_song_cover
import kreate.resources.generated.resources.gradient_from_system_theme
import kreate.resources.generated.resources.song_cover
import kreate.resources.generated.resources.system_theme
import org.jetbrains.compose.resources.StringResource

enum class PlayerBackgroundColors(
    override val textId: StringResource
): TextView {

    CoverColor(Res.string.song_cover),

    ThemeColor(Res.string.system_theme),

    CoverColorGradient(Res.string.gradient_from_song_cover),

    ThemeColorGradient(Res.string.gradient_from_system_theme),

    BlurredCoverColor(Res.string.blurred_song_cover),

    ColorPalette(Res.string.color_palette),

    AnimatedGradient(Res.string.animated_gradient);
}