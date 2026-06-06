package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.transition_animation_expand
import kreate.resources.generated.resources.transition_animation_fade
import kreate.resources.generated.resources.transition_animation_horizontal_slide
import kreate.resources.generated.resources.transition_animation_no_effect
import kreate.resources.generated.resources.transition_animation_vertical_slide
import kreate.resources.generated.resources.transition_animation_zoom
import org.jetbrains.compose.resources.StringResource

enum class TransitionEffect(
    override val textId: StringResource
): TextView {

    SlideVertical(Res.string.transition_animation_vertical_slide),

    SlideHorizontal(Res.string.transition_animation_horizontal_slide),

    Scale(Res.string.transition_animation_zoom),

    Fade(Res.string.transition_animation_fade),

    Expand(Res.string.transition_animation_expand),

    None(Res.string.transition_animation_no_effect);
}