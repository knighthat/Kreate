package app.kreate.internal.utils

import android.content.Context
import android.graphics.drawable.Drawable
import app.kreate.utils.Toaster
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


internal class ToasterImpl : Toaster, KoinComponent {

    private val context by inject<Context>()

    override fun toast(
        message: String,
        type: Toaster.Type,
        duration: Int,
        icon: Drawable,
        background: Int,
        foreground: Int
    ) {
        MainScope().launch {
            Toasty.custom(
                context, message, icon, background, foreground, duration, icon != Toaster.Type.NORMAL.icon, true
            ).show()
        }
    }

    override fun toast(
        message: String,
        type: Toaster.Type,
        duration: Int,
        icon: Drawable
    ) = this.toast( message, type, duration, icon, type.background, type.foreground )

    override fun toast(
        messageId: Int,
        type: Toaster.Type,
        duration: Int,
        icon: Drawable,
        vararg formatArgs: Any?
    ) = this.toast( context.getString(messageId, *formatArgs), type, duration, icon )

    override fun n( message: String, duration: Int ) =
        this.toast( message, Toaster.Type.NORMAL, duration )

    override fun n(
        messageId: Int,
        vararg formatArgs: Any?,
        duration: Int
    ) = this.toast( messageId, Toaster.Type.NORMAL, duration, formatArgs = formatArgs )

    override fun s( message: String, duration: Int ) =
        this.toast( message, Toaster.Type.SUCCESS, duration )

    override fun s( messageId: Int, duration: Int ) =
        this.toast( messageId, Toaster.Type.SUCCESS, duration )

    override fun s(
        messageId: Int,
        vararg formatArgs: Any?,
        duration: Int
    ) = this.toast( messageId, Toaster.Type.SUCCESS, duration, formatArgs = formatArgs )

    override fun i( message: String, duration: Int ) =
        this.toast( message, Toaster.Type.INFO, duration )

    override fun i( messageId: Int, duration: Int ) =
        this.toast( messageId, Toaster.Type.INFO, duration )

    override fun i(
        messageId: Int,
        vararg formatArgs: Any?,
        duration: Int
    ) = this.toast( messageId, Toaster.Type.INFO, duration, formatArgs = formatArgs )

    override fun w( message: String, duration: Int ) =
        this.toast( message, Toaster.Type.WARNING, duration )

    override fun w( messageId: Int, duration: Int ) =
        this.toast( messageId, Toaster.Type.WARNING, duration )

    override fun w(
        messageId: Int,
        vararg formatArgs: Any?,
        duration: Int
    ) = this.toast( messageId, Toaster.Type.WARNING, duration, formatArgs = formatArgs )

    override fun e( message: String, duration: Int ) =
        this.toast( message, Toaster.Type.ERROR, duration )

    override fun e( messageId: Int, duration: Int ) =
        this.toast( messageId, Toaster.Type.ERROR, duration )

    override suspend fun e(resource: StringResource, duration: Int) =
        e( getString(resource), duration )

    override fun e(
        messageId: Int,
        vararg formatArgs: Any?,
        duration: Int
    ) = this.toast( messageId, Toaster.Type.ERROR, duration, formatArgs = formatArgs )
}