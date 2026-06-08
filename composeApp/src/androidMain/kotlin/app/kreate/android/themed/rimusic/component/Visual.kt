package app.kreate.android.themed.rimusic.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.coil3.ImageFactory
import app.kreate.preferences.Preferences

abstract class Visual {

    companion object {
        fun getShape( percent: Int ): Shape =
            when( percent ) {
                0 -> RectangleShape
                50 -> CircleShape
                else -> RoundedCornerShape(percent)
            }
    }

    protected abstract val thumbnailRoundnessPercent: Preferences.IntPref

    abstract fun thumbnailSize(): DpSize

    @Composable
    fun Thumbnail(
        url: String?,
        contentScale: ContentScale,
        modifier: Modifier = Modifier,
        showThumbnail: Boolean = true,
        sizeDp: DpSize = thumbnailSize(),
        contentAlignment: Alignment = Alignment.TopStart,
        overlay: @Composable BoxScope.() -> Unit = {}
    ) =
        Box(
            contentAlignment = contentAlignment,
            modifier = modifier.requiredSize( sizeDp )
        ) {
            val percent by thumbnailRoundnessPercent.collectAsStateWithLifecycle()

            if( showThumbnail )
                ImageFactory.AsyncImage(
                    thumbnailUrl = url,
                    contentScale = contentScale,
                    modifier = Modifier.clip( getShape(percent) )
                                       .fillMaxSize()
                )

            overlay()
        }
}