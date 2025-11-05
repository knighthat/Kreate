package app.kreate.android.themed.rimusic.component.tab

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.constant.SortCategory
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.Drawable
import it.fast4x.rimusic.enums.MenuStyle
import it.fast4x.rimusic.enums.SortOrder
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.MenuState
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.tab.toolbar.Clickable
import it.fast4x.rimusic.ui.components.tab.toolbar.DualIcon
import it.fast4x.rimusic.ui.components.tab.toolbar.Menu
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.utils.semiBold
import me.knighthat.enums.TextView

open class Sort<T>(
    override val menuState: MenuState,
    sortByState: Preferences<T>,
    sortOrderState: Preferences<SortOrder>,
): MenuIcon, Clickable, Menu, DualIcon where T: Enum<T>, T: SortCategory {

    open var sortBy: T by sortByState
    open var sortOrder: SortOrder by sortOrderState

    open val arrowDirection: State<Float>
        @Composable
        get() = animateFloatAsState(
            targetValue = sortOrder.rotationZ,
            animationSpec = tween(durationMillis = 400, easing = LinearEasing),
            label = ""
        )
    override val iconId: Int = R.drawable.arrow_up
    override val secondIconId: Int = R.drawable.random
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.sorting_order )
    override val icon: Painter
        // [isFirstIcon] is not a MutableState object,
        // so when its state changes, no recomposition happens
        @Composable
        get() = painterResource(
            if( sortBy.isRandom ) secondIconId else iconId
        )

    override var menuStyle: MenuStyle by Preferences.MENU_STYLE
    override var isFirstIcon: Boolean = !sortBy.isRandom

    /** Flip oder. */
    override fun onShortClick() { sortOrder = !sortOrder }

    override fun onLongClick() = openMenu()

    @Composable
    override fun ListMenu() = me.knighthat.component.menu.ListMenu.Menu {
        // Ignore error "Cannot access 'java. lang. constant. Constable' which is a supertype of 'java. lang. Class'"
        sortBy::class.java.enumConstants?.forEach {
            me.knighthat.component.menu.ListMenu.Entry(
                text = if (it is TextView) it.text else it.name,
                icon = {
                    Icon(
                        painter =
                            if( it is Drawable )
                                it.icon
                            else
                                painterResource( R.drawable.close ),
                        contentDescription = it.name,
                        tint = colorPalette().text,
                        modifier = Modifier.size( TabToolBar.TOOLBAR_ICON_SIZE )
                    )
                },
                onClick = {
                    menuState.hide()
                    sortBy = it
                }
            )
        }
    }

    @Composable
    override fun GridMenu() = me.knighthat.component.menu.GridMenu.Menu {
        items(
            // Ignore error "Cannot access 'java. lang. constant. Constable' which is a supertype of 'java. lang. Class'"
            items = sortBy::class.java.enumConstants.orEmpty(),
            key = Enum<T>::ordinal
        ) {
            me.knighthat.component.menu.GridMenu.Entry(
                text = if (it is TextView) it.text else it.name,
                icon = {
                    Icon(
                        painter =
                            if( it is Drawable )
                                it.icon
                            else
                                painterResource( R.drawable.close ),
                        contentDescription = it.name,
                        tint = colorPalette().text,
                        modifier = Modifier.size( TabToolBar.TOOLBAR_ICON_SIZE )
                    )
                },
                onClick = {
                    menuState.hide()
                    sortBy = it
                }
            )
        }
    }

    @Composable
    override fun MenuComponent() =
        it.fast4x.rimusic.ui.components.themed.Menu {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding( end = 12.dp )
            ) {
                BasicText(
                    text = menuIconTitle,
                    style = typography().m.semiBold,
                    modifier = Modifier.padding(
                        vertical = 8.dp,
                        horizontal = 24.dp
                    )
                )
            }

            if( menuStyle == MenuStyle.List )
                ListMenu()
            else
                GridMenu()
        }

    @Composable
    override fun ToolBarButton() {
        val modifier = if( !sortBy.isRandom ) {
            val animatedArrow by arrowDirection
            Modifier.graphicsLayer { rotationZ = animatedArrow }
        } else
            Modifier

        TabToolBar.Icon(
            icon,
            color,
            sizeDp,
            isEnabled,
            this.modifier.then( modifier),
            this::onShortClick,
            this::onLongClick
        )
    }
}