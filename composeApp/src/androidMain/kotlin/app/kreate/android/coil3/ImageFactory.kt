package app.kreate.android.coil3

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import app.kreate.android.R
import app.kreate.util.thumbnail
import coil3.Image
import coil3.compose.AsyncImagePainter.State
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.error
import coil3.request.fallback
import coil3.request.placeholder
import coil3.request.transformations
import coil3.toBitmap
import coil3.transform.Transformation
import it.fast4x.rimusic.ui.styling.LocalAppearance
import org.jetbrains.annotations.Contract
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.contracts.ExperimentalContracts

object ImageFactory : KoinComponent {

    private val context: Context by inject()

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

    fun requestBuilder(
        thumbnailUrl: String?,
        builder: ImageRequest.Builder.() -> Unit = {}
    ) =
        /*
         * TODO: Make a simple system to detect network speed and/or
         * TODO: data saver that automatically lower the quality to
         * TODO: reduce loading time and to preserve data usage.
         */
        ImageRequest.Builder( context )
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
            .let( context.imageLoader::enqueue )
            .job
            .await()
            .image!!
            .toBitmap()
    }
}