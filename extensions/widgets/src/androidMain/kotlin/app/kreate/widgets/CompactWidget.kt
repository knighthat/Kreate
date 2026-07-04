package app.kreate.widgets

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import app.kreate.widgets.action.PlayPauseButtonAction
import coil3.size.Size
import org.koin.core.component.KoinComponent


internal class CompactWidget : AbstractWidget(), KoinComponent {

    override suspend fun provideGlance( context: Context, id: GlanceId ) = provideContent {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd // Aligns children to bottom-right corner
        ) {
            Thumbnail( context, Size(300, 300) )

            Box(
                modifier = GlanceModifier.padding(16.dp)
            ) {
                val isPlaying = currentState( isPLayingKey ) ?: return@Box
                val surface = currentColor( surfaceColorKey ) ?: return@Box
                val onSurface = currentColor( onSurfaceColorKey ) ?: return@Box

                val icon = if( isPlaying ) R.drawable.pause else R.drawable.play_arrow
                CircleIconButton(
                    imageProvider = ImageProvider(icon),
                    contentDescription = "Action Button",
                    backgroundColor = surface,
                    contentColor = onSurface,
                    onClick = actionRunCallback<PlayPauseButtonAction>(
                        actionParametersOf( PlayPauseButtonAction.IS_PLAYING_KEY to isPlaying )
                    )
                )
            }
        }
    }
}