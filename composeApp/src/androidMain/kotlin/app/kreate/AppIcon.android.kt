package app.kreate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import app.kreate.AppIcon.Round.bitmap
import app.kreate.AppIcon.bitmap
import app.kreate.android.R
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.utils.isAtLeastAndroid8

actual object AppIcon {

    private lateinit var _bitmap: Bitmap

    private fun mipmapToBitmap(
        context: Context,
        @DrawableRes res: Int,
        size: Int? = null
    ): Bitmap {
        val drawable = requireNotNull(ContextCompat.getDrawable( context, res ) ) {
            "Resource $res doesn't exist!"
        }
        val bitmap: Bitmap

        if ( drawable is BitmapDrawable )
        // This condition is `true` when rasterized image is used.
        // This is applicable for older devices (mostly API 25-)
            bitmap = drawable.bitmap
        else {
            val size = (
                    size ?: minOf(drawable.intrinsicHeight, drawable.intrinsicWidth)
                    ).takeIf { it != -1 } ?: 108
            bitmap = createBitmap(size, size)
            val canvas = Canvas(bitmap)

            if( isAtLeastAndroid8
                && drawable is AdaptiveIconDrawable
                && res == R.mipmap.ic_launcher
            ) {
                drawable.background?.setBounds( 0, 0, canvas.width, canvas.height )
                drawable.background.draw( canvas )

                /*
                   It gets a little bit interesting here.

                   [ic_launcher] looks good on launcher's icon,
                   but when rendered through here, the proportion between foreground
                   and background is so different that they look weird.

                   To combat this, the foreground is scaled up to size (80x80).
                */
                canvas.withSave {
                    // 80 is desired foreground size, 55 is it's actual size
                    val scale = 80f / 55
                    canvas.scale(  scale, scale, size / 2f, size / 2f )
                    drawable.foreground.setBounds( 0, 0, width, height )
                    drawable.foreground.draw( this )
                }
            } else {
                drawable.setBounds( 0, 0, canvas.width, canvas.height )
                drawable.draw( canvas )
            }
        }

        return bitmap.copy( bitmap.config ?: Bitmap.Config.ARGB_8888, false )
    }

    /**
     * Convert rasterized image (API 23-) or vector image (API 24+)
     * of [R.mipmap.ic_launcher] to [Bitmap].
     *
     * **This bitmap is immutable**
     *
     * This bitmap is sufficiently cached, thus, subsequent calls are
     * expected to be much faster.
     */
    fun bitmap( context: Context ): Bitmap {
        if( !::_bitmap.isInitialized )
            _bitmap = mipmapToBitmap( context, R.mipmap.ic_launcher )

        return _bitmap
    }

    /**
     * Convert rasterized image (API 23-) or vector image (API 24+)
     * of [R.mipmap.ic_launcher] to [Bitmap].
     *
     * If [R.mipmap.ic_launcher] is a [AdaptiveIconDrawable] (Android 8+),
     * the bitmap will be resized to match [size]. Otherwise, it stays
     * as retrieved from the system.
     *
     * **This bitmap is immutable**
     */
    fun bitmap( context: Context, size: Int ): Bitmap {
        if( ::_bitmap.isInitialized
            && _bitmap.height == size
            && _bitmap.width == size
        )
            return _bitmap

        return mipmapToBitmap( context, R.mipmap.ic_launcher, size )
    }

    /**
     * Create [ImageBitmap] version of [bitmap].
     *
     * Just like [bitmap], this component is **immutable**
     */
    fun imageBitmap( context: Context ): ImageBitmap = bitmap( context ).asImageBitmap()

    actual fun painter(): Painter = BitmapPainter( imageBitmap(appContext() ) )

    actual object Round {

        private lateinit var _bitmap: Bitmap

        /**
         * Convert rasterized image (API 23-) or vector image (API 24+)
         * of [R.mipmap.ic_launcher_round] to [Bitmap].
         *
         * **This bitmap is immutable**
         *
         * This bitmap is sufficiently cached, thus, subsequent calls are
         * expected to be much faster.
         */
        fun bitmap( context: Context ): Bitmap {
            if( !::_bitmap.isInitialized )
                _bitmap = mipmapToBitmap( context, R.mipmap.ic_launcher_round )

            return _bitmap
        }

        /**
         * Convert rasterized image (API 23-) or vector image (API 24+)
         * of [R.mipmap.ic_launcher_round] to [Bitmap].
         *
         * If [R.mipmap.ic_launcher_round] is a [AdaptiveIconDrawable] (Android 8+),
         * the bitmap will be resized to match [size]. Otherwise, it stays
         * as retrieved from the system.
         *
         * **This bitmap is immutable**
         */
        fun bitmap( context: Context, size: Int ): Bitmap {
            if( ::_bitmap.isInitialized
                && _bitmap.height == size
                && _bitmap.width == size
            )
                return _bitmap

            return mipmapToBitmap( context, R.mipmap.ic_launcher_round, size )
        }

        /**
         * Create [ImageBitmap] version of [bitmap].
         *
         * Just like [bitmap], this component is **immutable**
         */
        fun imageBitmap( context: Context ): ImageBitmap = bitmap( context ).asImageBitmap()

        actual fun painter(): Painter = BitmapPainter( imageBitmap(appContext() ) )
    }
}