package app.kreate.coil3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import app.kreate.getImageFactoryProvider
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.transform.Transformation

class ImageFactoryImpl: ImageFactory {

    // Delay this initialization because Preferences might not be initialized yet
    private val provider by lazy { getImageFactoryProvider() }

    // This is isn't for all devices, some devices need
    // bigger sizes so the image won't distort, but 900 (px)
    // is fit for majority of devices and the their storage sizes
    // isn't too big either.
    override val thumbnailSize: Int = 900

    @Composable
    @NonRestartableComposable
    override fun AsyncImage(
        thumbnailUrl: String?,
        modifier: Modifier,
        contentDescription: String?,
        placeholder: Painter?,
        error: Painter?,
        fallback: Painter?,
        onLoading: ((AsyncImagePainter.State.Loading) -> Unit)?,
        onSuccess: ((AsyncImagePainter.State.Success) -> Unit)?,
        onError: ((AsyncImagePainter.State.Error) -> Unit)?,
        alignment: Alignment,
        contentScale: ContentScale,
        alpha: Float,
        colorFilter: ColorFilter?,
        filterQuality: FilterQuality,
        clipToBounds: Boolean,
        transformations: List<Transformation>
    ) =
        coil3.compose.AsyncImage(
            model = requestBuilder( thumbnailUrl ){
                transformations( transformations )
            },
            contentDescription = contentDescription,
            imageLoader = provider.imageLoader,
            modifier = modifier,
            placeholder = placeholder,
            error = error,
            fallback = fallback,
            onLoading = onLoading,
            onSuccess = onSuccess,
            onError = onError,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
            filterQuality = filterQuality,
            clipToBounds = clipToBounds
        )

    @Composable
    @NonRestartableComposable
    override fun rememberAsyncImagePainter(
        thumbnailUrl: String?,
        placeholder: Painter?,
        error: Painter?,
        fallback: Painter?,
        onLoading: ((AsyncImagePainter.State.Loading) -> Unit)?,
        onSuccess: ((AsyncImagePainter.State.Success) -> Unit)?,
        onError: ((AsyncImagePainter.State.Error) -> Unit)?,
        contentScale: ContentScale,
        filterQuality: FilterQuality,
        transformations: List<Transformation>
    ) =
        coil3.compose.rememberAsyncImagePainter(
            model = requestBuilder( thumbnailUrl ){
                transformations( transformations )
            },
            imageLoader = provider.imageLoader,
            placeholder = placeholder,
            error = error,
            fallback = fallback,
            onLoading = onLoading,
            onSuccess = onSuccess,
            onError = onError,
            contentScale = contentScale,
            filterQuality = filterQuality
        )

    override fun requestBuilder(
        thumbnailUrl: String?,
        builder: ImageRequest.Builder.() -> Unit
    ): ImageRequest = provider.requestBuilder( thumbnailUrl, builder )
}