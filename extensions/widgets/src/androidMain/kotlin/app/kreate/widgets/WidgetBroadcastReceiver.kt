package app.kreate.widgets

import android.annotation.SuppressLint
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
        const val ACTION_UPDATE_PROGRESS = "app.kreate.widgets.actions.UPDATE_PROGRESS"

        const val EXTRA_SONG_STATE = "app.kreate.widgets.extras.EXTRA_SONG_STATE"
        const val EXTRA_COLOR_STATE = "app.kreate.widgets.extras.EXTRA_COLOR_STATE"
        const val EXTRA_IS_PLAYING = "app.kreate.widgets.extras.EXTRA_IS_PLAYING"
        const val EXTRA_PROGRESS = "app.kreate.widgets.extras.PROGRESS"
        const val EXTRA_DURATION = "app.kreate.widgets.extras.DURATION"
    }

    private val logger = Logger.withTag( "WidgetBroadcastReceiver" )
    private val widgetClasses = listOf(CompactWidget::class.java, TraditionalWidget::class.java)
    private val progressWidgets = listOf(TraditionalWidget::class.java)

    @OptIn(UnstableApi::class)
    private fun Intent.extractSongState(): MediaMetadata? {
        val bundle = getBundleExtra( EXTRA_SONG_STATE ) ?: return null
        return runCatching {
            MediaMetadata.fromBundle( bundle, MediaLibraryInfo.INTERFACE_VERSION )
        }.getOrNull()
    }

    private fun Intent.extractColorState(): WidgetColorState? {
        val bundle = getBundleExtra( EXTRA_COLOR_STATE ) ?: return null
        return runCatching { WidgetColorState.fromBundle(bundle) }.getOrNull()
    }

    private fun Intent.extractPlayingState(): Boolean? =
        if( hasExtra(EXTRA_IS_PLAYING) )
            getBooleanExtra( EXTRA_IS_PLAYING, false )
        else
            null

    private suspend fun syncWidgets(
        context: Context,
        manager: GlanceAppWidgetManager,
        state: MediaMetadata?,
        colors: WidgetColorState?,
        isPlaying: Boolean?
    ) {
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
                                            this[AbstractWidget.thumbnailKey] = state.artworkUri?.toString().orEmpty()
                                            this[AbstractWidget.titleKey] = state.title?.toString().orEmpty()
                                            this[AbstractWidget.artistsKey] = state.artist?.toString().orEmpty()
                                        }

                                        if( colors != null ) {
                                            this[AbstractWidget.backgroundColorKey] = colors.background
                                            this[AbstractWidget.surfaceColorKey] = colors.surface
                                            this[AbstractWidget.onSurfaceColorKey] = colors.onSurface
                                        }

                                        if( isPlaying != null )
                                            this[AbstractWidget.isPLayingKey] = isPlaying
                                    }
                           }
                       )
                       // Recompose
                       widgetInstance.update(context, id)
                   }
        }
    }

    @SuppressLint("DefaultLocale")
    private suspend fun updateWidgetProgress(
        context: Context,
        manager: GlanceAppWidgetManager,
        progress: Float
    ) {
        for( clazz in progressWidgets ) {
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
                                        this[TraditionalWidget.progressKey] = progress
                                        logger.v { "New progress: ${String.format("%.2f", progress)}" }
                                    }
                           }
                       )
                       // Recompose
                       widgetInstance.update(context, id)
                   }
        }
    }

    override fun onReceive( context: Context, intent: Intent ) {
        val action = intent.action ?: return
        logger.d { "Received action: $action" }

        get<CoroutineScope>().launch {
            val manager = GlanceAppWidgetManager(context)

            when( action ) {
                ACTION_SYNC -> {
                    val state = intent.extractSongState()
                    logger.d { "State: $state" }
                    val colors = intent.extractColorState()
                    logger.d { "Colors: $colors" }
                    val isPlaying = intent.extractPlayingState()
                    logger.d { "Is playing: $isPlaying" }

                    syncWidgets( context, manager, state, colors, isPlaying )
                }

                ACTION_UPDATE_PROGRESS -> {
                    val progress = intent.getLongExtra( EXTRA_PROGRESS, 0 )
                    val duration = intent.getLongExtra( EXTRA_DURATION, 0 )
                    logger.d { "Progress: $progress, duration: $duration" }

                    updateWidgetProgress( context, manager, progress.toFloat() / duration.toFloat() )
                }
            }
        }
    }
}