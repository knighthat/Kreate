package me.knighthat.component.ui.screens


import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.fast4x.rimusic.utils.isLandscape

/**
 * A layout that presents differently based on the
 * orientation of the screen.
 *
 * ## Current version
 *
 * - In **portrait** mode, thumbnail is placed on top
 * with vertical gradient, then content will be placed
 * below it.
 * - In **landscape** mode, thumbnail is placed on the left
 * and content will be placed on the right
 */
@Composable
fun DynamicOrientationLayout(
    thumbnail: Painter,
    content: @Composable () -> Unit
) {
    if( isLandscape )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth( .5f )
            ) {
                val roundness by app.kreate.preferences.Preferences.THUMBNAIL_BORDER_RADIUS.collectAsStateWithLifecycle()
                Image(
                    painter = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize( .5f )
                        .aspectRatio( 1f )
                        .clip( roundness.shape )
                )
            }
            content()
        }
    else
        content()
}
