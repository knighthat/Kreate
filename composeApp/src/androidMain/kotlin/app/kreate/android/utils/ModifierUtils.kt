package app.kreate.android.utils

import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier
import app.kreate.preferences.Preferences

/**
 * Apply marquee effect (scrolling text) if
 * size exceeds viewable area and only when
 * setting [Preferences.MARQUEE_TEXT_EFFECT] is **enabled**.
 *
 * @param iterations The number of times to repeat the animation. `Int.MAX_VALUE` will repeat
 *   forever, and 0 will disable animation
 *
 * @see basicMarquee
 */
fun Modifier.scrollingText( iterations: Int = Int.MAX_VALUE ) =
    if( Preferences.MARQUEE_TEXT_EFFECT.value )
        this.basicMarquee( iterations = iterations )
    else
        this