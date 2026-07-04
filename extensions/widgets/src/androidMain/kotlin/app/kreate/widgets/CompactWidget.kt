package app.kreate.widgets

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import coil3.size.Size
import org.koin.core.component.KoinComponent


internal class CompactWidget : AbstractWidget(), KoinComponent {

    override suspend fun provideGlance( context: Context, id: GlanceId ) = provideContent {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd // Aligns children to bottom-right corner
        ) {
            Thumbnail( context, Size(300, 300), GlanceModifier.fillMaxSize() )

            Box(
                modifier = GlanceModifier.padding(16.dp)
            ) {
                val surface = currentColor( surfaceColorKey ) ?: return@Box
                val onSurface = currentColor( onSurfaceColorKey ) ?: return@Box

                PlayPauseButton( surface, onSurface )
            }
        }
    }
}