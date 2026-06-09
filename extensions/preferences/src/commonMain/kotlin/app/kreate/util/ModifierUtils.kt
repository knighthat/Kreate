package app.kreate.util

import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.preferences.Preferences

/**
 * Apply marquee effect (scrolling text) if
 * size exceeds viewable area and only when
 * setting [Preferences.MARQUEE_TEXT_EFFECT] is **enabled**.
 *
 * @param iterations The number of times to repeat the animation.
 *  `Int.MAX_VALUE` will repeat forever, and 0 will disable animation.
 *
 * @see basicMarquee
 */
@Composable
fun Modifier.scrollingText( iterations: Int = Int.MAX_VALUE ): Modifier {
    val enabled by Preferences.MARQUEE_TEXT_EFFECT.collectAsStateWithLifecycle()

    return this.basicMarquee( if(enabled) iterations else 0 )
}