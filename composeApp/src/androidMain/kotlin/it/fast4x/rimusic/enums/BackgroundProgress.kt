package it.fast4x.rimusic.enums;

import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class BackgroundProgress(
    @field:StringRes override val androidTextId: Int
): TextView {

    Player( R.string.player ),

    MiniPlayer( R.string.minimized_player ),

    Both( R.string.both ),

    Disabled( R.string.vt_disabled );
}
