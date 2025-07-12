package app.kreate.android.themed.rimusic.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import app.kreate.android.themed.common.component.AbstractSearch
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.thumbnailShape
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.ui.styling.favoritesIcon

class Search(
    private val scrollableState: ScrollableState?
): AbstractSearch(), MenuIcon, Descriptive {

    override val iconId: Int = R.drawable.search_circle
    override val messageId: Int = android.R.string.search_go
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    /**
     * Attempt to hide search bar if it's empty
     */
    fun hideIfEmpty() {
        if ( !isVisible ) return

        if ( isBlank() )
            isVisible = false
        else
            isFocused = false
    }

    @Composable
    override fun DecorationBox( innerTextField: @Composable () -> Unit ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy( DECO_BOX_ITEM_SPACING.dp ),
            modifier = Modifier.padding( horizontal = 10.dp )
                               .fillMaxWidth()
                               .horizontalScroll( rememberScrollState() )
        ) {
            Icon(
                painter = painterResource( R.drawable.search ),
                contentDescription = stringResource( android.R.string.search_go ),
                tint = colorPalette().favoritesIcon,
                modifier = Modifier.size( DECO_BOX_ICON_SIZE.dp )
            )

            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.weight(1f)
            ) {
                Placeholder()

                // Actual text from user
                innerTextField()
            }

            ClearSearchButton()
        }
    }

    @Composable
    override fun SearchBar( modifier: Modifier  ) {
        // Scroll to top (with no animation) when search changes
        LaunchedEffect( input ) {
            scrollableState.let {
                if( it is LazyGridState )
                    it.requestScrollToItem( 0, 0 )
                else if( it is LazyListState )
                    it.requestScrollToItem( 0, 0 )
            }
        }

        AnimatedVisibility(
            visible = isVisible,
            modifier = Modifier.padding( all = 10.dp )
                               .fillMaxWidth()
        ) {
            super.SearchBar(
                modifier.background(
                    colorPalette().background4,
                    thumbnailShape()
                )
            )
        }
    }


    override fun onShortClick() {
        isVisible = !isVisible
        isFocused = isVisible
    }
}