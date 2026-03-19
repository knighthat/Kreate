package app.kreate.android.service.player

import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_LOCK
import android.app.WallpaperManager.FLAG_SYSTEM
import android.content.Context
import android.graphics.Rect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.utils.centerCropBitmap
import app.kreate.android.utils.centerCropToMatchScreenSize
import co.touchlab.kermit.Logger
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.error
import coil3.request.fallback
import coil3.request.placeholder
import coil3.toBitmap
import it.fast4x.rimusic.utils.isAtLeastAndroid7
import it.fast4x.rimusic.utils.thumbnail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.utils.Toaster


class LiveWallpaperEngine(private val context: Context) : Player.Listener  {

    companion object {
        const val ORIGINAL_WALLPAPER_FILENAME = "original_wallpaper"
    }

    private val logger = Logger.withTag("LiveWallpaper")
    private val manager = WallpaperManager.getInstance(context)
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val setting by Preferences.LIVE_WALLPAPER

    private var setWallpaperJob: Job? = null
    private var restoreWallpaperJob: Job? = null
    private var currentMediaItem: MediaItem? = null

    private suspend fun fetchImage(
        data: Any?,
        key: String?,
        memoryCachePolicy: CachePolicy = CachePolicy.ENABLED
    ): ImageResult {
        val request = ImageRequest.Builder(context)
            .data( data )
            .diskCacheKey( key )
            .memoryCachePolicy( memoryCachePolicy )
            .placeholder( R.drawable.loader )
            .error( R.drawable.noimage )
            .fallback( R.drawable.image )
            .allowHardware( false )
            .build()

        return withContext( Dispatchers.IO ) {
            context.imageLoader.execute( request )
        }
    }

    private suspend fun update( url: String ) {
        logger.v { "Updating wallpaper to \"$url\"" }

        val result = fetchImage( url.thumbnail(ImageFactory.THUMBNAIL_SIZE), url )
        if( result is SuccessResult ) {
            val bitmap = result.image.toBitmap()
            val rect = with( bitmap ) {
                centerCropToMatchScreenSize( width, height )
            }

            if( isAtLeastAndroid7 )
                manager.setBitmap( bitmap, rect, true, setting )
            else
                manager.setBitmap( centerCropBitmap(bitmap, rect) )
        } else if( result is ErrorResult ) {
            logger.e( "", result.throwable )
            Toaster.e( R.string.error_failed_to_set_wallpaper )
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun restoreModern( which: Int ) {
        val filename = "${ORIGINAL_WALLPAPER_FILENAME}_$which"
        val file = context.filesDir.resolve( filename )
        val result = fetchImage( file.absolutePath, null, CachePolicy.DISABLED )
        if( result is ErrorResult )
            throw result.throwable

        val bitmap = (result as SuccessResult).image.toBitmap()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        manager.setBitmap( bitmap, rect, true, which )
    }

    suspend fun restore() {
        try {
            if( !isAtLeastAndroid7 ) {
                val file = context.filesDir.resolve( ORIGINAL_WALLPAPER_FILENAME )
                val result = fetchImage( file.absolutePath, null, CachePolicy.DISABLED )
                if( result is ErrorResult )
                    throw result.throwable

                val bitmap = (result as SuccessResult).image.toBitmap()
                manager.setBitmap( bitmap )
            } else {
                // Check if first bit is 1
                if( setting and FLAG_SYSTEM == 1 )
                    restoreModern( FLAG_SYSTEM )
                // Check if second bit is 1
                if( setting and FLAG_LOCK == 2 )
                    restoreModern( FLAG_LOCK )
            }
        } catch( err: Exception ) {
            logger.e( "", err )

            if( err !is java.io.FileNotFoundException )
                Toaster.w(
                    when( setting ) {
                        1       -> R.string.warning_failed_to_restore_original_home_screen_wallpaper
                        2       -> R.string.warning_failed_to_restore_original_lock_screen_wallpaper
                        else    -> R.string.warning_failed_to_restore_original_wallpaper
                    }
                )

            if( isAtLeastAndroid7 )
                manager.clear( setting )
            else
                manager.clear()
        }
    }

    /**
     * Cancel all running jobs and release resources.
     *
     * Once released, it can no longer be used.
     * New instance must be created as a replacement.
     */
    fun release() {
        coroutineScope.cancel()
    }

    /*
            Player listener
     */

    override fun onMediaItemTransition( mediaItem: MediaItem?, reason: Int ) {
        // Don't update when song on repeat
        if( reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT )
            currentMediaItem = mediaItem
    }

    override fun onIsPlayingChanged( isPlaying: Boolean ) {
        // Cancel all jobs
        setWallpaperJob?.cancel()
        setWallpaperJob = null
        restoreWallpaperJob?.cancel()
        restoreWallpaperJob = null

        if( isPlaying ) {
            val artworkUrl = currentMediaItem?.mediaMetadata?.artworkUri?.toString()
            if( !artworkUrl.isNullOrBlank() )
                setWallpaperJob = coroutineScope.launch {
                    update( artworkUrl )
                }
        } else
            restoreWallpaperJob = coroutineScope.launch {
                val setting by Preferences.LIVE_WALLPAPER_RESET_DURATION
                delay( setting )

                restore()
            }
    }
}