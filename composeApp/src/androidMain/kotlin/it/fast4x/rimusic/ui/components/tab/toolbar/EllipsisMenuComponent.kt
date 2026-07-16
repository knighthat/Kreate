package it.fast4x.rimusic.ui.components.tab.toolbar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import app.kreate.compose.R
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.enums.MenuStyle
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.MenuState
import it.fast4x.rimusic.ui.components.themed.Menu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class EllipsisMenuComponent private constructor(
    coroutineScope: CoroutineScope,
    private val buttons: () -> List<Button>,
    override val menuState: MenuState,
) : Menu, Icon {

    companion object {
        @JvmStatic
        @Composable
        fun init( coroutineScope: CoroutineScope, items: () -> List<Button> ) = EllipsisMenuComponent(
            coroutineScope,
            items,
            LocalMenuState.current
        )
    }

    override val iconId: Int = R.drawable.ellipsis_horizontal

    override var menuStyle: MenuStyle by mutableStateOf( Preferences.MENU_STYLE.value )

    init {
        coroutineScope.launch {
            Preferences.MENU_STYLE
                       // Drop the first value because it's already in the init value
                       .drop( 1 )
                       .collect { menuStyle = it }
        }
    }

    override fun onShortClick() = openMenu()

    @Composable
    override fun ListMenu() {
        Menu(
            Modifier.fillMaxHeight(0.4f)
                .onPlaced { it.size.height.dp * 0.5f }
        ) {
            buttons().forEach {
                if( it is MenuIcon)
                    it.ListMenuItem()
            }
        }
    }

    @Composable
    override fun GridMenu() {
        it.fast4x.rimusic.ui.components.themed.GridMenu(
            contentPadding = PaddingValues(
                start = 8.dp,
                top = 8.dp,
                end = 8.dp,
                bottom = 8.dp + WindowInsets.systemBars.asPaddingValues()
                    .calculateBottomPadding()
            )
        ) {
            items( buttons(), Button::hashCode ) {
                if( it is MenuIcon)
                    it.GridMenuItem()
            }
        }
    }

    @Composable
    override fun MenuComponent() {
        if( menuStyle == MenuStyle.Grid )
            GridMenu()
        else
            ListMenu()
    }
}