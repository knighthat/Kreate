package app.kreate.coil3

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope.Companion.DefaultFilterQuality
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import app.kreate.AppIcon
import coil3.Image
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePainter.State
import coil3.disk.DiskCache
import coil3.request.ImageRequest
import coil3.transform.Transformation
import me.knighthat.kreate.composeapp.generated.resources.Res
import me.knighthat.kreate.composeapp.generated.resources.loader
import org.jetbrains.compose.resources.painterResource

interface ImageFactory {

    companion object: ImageFactory by ImageFactoryImpl()

    val thumbnailSize: Int

    /**
     * Wrapper of [coil3.compose.AsyncImage] with
     * [coil3.ImageLoader] and [coil3.disk.DiskCache] from [Provider]
     *
     * @param thumbnailUrl Url to get image
     * @param contentDescription Text used by accessibility services to describe what this image
     *  represents. This should always be provided unless this image is used for decorative purposes,
     *  and does not represent a meaningful action that a user can take.
     * @param modifier Modifier used to adjust the layout algorithm or draw decoration content.
     * @param placeholder A [Painter] that is displayed while the image is loading.
     * @param error A [Painter] that is displayed when the image request is unsuccessful.
     * @param fallback A [Painter] that is displayed when [thumbnailUrl] is `null`.
     * @param onLoading Called when the image request begins loading.
     * @param onSuccess Called when the image request completes successfully.
     * @param onError Called when the image request completes unsuccessfully.
     * @param alignment Optional alignment parameter used to place the [AsyncImagePainter] in the given
     *  bounds defined by the width and height.
     * @param contentScale Optional scale parameter used to determine the aspect ratio scaling to be
     *  used if the bounds are a different size from the intrinsic size of the [AsyncImagePainter].
     * @param alpha Optional opacity to be applied to the [AsyncImagePainter] when it is rendered
     *  onscreen.
     * @param colorFilter Optional [ColorFilter] to apply for the [AsyncImagePainter] when it is
     *  rendered onscreen.
     * @param filterQuality Sampling algorithm applied to a bitmap when it is scaled and drawn into the
     *  destination.
     * @param clipToBounds If true, clips the content to its bounds. Else, it will not be clipped.
     */
    @Composable
    @NonRestartableComposable
    fun AsyncImage(
        thumbnailUrl: String?,
        modifier: Modifier = Modifier,
        contentDescription: String? = null,
        placeholder: Painter? = painterResource( Res.drawable.loader ),
        error: Painter? = AppIcon.painter(),
        fallback: Painter? = error,
        onLoading: ((State.Loading) -> Unit)? = null,
        onSuccess: ((State.Success) -> Unit)? = null,
        onError: ((State.Error) -> Unit)? = null,
        alignment: Alignment = Alignment.Center,
        contentScale: ContentScale = ContentScale.Fit,
        alpha: Float = DefaultAlpha,
        colorFilter: ColorFilter? = null,
        filterQuality: FilterQuality = DefaultFilterQuality,
        clipToBounds: Boolean = true,
        transformations: List<Transformation> = emptyList()
    )

    /**
     * Wrapper of [coil3.compose.rememberAsyncImagePainter] with
     * [coil3.ImageLoader] and [coil3.disk.DiskCache] from [Provider]
     *
     * @param thumbnailUrl Url to get image
     * @param placeholder A [Painter] that is displayed while the image is loading.
     * @param error A [Painter] that is displayed when the image request is unsuccessful.
     * @param fallback A [Painter] that is displayed when [thumbnailUrl] is `null`.
     * @param onLoading Called when the image request begins loading.
     * @param onSuccess Called when the image request completes successfully.
     * @param onError Called when the image request completes unsuccessfully.
     * @param contentScale Used to determine the aspect ratio scaling to be used if the canvas bounds
     *  are a different size from the intrinsic size of the image loaded by [thumbnailUrl]. This should be set
     *  to the same value that's passed to [Image].
     * @param filterQuality Sampling algorithm applied to a bitmap when it is scaled and drawn into the
     *  destination.
     */
    @Composable
    @NonRestartableComposable
    fun rememberAsyncImagePainter(
        thumbnailUrl: String?,
        placeholder: Painter? = painterResource( Res.drawable.loader ),
        error: Painter? = AppIcon.painter(),
        fallback: Painter? = error,
        onLoading: ((State.Loading) -> Unit)? = null,
        onSuccess: ((State.Success) -> Unit)? = null,
        onError: ((State.Error) -> Unit)? = null,
        contentScale: ContentScale = ContentScale.Fit,
        filterQuality: FilterQuality = DefaultFilterQuality,
        transformations: List<Transformation> = emptyList()
    ): AsyncImagePainter

    fun requestBuilder(
        thumbnailUrl: String?,
        builder: ImageRequest.Builder.() -> Unit = {}
    ): ImageRequest

    interface Provider {

        val diskCache: DiskCache
        val imageLoader: ImageLoader

        fun requestBuilder(
            thumbnailUrl: String?,
            builder: ImageRequest.Builder.() -> Unit = {}
        ): ImageRequest
    }
}