package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class MusicAnimationType(
    @field:StringRes override val androidTextId: Int
): TextView {

    Disabled( R.string.vt_disabled ),

    Bars( R.string.music_animations_bars ),

    CrazyBars( R.string.music_animations_crazy_bars ),

    CrazyPoints( R.string.music_animations_crazy_points ),

    Bubbles( R.string.music_animations_bubbles );
}