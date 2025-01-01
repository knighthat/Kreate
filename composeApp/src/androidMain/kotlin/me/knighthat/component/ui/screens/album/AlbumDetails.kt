package me.knighthat.component.ui.screens.album

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.ui.components.tab.toolbar.*
import it.fast4x.rimusic.ui.components.themed.IDialog
import kotlinx.coroutines.Dispatchers

class AlbumModifier private constructor(
    private val activeState: MutableState<Boolean>,
    private val onAction: Database.(String) -> Unit,
    override val iconId: Int,
    override val messageId: Int,
    override var value: String,
): MenuIcon, Descriptive, IDialog {

    companion object {
        @JvmStatic
        @Composable
        fun init(
            onAction: Database.(String) -> Unit,
            iconId: Int,
            messageId: Int,
            value: String,
            placeholder: String
        ): AlbumModifier = AlbumModifier(
            remember { mutableStateOf(false) },
            onAction,
            iconId,
            messageId,
            value
        )
    }

    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var isActive: Boolean = activeState.value
        set(value) {
            activeState.value = value
            field = value
        }
    override val dialogTitle: String
        @Composable
        get() = menuIconTitle

    override fun onShortClick() = super.onShortClick()

    override fun onSet(newValue: String) =
        Database.asyncTransaction { onAction(newValue) }
}

@Composable
fun AlbumBookmark(
    albumId: String
): MenuIcon = object : MenuIcon, Descriptive, DualIcon {
    val isBookmarked by remember {
        Database.isAlbumBookmarked( albumId )
    }.collectAsState( false, Dispatchers.IO )

    override val iconId: Int = R.drawable.bookmark
    override val secondIconId: Int = R.drawable.bookmark_outline
    override var isFirstIcon: Boolean = isBookmarked
    override val messageId: Int = R.string.info_bookmark_album
    override val color: Color
        @Composable
        get() = colorPalette().accent
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() = Database.asyncTransaction {
        toggleAlbumBookmark( albumId )
    }
}

class Translate private constructor(
    activeState: MutableState<Boolean>
): DynamicColor, Icon, Descriptive {

    companion object {
        @JvmStatic
        @Composable
        fun init() = Translate(
            rememberSaveable { mutableStateOf(false) }
        )
    }

    var isActive by activeState

    override val iconId: Int = R.drawable.translate
    override val messageId: Int = R.string.info_translation

    override var isFirstColor by activeState

    override fun onShortClick() { isFirstColor = !isFirstColor }
}