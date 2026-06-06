package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.player_swipe_animation_carousel
import kreate.resources.generated.resources.player_swipe_animation_circular
import kreate.resources.generated.resources.transition_animation_fade
import kreate.resources.generated.resources.transition_animation_vertical_slide
import kreate.resources.generated.resources.transition_animation_zoom
import org.jetbrains.compose.resources.StringResource

enum class SwipeAnimationNoThumbnail(
    override val textId: StringResource
): TextView {

    Sliding(Res.string.transition_animation_vertical_slide),

    Fade(Res.string.transition_animation_fade),

    Scale(Res.string.transition_animation_zoom),

    Carousel(Res.string.player_swipe_animation_carousel),

    Circle(Res.string.player_swipe_animation_circular)
}