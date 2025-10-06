package it.fast4x.rimusic.service


import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.applyCanvas
import app.kreate.Platform
import app.kreate.coil3.ImageFactory
import coil3.request.Disposable
import coil3.request.allowHardware
import coil3.toBitmap
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.utils.thumbnail
import timber.log.Timber

//context(Context)
class BitmapProvider(
    private val bitmapSize: Int,
    private val colorProvider: (isSystemInDarkMode: Boolean) -> Int
) {
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
            Timber.e("Failed set default bitmap in BitmapProvider ${it.stackTraceToString()}")
        }

        return lastBitmap == null
    }

    fun load(uri: Uri?, onDone: (Bitmap) -> Unit) {
        Timber.d("BitmapProvider load method being called")
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
                        Timber.e("Failed to load bitmap ${result.throwable.stackTraceToString()}")
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
            }.let( Platform.imageFactoryProvider.imageLoader::enqueue )
        }.onFailure {
            Timber.e("Failed enqueue in BitmapProvider ${it.stackTraceToString()}")
        }
    }
}
