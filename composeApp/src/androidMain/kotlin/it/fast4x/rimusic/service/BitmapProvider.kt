package it.fast4x.rimusic.service


import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.applyCanvas
import app.kreate.android.coil3.ImageFactory
import co.touchlab.kermit.Logger
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.allowHardware
import coil3.toBitmap
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.utils.thumbnail
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

//context(Context)
class BitmapProvider(
    private val bitmapSize: Int,
    private val colorProvider: (isSystemInDarkMode: Boolean) -> Int
) : KoinComponent {

    private val context: Context by inject()

    var lastUri: Uri? = null
        private set

    var lastBitmap: Bitmap? = null
    private var lastIsSystemInDarkMode = false

    private var lastEnqueued: Disposable? = null

    private lateinit var defaultBitmap: Bitmap

    val bitmap: Bitmap
        get() = lastBitmap ?: defaultBitmap

    var listener: ((Bitmap?) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(lastBitmap)
        }

    private val logger = Logger.withTag( this::class.java.simpleName )

    init {
        setDefaultBitmap()
    }

    fun setDefaultBitmap(): Boolean {
        val isSystemInDarkMode = appContext().resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        if (::defaultBitmap.isInitialized && isSystemInDarkMode == lastIsSystemInDarkMode) return false

        lastIsSystemInDarkMode = isSystemInDarkMode

        runCatching {
            defaultBitmap =
                Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888).applyCanvas {
                    drawColor(colorProvider(isSystemInDarkMode))
                }
        }.onFailure {
            logger.e( it ) { "Failed set default bitmap" }
        }

        return lastBitmap == null
    }

    fun load(uri: Uri?, onDone: (Bitmap) -> Unit) {
        logger.d("BitmapProvider load method being called")
        if (lastUri == uri) {
            listener?.invoke(lastBitmap)
            return
        }

        lastEnqueued?.dispose()
        lastUri = uri

        runCatching {
            lastEnqueued = ImageFactory.requestBuilder( uri.thumbnail(bitmapSize).toString() ) {
                allowHardware( false )
                listener(
                    onError = { _, result ->
                        logger.e( result.throwable ) { "Failed to load bitmap" }
                        lastBitmap = null
                        onDone(bitmap)
                        //listener?.invoke(lastBitmap)
                    },
                    onSuccess = { _, result ->
                        lastBitmap = result.image.toBitmap()
                        onDone(bitmap)
                        //listener?.invoke(lastBitmap)
                    }
                )
            }.let(context.imageLoader::enqueue )
        }.onFailure {
            logger.e( it ) { "Failed enqueue" }
        }
    }
}
