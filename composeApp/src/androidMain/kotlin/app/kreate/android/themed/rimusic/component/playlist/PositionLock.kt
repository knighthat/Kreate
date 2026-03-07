package app.kreate.android.themed.rimusic.component.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.kreate.android.R
import app.kreate.constant.SortOrder
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.DualIcon
import it.fast4x.rimusic.ui.components.tab.toolbar.DynamicColor
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import me.knighthat.utils.Toaster

class PositionLock(
    colorState: MutableState<Boolean>
): MenuIcon, DualIcon, DynamicColor, Descriptive {

    constructor( sortOrder: SortOrder ): this(mutableStateOf( sortOrder == SortOrder.ASCENDING ))
    constructor(): this(mutableStateOf( true ))

    override val secondIconId: Int = R.drawable.unlocked
    override val iconId: Int = R.drawable.locked
    override val messageId: Int = R.string.info_lock_unlock_reorder_songs
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    // This is inverted, first icon is locked icon
    override var isFirstIcon: Boolean by mutableStateOf( true )
    override var isFirstColor: Boolean by colorState

    fun isLocked(): Boolean = isFirstIcon

    override fun onShortClick() {
        if( !isFirstColor )
            Toaster.e( R.string.info_reorder_is_possible_only_in_ascending_sort )
        else
            isFirstIcon = !isFirstIcon
    }
}