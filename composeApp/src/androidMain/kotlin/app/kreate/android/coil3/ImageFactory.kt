package app.kreate.android.coil3

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.util.fastCoerceAtLeast
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.drawable.AppIcon
import app.kreate.android.service.NetworkService
import coil3.Image
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.AsyncImagePainter.State
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.error
import coil3.request.fallback
import coil3.request.placeholder
import coil3.request.transformations
import coil3.toBitmap
import coil3.transform.Transformation
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.thumbnail
import it.fast4x.rimusic.ui.styling.LocalAppearance
import kotlinx.coroutines.Dispatchers
import org.jetbrains.annotations.Contract
import kotlin.contracts.ExperimentalContracts

object ImageFactory {

    // This is isn't for all devices, some devices need
    // bigger sizes so the image won't distort, but 900 (px)
    // is fit for majority of devices and the their storage sizes
    // isn't too big either.
    const val THUMBNAIL_SIZE = 900;
    val defaultModifier: Modifier
        @Composable
        get() {
            val appearance = LocalAppearance.current

            return Modifier.clip( appearance.thumbnailShape )
                           .fillMaxSize()
        }

    lateinit var diskCache: DiskCache
    lateinit var imageLoader: ImageLoader
    lateinit var errorPlaceholder: Bitmap

    fun init( context: Context ) {
        errorPlaceholder = AppIcon.bitmap( context, THUMBNAIL_SIZE )

        val cacheSize by Preferences.IMAGE_CACHE_SIZE
        if( !::diskCache.isInitialized )
            DiskCache.Builder()
                     .directory(
                         context.cacheDir.resolve( "coil3" )
                     )
                     // DiskCache.Builder doesn't allow 0 byte.
                     // `1` is there for the sake of creating this,
                     // but won't be added to ImageLoader
                     .maxSizeBytes( cacheSize.fastCoerceAtLeast( 1L ) )
                     .cleanupCoroutineContext( Dispatchers.IO )
                     .build()
                     .also { diskCache = it }

        if( !::imageLoader.isInitialized )
            // TODO: Add a toggle in setting that let user enable network caching
            // This feature will set an expiration date on cache, forcing
            // user to "re-fetch" the image data again after a period of time.
            // This will potentially double the storage.
            ImageLoader.Builder( context )
                       .crossfade( true )
                       .diskCachePolicy( CachePolicy.ENABLED )
                       .error(
                           errorPlaceholder.asImage()
                       )
                       .components {
                           add(
                               KtorNetworkFetcherFactory(NetworkService.client)
                           )
                       }
                       .apply {
                           if( cacheSize > 0 )
                               diskCache( diskCache )
                       }
                       .build()
                       .also { imageLoader = it }
    }

    fun requestBuilder(
        thumbnailUrl: String?,
        builder: ImageRequest.Builder.() -> Unit = {}
    ) =
        /*
         * TODO: Make a simple system to detect network speed and/or
         * TODO: data saver that automatically lower the quality to
         * TODO: reduce loading time and to preserve data usage.
         */
        ImageRequest.Builder( appContext() )
                    .data( thumbnailUrl.thumbnail( THUMBNAIL_SIZE ) )
                    .diskCacheKey( thumbnailUrl )
                    .placeholder( R.drawable.loader )
                    .error( R.drawable.noimage )
                    .fallback( R.drawable.image )
                    .apply( builder )
                    .build()

    @Composable
    fun AsyncImage(
        thumbnailUrl: String?,
        modifier: Modifier = defaultModifier,
        contentDescription: String? = null,
        contentScale: ContentScale = ContentScale.FillBounds,
        transformations: List<Transformation> = emptyList()
    ) =
        coil3.compose.AsyncImage(
            model = requestBuilder( thumbnailUrl ){
                transformations( transformations )
            },
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )

    @Composable
    fun rememberAsyncImagePainter(
        thumbnailUrl: String?,
        contentScale: ContentScale = ContentScale.FillBounds,
        transformations: List<Transformation> = emptyList(),
        onLoading: ((State.Loading) -> Unit)? = null,
        onSuccess: ((State.Success) -> Unit)? = null,
        onError: ((State.Error) -> Unit)? = null
    ) =
        coil3.compose.rememberAsyncImagePainter(
            model = requestBuilder( thumbnailUrl ) {
                transformations( transformations )
            },
            imageLoader = imageLoader,
            contentScale = contentScale,
            onLoading = onLoading,
            onSuccess = onSuccess,
            onError = onError
        )

    @OptIn(ExperimentalContracts::class)
    @Contract("null,_->null")
    suspend fun bitmap(
        thumbnailUrl: String,
        toBitmap: Image.() -> Bitmap = Image::toBitmap,
        requestBuilder: ImageRequest.Builder.() -> Unit = {}
    ) = runCatching {
        requestBuilder( thumbnailUrl, requestBuilder )
            .let( imageLoader::enqueue )
            .job
            .await()
            .image!!
            .toBitmap()
    }
}