package me.knighthat.component.menu

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kreate.util.scrollingText
import it.fast4x.rimusic.colorPalette
import me.knighthat.component.menu.MenuConstants.CONTENT_HEIGHT_FRACTION
import me.knighthat.component.menu.MenuConstants.CONTENT_TOP_PADDING

object ListMenu {

    @Composable
    fun Menu( content: @Composable ColumnScope.() -> Unit ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp

        Column(
            Modifier.heightIn( max = (screenHeight * CONTENT_HEIGHT_FRACTION).dp )
                    .padding(
                        top = CONTENT_TOP_PADDING.dp
                        // bottom padding is handled by [Modifier#navigationBarsPadding]
                    )
                    .verticalScroll( rememberScrollState() )
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            content = content
        )
    }

    @Composable
    fun Entry(
        text: String,
        icon: @Composable RowScope.() -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {},
        trailingContent: @Composable () -> Unit = {}
    ) = Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy( 24.dp ),
        modifier = modifier.fillMaxWidth()
                           .alpha( if (enabled) 1f else 0.4f )
                           .combinedClickable(
                               enabled = enabled,
                               onClick = onClick,
                               onLongClick = onLongClick
                           )
                           .padding( horizontal = 24.dp )
    ) {
        icon()

        Column(
            modifier = Modifier.padding( vertical = 16.dp )
                               .weight( 1f )
        ) {
            Text(
                text = text,
                overflow = TextOverflow.Ellipsis,
                color = colorPalette().text,
                textAlign = TextAlign.Start,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
                                   .scrollingText()
            )
        }

        trailingContent()
    }
}