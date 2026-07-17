package app.kreate.utils

import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.annotation.AnyThread
import androidx.annotation.IntDef
import androidx.annotation.StringRes
import app.kreate.internal.utils.ToasterImpl
import app.kreate.internal.utils.icon
import app.kreate.resources.R

/**
 * Thread-safe toasts
 */
interface Toaster {

    companion object : Toaster by ToasterImpl()

    //region Raw
    @AnyThread
    fun toast(
        message: String,
        type: Type = Type.NORMAL,
        @Duration duration: Int = Toast.LENGTH_SHORT,
        icon: Drawable = type.icon,
        background: Int,
        foreground: Int
    )

    @AnyThread
    fun toast(
        message: String,
        type: Type = Type.NORMAL,
        @Duration duration: Int = Toast.LENGTH_SHORT,
        icon: Drawable = type.icon
    )

    @AnyThread
    fun toast(
        @StringRes messageId: Int,
        type: Type = Type.NORMAL,
        @Duration duration: Int = Toast.LENGTH_SHORT,
        icon: Drawable = type.icon,
        vararg formatArgs: Any?
    )
    //endregion

    @AnyThread
    fun n( message: String, @Duration duration: Int = Toast.LENGTH_SHORT )

    @AnyThread
    fun n(
        @StringRes messageId: Int,
        vararg formatArgs: Any?,
        @Duration duration: Int = Toast.LENGTH_SHORT
    )

    @AnyThread
    fun s( message: String, @Duration duration: Int = Toast.LENGTH_SHORT )


    @AnyThread
    fun s( @StringRes messageId: Int, @Duration duration: Int = Toast.LENGTH_SHORT )


    @AnyThread
    fun s(
        @StringRes messageId: Int,
        vararg formatArgs: Any?,
        @Duration duration: Int = Toast.LENGTH_SHORT
    )

    @AnyThread
    fun i( message: String, @Duration duration: Int = Toast.LENGTH_SHORT )


    @AnyThread
    fun i( @StringRes messageId: Int, @Duration duration: Int = Toast.LENGTH_SHORT )


    @AnyThread
    fun i(
        @StringRes messageId: Int,
        vararg formatArgs: Any?,
        @Duration duration: Int = Toast.LENGTH_SHORT
    )

    @AnyThread
    fun w( message: String, @Duration duration: Int = Toast.LENGTH_SHORT )


    @AnyThread
    fun w( @StringRes messageId: Int, @Duration duration: Int = Toast.LENGTH_SHORT )


    @AnyThread
    fun w(
        @StringRes messageId: Int,
        vararg formatArgs: Any?,
        @Duration duration: Int = Toast.LENGTH_SHORT
    )

    @AnyThread
    fun e( message: String, @Duration duration: Int = Toast.LENGTH_SHORT )


    @AnyThread
    fun e( @StringRes messageId: Int, @Duration duration: Int = Toast.LENGTH_SHORT )


    @AnyThread
    fun e(
        @StringRes messageId: Int,
        vararg formatArgs: Any?,
        @Duration duration: Int = Toast.LENGTH_SHORT
    )

    //region Predefined toasts
    @AnyThread
    fun done() = this.s( R.string.toast_done )

    @AnyThread
    fun noInternet() = this.w( R.string.toast_no_internet )
    //endregion

    enum class Type { NORMAL, SUCCESS, INFO, WARNING, ERROR }

    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(Toast.LENGTH_SHORT, Toast.LENGTH_LONG)
    annotation class Duration
}