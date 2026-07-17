package app.kreate.internal.utils

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import app.kreate.resources.R
import app.kreate.utils.Toaster
import org.koin.java.KoinJavaComponent.get


internal val <E: Enum<Toaster.Type>> E.background: Int
    @ColorInt
    get() = when( this ) {
        Toaster.Type.NORMAL     -> Color.rgb( 108, 117, 125 )
        Toaster.Type.SUCCESS    -> Color.rgb( 25, 135, 84 )
        Toaster.Type.INFO       -> Color.rgb( 13, 110, 253 )
        Toaster.Type.WARNING    -> Color.rgb( 255, 193, 7 )
        Toaster.Type.ERROR      -> Color.rgb( 220, 53, 69 )
        else                    -> throw IllegalStateException("Unknown type: $this")
    }

internal val <E: Enum<Toaster.Type>> E.foreground: Int
    @ColorInt
    get() = when( this ) {
        Toaster.Type.NORMAL     -> Color.WHITE
        Toaster.Type.SUCCESS    -> Color.WHITE
        Toaster.Type.INFO       -> Color.WHITE
        Toaster.Type.WARNING    -> Color.rgb( 33, 37, 41 )
        Toaster.Type.ERROR      -> Color.WHITE
        else                    -> throw IllegalStateException("Unknown type: $this")
    }

internal val <E: Enum<Toaster.Type>> E.icon: Drawable
    get() = when( this ) {
        Toaster.Type.NORMAL -> Color.TRANSPARENT.toDrawable()

        else -> {
            val context: Context = get(Context::class.java)
            val iconId = when( this ) {
                Toaster.Type.SUCCESS    -> R.drawable.check
                Toaster.Type.INFO       -> R.drawable.info
                Toaster.Type.WARNING    -> R.drawable.warning
                Toaster.Type.ERROR      -> R.drawable.close
                else                    -> throw IllegalStateException("Unknown type: $this")
            }

            return AppCompatResources.getDrawable( context, iconId )!!
        }
    }