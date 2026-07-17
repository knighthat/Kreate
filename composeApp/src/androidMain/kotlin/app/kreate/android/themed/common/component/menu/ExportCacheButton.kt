package app.kreate.android.themed.common.component.menu

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.compose.R
import app.kreate.di.CacheType
import app.kreate.util.cleanPrefix
import app.kreate.utils.Toaster
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.SequenceInputStream
import java.net.URLConnection
import java.util.Collections


@OptIn(UnstableApi::class)
class ExportCacheButton : MenuButton<MediaItem>(), KoinComponent {

    private val cache by inject<Cache>(CacheType.CACHE)
    private val downloadCache by inject<Cache>(CacheType.DOWNLOAD)

    private var isCached: Boolean = false
    private var isDownloaded: Boolean = false
    private lateinit var songId: String
    private lateinit var launcher: ManagedActivityResultLauncher<String, Uri?>

    override val iconId: Int = R.drawable.export_outline
    override val tooltipMessageId: Int = R.string.info_export_cached_or_downloaded_song
    override val title: String
        @Composable
        get() {
            // FIXME: This is hacky, find a way to avoid relying on Composable function
            launcher = rememberLauncherForActivityResult(
                // Mimetype is determined at export time
                contract = ActivityResultContracts.CreateDocument("audio/*"),
                onResult = { it?.also( ::onExport ) }
            )

            return stringResource( R.string.export_cached )
        }

    private fun onExport( destination: Uri ) = CoroutineScope(Dispatchers.IO).launch {
        // To prevent OOM, we must avoid turning audio file into ByteArray.
        // To do this, we just have to convert all CacheSpans into Sequential streams
        // and write them directly to output file
        val inputStreams =
            (if( isCached ) cache else downloadCache)
                .getCachedSpans( songId )
                .mapNotNull { it.file?.inputStream() }
        // Chain them sequentially using an Enumeration
        val enumeration = Collections.enumeration(inputStreams)
        val combinedInputStream = SequenceInputStream(enumeration)
        // Stream everything into the destination Uri
        try {
            get<Context>().contentResolver.openOutputStream( destination )?.use { outputStream ->
                combinedInputStream.use { inputStream ->
                    // Kotlin's built-in extension streams data in 8KB chunks safely
                    inputStream.copyTo(outputStream)
                }
            }
        } catch( e: Exception ) {
            Logger.e( "", e )
            Toaster.e( R.string.error_failed_to_export_song )
        }
    }

    override fun onClick( menu: BottomMenu, item: MediaItem ) {
        songId = item.mediaId

        try {
            //<editor-fold desc="Final check to make sure song is cached or downloaded before exporting">
            val cacheMetadata = cache.getContentMetadata( songId )
            isCached = cache.isCached(
                songId,
                0,
                ContentMetadata.getContentLength( cacheMetadata )
            )
            val downloadMetadata = downloadCache.getContentMetadata( songId )
            isDownloaded = cache.isCached(
                songId,
                0,
                ContentMetadata.getContentLength( downloadMetadata )
            )

            if( !isCached && !isDownloaded ) {
                Toaster.i( R.string.song_must_be_cached_or_downloaded_to_export )
                return
            }
            //</editor-fold>
            //<editor-fold desc="Mimetype & extension">
            val mimetype =
                (if( isCached ) cache else downloadCache)
                    .getCachedSpans( songId )
                    // To prevent OOM, only load the first chunk,
                    // which contains all the headers of the file
                    .firstOrNull()
                    ?.let { it.file?.readBytes() }
                    ?.let { bytes ->
                        val inputStream = BufferedInputStream(ByteArrayInputStream(bytes))
                        val guessedMime = URLConnection.guessContentTypeFromStream(inputStream)

                        // Return the guess, or fall back to generic binary stream if it's completely unknown
                        guessedMime ?: "application/octet-stream"
                    }
            val extension =
                if( mimetype?.let { MimeTypeMap.getSingleton().hasMimeType(it) } == true )
                    MimeTypeMap.getSingleton().getExtensionFromMimeType( mimetype )!!
                else
                    "m4a"       // Fallback value, applicable for most (if not all) audio files on YTM
            //</editor-fold>

            //<editor-fold desc="Make file name">
            val filename = StringBuilder()
            item.mediaMetadata.artist?.toString()?.let( ::cleanPrefix )?.also {
                if( it.isNotBlank() )
                    filename.append( "$it - " )
            }
            item.mediaMetadata.title?.toString()?.let( ::cleanPrefix )?.also {
                if( it.isNotBlank() )
                    filename.append( it )
            }
            //</editor-fold>
            launcher.launch( "$filename.$extension" )
        } catch( e: Exception ) {
            Logger.e( "", e )
            Toaster.e( R.string.error_failed_to_export_song )
        }
    }
}