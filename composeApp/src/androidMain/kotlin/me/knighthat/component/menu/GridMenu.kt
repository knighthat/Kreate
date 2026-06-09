package me.knighthat.component.menu

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kreate.util.scrollingText
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.ui.components.themed.GridMenuItemHeight
import me.knighthat.component.menu.MenuConstants.CONTENT_HEIGHT_FRACTION
import me.knighthat.component.menu.MenuConstants.CONTENT_HORIZONTAL_PADDING
import me.knighthat.component.menu.MenuConstants.CONTENT_TOP_PADDING

object GridMenu {

    @Composable
    fun Menu( content: LazyGridScope.() -> Unit ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp

        LazyVerticalGrid(
            columns = GridCells.Adaptive( minSize = 120.dp ),
            contentPadding = PaddingValues(
                start = CONTENT_HORIZONTAL_PADDING.dp,
                end = CONTENT_HORIZONTAL_PADDING.dp,
                top = CONTENT_TOP_PADDING.dp
                // bottom padding is handled by [Modifier#navigationBarsPadding]
            ),
            modifier = Modifier.heightIn( max = (screenHeight * CONTENT_HEIGHT_FRACTION).dp )
                               .navigationBarsPadding(),
            content = content
        )
    }

    @Composable
    fun Entry(
        text: String,
        icon: @Composable BoxScope.() -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) = Column(
        modifier = modifier
            .clip(ShapeDefaults.Large)
            .height(GridMenuItemHeight)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(12.dp)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                               .weight( 1f ),
            contentAlignment = Alignment.Center,
            content = icon
        )

        Text(
            text = text,
            overflow = TextOverflow.Ellipsis,
            color = colorPalette().text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
                               .scrollingText()
        )
    }
}