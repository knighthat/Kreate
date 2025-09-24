package app.kreate.android.themed.rimusic.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import app.kreate.android.Preferences
import app.kreate.android.coil3.ImageFactory

abstract class Visual {

    companion object {
        fun getShape( percent: Int ): Shape =
            when( percent ) {
                0 -> RectangleShape
                50 -> CircleShape
                else -> RoundedCornerShape(percent)
            }
    }

    protected abstract val thumbnailRoundnessPercent: Preferences.Int

    val thumbnailShape: Shape by derivedStateOf {
        getShape( thumbnailRoundnessPercent.value )
    }

    @Composable
    fun Thumbnail(
        thumbnailUrl: String?,
        contentScale: ContentScale,
        modifier: Modifier = Modifier
    ) =
        ImageFactory.AsyncImage(
            thumbnailUrl = thumbnailUrl,
            contentScale = contentScale,
            modifier = modifier.clip( thumbnailShape )
                               .fillMaxSize()
        )
}