package me.knighthat.component.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.kreate.compose.R
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.DualIcon
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon

class HiddenSongs private constructor(
    firstIconState: MutableState<Boolean>
): MenuIcon, DualIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke(): HiddenSongs = HiddenSongs(
            rememberSaveable { mutableStateOf(false) }
        )
    }

    override val secondIconId: Int = R.drawable.eye_off
    override val iconId: Int = R.drawable.eye
    override val messageId: Int = R.string.info_show_hide_hidden_songs
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var isFirstIcon: Boolean by firstIconState

    fun isHiddenExcluded(): Boolean = !isFirstIcon

    override fun onShortClick() { isFirstIcon = !isFirstIcon }
}