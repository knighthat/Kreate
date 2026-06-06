package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.now_playling_animation_bars
import kreate.resources.generated.resources.now_playling_animation_bubbles
import kreate.resources.generated.resources.now_playling_animation_crazy_points
import kreate.resources.generated.resources.now_playlist_animation_crazy_bars
import kreate.resources.generated.resources.word_disabled
import org.jetbrains.compose.resources.StringResource

enum class MusicAnimationType(
    override val textId: StringResource
): TextView {

    Disabled(Res.string.word_disabled),

    Bars(Res.string.now_playling_animation_bars),

    CrazyBars(Res.string.now_playlist_animation_crazy_bars),

    CrazyPoints(Res.string.now_playling_animation_crazy_points),

    Bubbles(Res.string.now_playling_animation_bubbles);
}