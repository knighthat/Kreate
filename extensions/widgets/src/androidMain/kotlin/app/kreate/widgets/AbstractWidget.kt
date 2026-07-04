package app.kreate.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceComposable
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.unit.ColorProvider
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


internal abstract class AbstractWidget : GlanceAppWidget() {

    companion object {

        val thumbnailKey = stringPreferencesKey( "THUMBNAIL" )
        val isPLayingKey = booleanPreferencesKey( "IS_PLAYING" )
        val backgroundColorKey = longPreferencesKey( "BACKGROUND_COLOR" )
        val surfaceColorKey = longPreferencesKey( "SURFACE_COLOR" )
        val onSurfaceColorKey = longPreferencesKey( "ON_SURFACE_COLOR" )
    }

    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    @SuppressLint("RestrictedApi")      // Isn't supposed to be, but here we are
    @Composable
    @GlanceComposable
    protected fun currentColor( key: Preferences.Key<Long> ): ColorProvider? {
        val color = currentState( key )?.toULong()?.let( ::Color )
        return if( color != null ) ColorProvider(color) else null
    }

    @Composable
    @GlanceComposable
    fun Thumbnail( context: Context, size: Size ) {
        var bitmap: Bitmap? by remember { mutableStateOf(null) }

        if( bitmap == null ) {
            val background = currentColor( backgroundColorKey ) ?: GlanceTheme.colors.background

            // Fallback background while loading
            Box(
                modifier = GlanceModifier.fillMaxSize().background( background ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(app.kreate.resources.R.drawable.app_icon),
                    contentDescription = null
                )
            }
        } else {
            Image(
                provider = ImageProvider(bitmap!!),
                contentDescription = "Thumbnail",
                contentScale = ContentScale.Crop,       // Mimics CenterCrop behavior
                modifier = GlanceModifier.fillMaxSize()
            )
        }

        val url = currentState( thumbnailKey )
        LaunchedEffect( url ) {
            if( url == null ) {
                bitmap = null
                return@LaunchedEffect
            }

            withContext( Dispatchers.IO ) {
                val request = ImageRequest.Builder(context)
                    .data( url )
                    // Critical: Bitmaps used in RemoteViews cannot be Hardware Bitmaps
                    .allowHardware( false )
                    .size( size )
                    .build()

                val result = ImageLoader(context).execute( request )
                if( result is SuccessResult )
                    bitmap = result.image.toBitmap()
                else if( result is ErrorResult )
                    Logger.e( "Failed to load thumbnail as bitmap", result.throwable, this::class.java.simpleName )
            }
        }
    }
}