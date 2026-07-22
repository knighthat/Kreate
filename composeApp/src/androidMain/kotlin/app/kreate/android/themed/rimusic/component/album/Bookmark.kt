package app.kreate.android.themed.rimusic.component.album

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.kreate.compose.R
import app.kreate.database.Database
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.DualIcon
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon

class Bookmark(
    private val albumId: String,
): MenuIcon, Descriptive, DualIcon {

    override val iconId: Int = R.drawable.bookmark
    override val secondIconId: Int = R.drawable.bookmark_outline
    override val messageId: Int = R.string.info_bookmark_album
    override val color: Color
        @Composable
        get() = colorPalette().accent
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var isFirstIcon: Boolean by mutableStateOf( false )

    override fun onShortClick() = Database.asyncTransaction {
        albumTable.toggleBookmark( albumId )
    }
}