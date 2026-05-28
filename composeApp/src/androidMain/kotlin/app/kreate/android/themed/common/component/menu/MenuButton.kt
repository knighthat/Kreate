package app.kreate.android.themed.common.component.menu

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.kreate.android.themed.common.component.BottomMenu
import me.knighthat.utils.Toaster


abstract class MenuButton<T> {

    @get:DrawableRes
    abstract val iconId: Int
    @get:StringRes
    abstract val tooltipMessageId: Int
    open val title: String
        @Composable
        get() = stringResource( tooltipMessageId )

    abstract fun onClick( menu: BottomMenu, item: T )

    open fun onLongClick( menu: BottomMenu, item: T ) = Toaster.i( tooltipMessageId )
}