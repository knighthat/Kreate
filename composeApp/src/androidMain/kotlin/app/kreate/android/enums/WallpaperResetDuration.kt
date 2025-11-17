package app.kreate.android.enums

import androidx.compose.runtime.Composable
import me.knighthat.enums.TextView

enum class WallpaperResetDuration(val ms: Long) : TextView {
    DISABLED(-1),
    INSTANT(0),
    S15( 15 * 1000),
    S30( 30 * 1000),
    M1( 60 * 1000),
    M5( 5 * 60 * 1000),
    M15(15 * 60 * 1000),
    M30( 30 * 60 * 1000),
    H1( 60 * 60 * 1000);

    override val text: String
        @Composable
        get() =  if(this.ms > 0)  (this.ms / 1000).toString() else  "Disabled"
}