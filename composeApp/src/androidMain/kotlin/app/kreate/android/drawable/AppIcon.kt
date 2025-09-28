package app.kreate.android.drawable

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import app.kreate.android.R
import app.kreate.android.drawable.AppIcon.Round.bitmap
import app.kreate.android.drawable.AppIcon.bitmap


object AppIcon {

    private lateinit var _bitmap: Bitmap

    private fun mipmapToBitmap( context: Context, @DrawableRes res: Int ): Bitmap {
        val drawable = requireNotNull( AppCompatResources.getDrawable( context, res ) ) {
            "Resource $res doesn't exist!"
        }

        if ( drawable is BitmapDrawable )
            return drawable.bitmap

        val bitmap = createBitmap(
            width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
            height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1,
            config = Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        drawable.setBounds( 0, 0, canvas.width, canvas.height )
        drawable.draw( canvas )

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
     * Create [ImageBitmap] version of [bitmap].
     *
     * Just like [bitmap], this component is **immutable**
     */
    fun imageBitmap( context: Context ): ImageBitmap = bitmap( context ).asImageBitmap()

    object Round {

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
         * Create [ImageBitmap] version of [bitmap].
         *
         * Just like [bitmap], this component is **immutable**
         */
        fun imageBitmap( context: Context ): ImageBitmap = bitmap( context ).asImageBitmap()
    }
}