package me.knighthat.utils

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import app.kreate.android.R
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.MagicConstant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * This singleton handles displaying **toast** to users.
 *
 * It sits on top of [Toasty] (which already an abstraction
 * of [Toast.makeText]).
 *
 * Additional features include:
 * - Ensure toasts are displayed on **_main_** thread
 * - Customizable background and foreground colors
 *
 * Future plans:
 * - Handles console outputs for debugging
 */
object Toaster : KoinComponent{

    private val context: Context by inject()

    enum class Type(
        @field:ColorInt val background: Int,
        @field:ColorInt val foreground: Int,
        @field:DrawableRes private val iconId: Int
    ) {
        NORMAL(
            Color.rgb( 108, 117, 125 ),
            Color.WHITE,
            -1
        ),
        SUCCESS(
            Color.rgb( 25, 135, 84 ),
            Color.WHITE,
            R.drawable.checkmark
        ),
        INFO(
            Color.rgb( 13, 110, 253 ),
            Color.WHITE,
            R.drawable.information
        ),
        WARNING(
            Color.rgb( 255, 193, 7 ),
            Color.rgb( 33, 37, 41 ),
            R.drawable.alert
        ),
        ERROR(
            Color.rgb( 220, 53, 69 ),
            Color.WHITE,
            R.drawable.close
        );

        val icon: Drawable by lazy {
            when( this ) {
                NORMAL -> ColorDrawable(Color.TRANSPARENT)
                else -> AppCompatResources.getDrawable( context, iconId )!!
            }
        }
    }

    @MainThread
    fun toast(
        message: String,
        type: Type = Type.NORMAL,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT,
        icon: Drawable = type.icon,
        background: Int,
        foreground: Int
    ) {
        CoroutineScope( Dispatchers.Main ).launch {
            Toasty.custom(
                context, message, icon, background, foreground, duration, icon != Type.NORMAL.icon, true
            ).show()
        }
    }

    @MainThread
    fun toast(
        message: String,
        type: Type = Type.NORMAL,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT,
        icon: Drawable = type.icon
    ) = this.toast( message, type, duration, icon, type.background, type.foreground )

    @MainThread
    fun toast(
        @StringRes messageId: Int,
        type: Type = Type.NORMAL,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT,
        icon: Drawable = type.icon,
        vararg formatArgs: Any?
    ) = this.toast( context.getString( messageId, *formatArgs ), type, duration, icon )

    @MainThread
    fun toast(
        @StringRes messageId: Int,
        @DrawableRes iconId: Int,
        type: Type = Type.NORMAL,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT
    ) = this.toast(
        message = context.getString( messageId ),
        type = type,
        duration = duration,
        icon = AppCompatResources.getDrawable( context, iconId )!!
    )

    fun n( message: String, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( message, Type.NORMAL, duration )

    fun n(
        @StringRes messageId: Int,
        vararg formatArgs: Any?,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT
    ) = this.toast( messageId, Type.NORMAL, duration, formatArgs = formatArgs )

    fun s( message: String, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( message, Type.SUCCESS, duration )

    fun s( @StringRes messageId: Int, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( messageId, Type.SUCCESS, duration )

    fun s(
        @StringRes messageId: Int,
        vararg formatArgs: Any?,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT
    ) = this.toast( messageId, Type.SUCCESS, duration, formatArgs = formatArgs )

    fun i( message: String, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( message, Type.INFO, duration )

    fun i( @StringRes messageId: Int, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( messageId, Type.INFO, duration )

    fun i(
        @StringRes messageId: Int,
        vararg formatArgs: Any?,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT
    ) = this.toast( messageId, Type.INFO, duration, formatArgs = formatArgs )

    fun w( message: String, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( message, Type.WARNING, duration )

    fun w( @StringRes messageId: Int, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( messageId, Type.WARNING, duration )

    fun w(
        @StringRes messageId: Int,
        vararg formatArgs: Any?,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT
    ) = this.toast( messageId, Type.WARNING, duration, formatArgs = formatArgs )

    fun e( message: String, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( message, Type.ERROR, duration )

    fun e( @StringRes messageId: Int, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( messageId, Type.ERROR, duration )

    fun e(
        @StringRes messageId: Int,
        vararg formatArgs: Any?,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT
    ) = this.toast( messageId, Type.ERROR, duration, formatArgs = formatArgs )

    /*

        PRE-DEFINED TOASTS

     */

    fun done() = this.s( R.string.done )

    fun noInternet() = this.e( R.string.no_connection )
}