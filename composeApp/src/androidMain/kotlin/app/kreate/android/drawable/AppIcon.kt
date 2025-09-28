package app.kreate.android.drawable

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.VectorDrawable
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import app.kreate.android.R
import app.kreate.android.drawable.AppIcon.Round.bitmap
import app.kreate.android.drawable.AppIcon.bitmap
import it.fast4x.rimusic.utils.isAtLeastAndroid8


object AppIcon {

    private lateinit var _bitmap: Bitmap

    private fun mipmapToBitmap( context: Context, @DrawableRes res: Int ): Bitmap {
        val drawable = requireNotNull( AppCompatResources.getDrawable( context, res ) ) {
            "Resource $res doesn't exist!"
        }
        val bitmap: Bitmap

        if ( drawable is BitmapDrawable )
            // This condition is `true` when rasterized image is used.
            // This is applicable for older devices (mostly API 25-)
            bitmap = drawable.bitmap
        else {
            bitmap = createBitmap(
                width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
                height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
            )
            val canvas = Canvas(bitmap)

            if( isAtLeastAndroid8
                && drawable is AdaptiveIconDrawable
                && res == R.mipmap.ic_launcher
            ) {
                /*
                    It gets a little bit interesting here.

                    [ic_launcher_foreground_round] looks good on launcher's icon,
                    but when rendered through here, the proportion between foreground
                    and background is so different that they look weird.

                    To combat this, [ic_launcher_foreground] is introduced with increased
                    size (72x72).
                 */
                val foreground = requireNotNull(
                    AppCompatResources.getDrawable( context, R.drawable.ic_launcher_foreground )
                ) { "Can't get ic_launcher_foreground" }
                check( foreground is VectorDrawable ) {
                    "ic_launcher_foreground isn't VectorDrawable"
                }

                drawable.background?.setBounds( 0, 0, canvas.width, canvas.height )
                drawable.background.draw( canvas )

                foreground.setBounds( 0, 0, canvas.width, canvas.height )
                foreground.draw( canvas )
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