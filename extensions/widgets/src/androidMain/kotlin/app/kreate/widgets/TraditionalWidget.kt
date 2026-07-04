package app.kreate.widgets

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import coil3.size.Size


internal class TraditionalWidget : AbstractWidget() {

    companion object {

        val progressKey = floatPreferencesKey( "PROGRESS" )
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId ) = provideContent {
        val background = currentColor( backgroundColorKey ) ?: return@provideContent
        val surface = currentColor( surfaceColorKey ) ?: return@provideContent
        val onSurface = currentColor( onSurfaceColorKey ) ?: return@provideContent

        Column(
            modifier = GlanceModifier.fillMaxSize()
                                     .padding( 16.dp )
                                     .background( background ),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- TOP SECTION: Song Information ---
            Row(
                modifier = GlanceModifier.fillMaxWidth()
                                         .defaultWeight()
                                         .padding( horizontal = 16.dp ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Song Thumbnail (Left Side)
                Thumbnail( context, Size(64, 64), GlanceModifier .size(64.dp) )

                Spacer( GlanceModifier.width(12.dp) )

                // Title & Artist Text (Right Side, Aligned Vertically)
                Column(
                    modifier = GlanceModifier.fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val title = currentState( titleKey ).orEmpty()
                    Text(
                        text = title,
                        style = TextStyle(
                            color = surface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold // Title is bold
                        ),
                        maxLines = 1
                    )

                    Spacer( GlanceModifier.height(4.dp) )

                    val artists = currentState( artistsKey ).orEmpty()
                    Text(
                        text = artists,
                        style = TextStyle(
                            color = onSurface,
                            fontSize = 14.sp
                        )
                    )
                }
            }

            val currentProgress = currentState( progressKey ) ?: .0f
            LinearProgressIndicator(
                progress = currentProgress,
                modifier = GlanceModifier.fillMaxWidth().height( 6.dp ),
                color = onSurface,
                backgroundColor = surface
            )

            Spacer( GlanceModifier.height(24.dp) )

            // --- BOTTOM SECTION: Media Control Buttons ---
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkipPreviousButton(
                    backgroundColor = null,
                    contentColor = onSurface
                )

                Spacer( GlanceModifier.width(16.dp) )

                PlayPauseButton(
                    contentColor = surface,
                    backgroundColor = null
                )

                Spacer( GlanceModifier.width(16.dp) )

                SkipNextButton(
                    backgroundColor = null,
                    contentColor = onSurface
                )
            }
        }
    }
}