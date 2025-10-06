package app.kreate.android.widget

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceComposable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
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
import androidx.media3.common.util.UnstableApi
import app.kreate.AppIcon
import app.kreate.android.R
import it.fast4x.rimusic.MainActivity
import it.fast4x.rimusic.cleanPrefix
import java.io.File

sealed class Widget: GlanceAppWidget() {

    val songTitleKey = stringPreferencesKey("songTitleKey")
    val songArtistKey = stringPreferencesKey("songArtistKey")
    val isPlayingKey = booleanPreferencesKey("isPlayingKey")
    var bitmapPath = stringPreferencesKey("thumbnailPathKey")

    private var onPlayPauseAction: () -> Unit = {}
    private var onPreviousAction: () -> Unit = {}
    private var onNextAction: () -> Unit = {}

    @Composable
    protected abstract fun Content( context: Context )

    @Composable
    @GlanceComposable
    protected fun Thumbnail( modifier: GlanceModifier ) {
        val bitmap = currentState( bitmapPath )?.let( BitmapFactory::decodeFile )
            ?: AppIcon.bitmap( LocalContext.current )
        Image(
            provider = ImageProvider( bitmap ),
            contentDescription = "cover",
            modifier = modifier.clickable( actionStartActivity<MainActivity>() )
        )
    }

    @Composable
    @GlanceComposable
    protected fun Controller() {
        val isPlaying = currentState( isPlayingKey ) ?: false

        Image(
            provider = ImageProvider( R.drawable.play_skip_back ),
            contentDescription = "back",
            modifier = GlanceModifier.clickable( onPreviousAction )
        )

        Image(
            provider = ImageProvider(
                if ( isPlaying ) R.drawable.pause else R.drawable.play
            ),
            contentDescription = "play/pause",
            modifier = GlanceModifier.padding(horizontal = 20.dp)
                                     .clickable( onPlayPauseAction )
        )

        Image(
            provider = ImageProvider( R.drawable.play_skip_forward ),
            contentDescription = "next",
            modifier = GlanceModifier.clickable( onNextAction )
        )
    }

    @UnstableApi
    suspend fun update(
        context: Context,
        actions: Triple<() -> Unit, () -> Unit, () -> Unit>,
        status: Triple<String, String, Boolean>,
        bitmapFile: File
    ) {
        val glanceId =
            GlanceAppWidgetManager(context).getGlanceIds(this::class.java).firstOrNull() ?: return

        updateAppWidgetState(context, glanceId) {
            it[songTitleKey] = cleanPrefix( status.first )
            it[songArtistKey] = cleanPrefix( status.second )
            it[isPlayingKey] = status.third

            if( it[bitmapPath].isNullOrEmpty() )
                it[bitmapPath] = bitmapFile.absolutePath
        }

        onPlayPauseAction = actions.first
        onPreviousAction = actions.second
        onNextAction = actions.third

        update(context, glanceId)
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
                    ) { Controller() }
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
                ) { Controller() }

                Thumbnail( GlanceModifier.padding( horizontal = 5.dp) )
            }
        }
    }
}