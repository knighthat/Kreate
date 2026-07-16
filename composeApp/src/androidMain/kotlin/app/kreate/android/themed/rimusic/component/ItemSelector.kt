package app.kreate.android.themed.rimusic.component

import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import app.kreate.compose.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.ui.components.MenuState
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon

/**
 * Behaves exactly like a [MutableList] but with extra functions on top.
 *
 * When [isActive] is set to `false`, all selected items will be cleared
 */
class ItemSelector<E>(
    private val menuState: MenuState,
    list: MutableList<E> = mutableStateListOf(),
    private val onLongClickWhileActive: ItemSelector<E>.() -> Unit = {}
): MutableList<E> by list, MenuIcon, Descriptive {

    var isActive: Boolean by mutableStateOf( false )

    override val iconId: Int
        get() = if( isActive ) R.drawable.checked_filled else R.drawable.unchecked_outline
    override val messageId: Int
        get() = if ( isActive ) R.string.item_deselect else R.string.item_select
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    @Composable
    fun CheckBox(
        item: E,
        modifier: Modifier = Modifier.scale( 0.7f )
    ) {
        if( !isActive ) return

        Checkbox(
            checked = item in this,
            onCheckedChange = {
                if ( it )
                    add( item )
                else
                    remove( item )
            },
            colors = CheckboxDefaults.colors(
                checkedColor = colorPalette().accent,
                uncheckedColor = colorPalette().text
            ),
            modifier = modifier
        )
    }

    override fun onShortClick() {
        menuState.hide()
        isActive = !isActive

        if( !isActive ) clear()
    }

    override fun onLongClick() {
        if( isActive )
            onLongClickWhileActive()
        else
            super.onLongClick()
    }
}