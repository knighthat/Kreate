package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class MusicAnimationType(
    @field:StringRes override val textId: Int
): TextView {

    Disabled( R.string.vt_disabled ),

    Bars( R.string.music_animations_bars ),

    CrazyBars( R.string.music_animations_crazy_bars ),

    CrazyPoints( R.string.music_animations_crazy_points ),

    Bubbles( R.string.music_animations_bubbles );
}