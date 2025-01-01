package me.knighthat.component.ui.screens


import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImagePainter
import it.fast4x.rimusic.enums.ThumbnailRoundness
import it.fast4x.rimusic.utils.isLandscape
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.thumbnailRoundnessKey

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
    thumbnail: AsyncImagePainter,
    content: @Composable () -> Unit
) {
    if( isLandscape )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth( .5f )
            ) {
                val roundness by rememberPreference(
                    thumbnailRoundnessKey, ThumbnailRoundness.Heavy
                )
                Image(
                    painter = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize( .5f )
                        .aspectRatio( 1f )
                        .clip( roundness.shape() )
                )
            }
            content()
        }
    else
        content()
}
