package me.knighthat.component.ui.screens.album

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.kreate.android.R
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.DynamicColor
import it.fast4x.rimusic.ui.components.tab.toolbar.Icon
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.ui.components.themed.IDialog

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