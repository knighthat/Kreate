package app.kreate.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import app.kreate.widgets.state.WidgetColorState
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get


class WidgetBroadcastReceiver : BroadcastReceiver(), KoinComponent {

    companion object {

        const val ACTION_SYNC = "app.kreate.widgets.actions.SYNC"

        const val EXTRA_SONG_STATE = "app.kreate.widgets.extras.EXTRA_SONG_STATE"
        const val EXTRA_COLOR_STATE = "app.kreate.widgets.extras.EXTRA_COLOR_STATE"
        const val EXTRA_IS_PLAYING = "app.kreate.widgets.extras.EXTRA_IS_PLAYING"
    }

    private val logger = Logger.withTag( "WidgetBroadcastReceiver" )
    private val widgetClasses = listOf(CompactWidget::class.java)

    @OptIn(UnstableApi::class)
    override fun onReceive( context: Context, intent: Intent ) {
        val action = intent.action ?: return
        val state = intent.getBundleExtra( EXTRA_SONG_STATE )
        val color = intent.getBundleExtra( EXTRA_COLOR_STATE )
        val isPlaying = if( intent.hasExtra(EXTRA_IS_PLAYING) )
            intent.getBooleanExtra( EXTRA_IS_PLAYING, false )
        else
            null
        logger.d { "Received action: $action, state: $state, color: $color, is playing: $isPlaying" }

        // Don't go further if action isn't supported
        if( action != ACTION_SYNC ) return

        get<CoroutineScope>().launch {
            val manager = GlanceAppWidgetManager(context)

            for( clazz in widgetClasses ) {
                manager.getGlanceIds( clazz )
                       .forEach { id ->
                           logger.v { "Updating state of $id" }

                           val widgetInstance = clazz.getDeclaredConstructor().newInstance()

                           updateAppWidgetState(
                               context = context,
                               definition = widgetInstance.stateDefinition,
                               glanceId = id,
                               updateState = { prefs ->
                                   prefs.toMutablePreferences()
                                        .apply {
                                            if( state != null ) {
                                                val state = MediaMetadata.fromBundle(state, MediaLibraryInfo.INTERFACE_VERSION)
                                                val url = state.artworkUri?.toString()
                                                if( url != null ) {
                                                    this[AbstractWidget.thumbnailKey] = url
                                                    logger.v { "Updated thumbnail to: $url" }
                                                }
                                            }

                                            if( color != null ) {
                                                // Don't crash if there's an error
                                                runCatching {
                                                    WidgetColorState.fromBundle( color )
                                                }.onSuccess { color ->
                                                    this[AbstractWidget.backgroundColorKey] = color.background
                                                    this[AbstractWidget.surfaceColorKey] = color.surface
                                                    this[AbstractWidget.onSurfaceColorKey] = color.onSurface
                                                    logger.v { "Updated colors to: $color" }
                                                }
                                            }

                                            if( isPlaying != null ) {
                                                this[AbstractWidget.isPLayingKey] = isPlaying
                                                logger.v { "Updated playing state to: $isPlaying" }
                                            }
                                        }
                               }
                           )
                           // Recompose
                           widgetInstance.update(context, id)
                       }
            }
        }
    }
}