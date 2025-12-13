package it.fast4x.rimusic.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import app.kreate.android.coil3.ImageFactory
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import me.knighthat.utils.Toaster
import timber.log.Timber

@UnstableApi
class CoilBitmapLoader(
    private val scope: CoroutineScope
) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(data, 0, data.size) ?: error("Could not decode image data")
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future {
            val imageLoader = ImageFactory.imageLoader
            val request = ImageFactory.requestBuilder( uri.toString() ) {
                bitmapConfig( Bitmap.Config.ARGB_8888 )
                allowHardware( false )
                size( ImageFactory.THUMBNAIL_SIZE )
            }
            val result = imageLoader.execute( request )

            return@future result.image?.toBitmap() ?: throw IllegalStateException( "failed to load bitmap from $uri" )
        }

    // Prioritize loadBitmap (uses coil3) to get image rather than
    // decoding image from Media3
    override fun loadBitmapFromMetadata( metadata: MediaMetadata ): ListenableFuture<Bitmap>? =
        if ( metadata.artworkUri != null )
            loadBitmap( metadata.artworkUri!! )
        else if ( metadata.artworkData != null )
            decodeBitmap( metadata.artworkData!! )
        else
            null
}
