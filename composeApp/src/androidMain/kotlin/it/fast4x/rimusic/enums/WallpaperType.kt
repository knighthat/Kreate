package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class WallpaperType(
    @param:StringRes override val androidTextId: Int
): TextView {
    DISABLED( R.string.vt_disabled ),
    HOME( R.string.home ),
    LOCKSCREEN( R.string.word_lockscreen ),
    BOTH( R.string.both );

    override val text: String
        @Composable
        get() = stringResource( this.androidTextId )
}