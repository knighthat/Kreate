package app.kreate.android.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceComposable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import app.kreate.android.drawable.AppIcon
import app.kreate.di.THUMBNAIL_SIZE
import app.kreate.util.thumbnail
import co.touchlab.kermit.Logger
import coil3.asImage
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.placeholder
import coil3.toBitmap
import it.fast4x.rimusic.MainActivity
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.knighthat.utils.Toaster
import org.jetbrains.annotations.Contract


sealed class Widget: GlanceAppWidget() {

    val songTitleKey = stringPreferencesKey("songTitleKey")
    val songArtistKey = stringPreferencesKey("songArtistKey")
    val isPlayingKey = booleanPreferencesKey("isPlayingKey")

    private var bitmap: Bitmap? by mutableStateOf(null)

    @Composable
    protected abstract fun Content( context: Context )

    @Composable
    @GlanceComposable
    protected fun Thumbnail( modifier: GlanceModifier ) {
        val bitmap = bitmap ?: return
        Image(
            provider = ImageProvider( bitmap ),
            contentDescription = "cover",
            modifier = modifier.clickable( actionStartActivity<MainActivity>() )
        )
    }

    @Composable
    @GlanceComposable
    protected fun Controller( context: Context ) {
        val isPlaying = currentState( isPlayingKey ) ?: false

        Image(
            provider = ImageProvider( R.drawable.play_skip_back ),
            contentDescription = "back",
            modifier = GlanceModifier.clickable(
                actionStartService(
                    Intent(context, PlayerServiceModern::class.java).apply {
                        action = PlayerServiceModern.PLAYER_ACTION_PREVIOUS
                    }
                )
            )
        )

        Image(
            provider = ImageProvider(
                if ( isPlaying ) R.drawable.pause else R.drawable.play
            ),
            contentDescription = "play/pause",
            modifier = GlanceModifier.padding(horizontal = 20.dp).clickable(
                actionStartService(
                    Intent(context, PlayerServiceModern::class.java).apply {
                        action = if( isPlaying ) PlayerServiceModern.PLAYER_ACTION_PAUSE else PlayerServiceModern.PLAYER_ACTION_PLAY
                    }
                )
            )
        )

        Image(
            provider = ImageProvider( R.drawable.play_skip_forward ),
            contentDescription = "next",
            modifier = GlanceModifier.clickable(
                actionStartService(
                    Intent(context, PlayerServiceModern::class.java).apply {
                        action = PlayerServiceModern.PLAYER_ACTION_NEXT
                    }
                )
            )
        )
    }

    @Contract("_,null->null")
    private suspend fun getThumbnail( context: Context, artworkUri: String? ): Bitmap? {
        if( artworkUri == null ) return null

        val request = ImageRequest.Builder(context)
            .data( artworkUri.thumbnail(THUMBNAIL_SIZE) )
            .diskCacheKey( artworkUri )
            .placeholder( R.drawable.loader )
            .fallback {
                AppIcon.bitmap(context).asImage()
            }
            .error {
                AppIcon.bitmap(context).asImage()
            }
            .build()
        val result = withContext( Dispatchers.IO ) {
            context.imageLoader.execute( request )
        }

        return if( result is ErrorResult ) {
            Logger.e( "", result.throwable, this::class.java.simpleName )
            Toaster.e( R.string.error_failed_to_update_widget )
            null
        } else
            (result as SuccessResult).image.toBitmap()
    }

    @UnstableApi
    suspend fun update(
        context: Context,
        isPlaying: Boolean?,
        metadata: MediaMetadata?
    ) {
        val appContext = context.applicationContext
        val artworkUri = metadata?.artworkUri?.toString()
        bitmap = getThumbnail( context, artworkUri )
        val glanceId = GlanceAppWidgetManager(appContext)
            .getGlanceIds(this::class.java)
            .firstOrNull() ?: return

        updateAppWidgetState(appContext, glanceId) {
            it[songTitleKey] = metadata?.title.toString()
            it[songArtistKey] = metadata?.artist.toString()
            if( isPlaying != null )
                it[isPlayingKey] = isPlaying
        }

        update(appContext, glanceId)

        Logger.d( tag = this::class.java.simpleName ) { "Widget updated" }
    }

    override suspend fun provideGlance( context: Context, id: GlanceId ) {
        provideContent {
            GlanceTheme { Content( context ) }
        }
    }

    data object Horizontal: Widget() {

        @Composable
        override fun Content(context: Context) {
            Row(
                modifier = GlanceModifier.fillMaxWidth()
                                         .background( GlanceTheme.colors.widgetBackground )
                                         .padding( 4.dp ),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.Start
            ) {
                Thumbnail( GlanceModifier.padding( start = 5.dp, end = 20.dp ).size( 120.dp ) )

                Column(
                    modifier = GlanceModifier.fillMaxWidth()
                                             .padding( vertical = 12.dp ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text( currentState( songTitleKey ).orEmpty() )
                    Text( currentState( songArtistKey ).orEmpty() )

                    Row(
                        modifier = GlanceModifier.padding( vertical = 12.dp ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) { Controller( context ) }
                }
            }
        }
    }

    data object Vertical: Widget() {

        @Composable
        override fun Content(context: Context) {
            Column(
                modifier = GlanceModifier.fillMaxWidth()
                                         .background( GlanceTheme.colors.widgetBackground )
                                         .padding( 4.dp ),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text( currentState( songTitleKey ).orEmpty() )
                Text( currentState( songArtistKey ).orEmpty() )

                Row(
                    modifier = GlanceModifier.fillMaxWidth()
                                             .background( GlanceTheme.colors.widgetBackground )
                                             .padding( vertical = 12.dp ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { Controller( context ) }

                Thumbnail( GlanceModifier.padding( horizontal = 5.dp) )
            }
        }
    }
}