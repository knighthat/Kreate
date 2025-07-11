package me.knighthat.component.ui.screens.player

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.media3.exoplayer.ExoPlayer
import app.kreate.android.R
import app.kreate.android.Preferences
import it.fast4x.compose.reordering.ReorderingState
import it.fast4x.rimusic.enums.QueueLoopType
import it.fast4x.rimusic.ui.components.tab.toolbar.ConfirmDialog
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.DynamicColor
import it.fast4x.rimusic.ui.components.tab.toolbar.Icon
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.utils.shuffleQueue
import it.fast4x.rimusic.utils.smoothScrollToTop
import kotlinx.coroutines.launch

@SuppressLint("ComposableNaming")
@Composable
fun Discover(
    onDiscoverClick: (Boolean) -> Unit
): MenuIcon = object: MenuIcon, Descriptive, DynamicColor {
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.discover )
    override val iconId: Int = R.drawable.star_brilliant
    override val messageId: Int = R.string.discoverinfo

    // Active state of this button
    override var isFirstColor: Boolean by Preferences.ENABLE_DISCOVER

    override fun onShortClick() {
        isFirstColor = !isFirstColor
        onDiscoverClick( isFirstColor )
    }
}

class Repeat private constructor(
    private val typeState: MutableState<QueueLoopType>
): MenuIcon, Descriptive {

    companion object {
        @JvmStatic
        @Composable
        fun init(): Repeat = Repeat(Preferences.QUEUE_LOOP_TYPE)
    }

    var type: QueueLoopType = typeState.value
        private set(value) {
            typeState.value = value
            field = value
        }

    override val iconId: Int = -1   // Unused
    override val messageId: Int = R.string.repeat
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
    override val icon: Painter
        @Composable
        get() = painterResource( type.iconId )

    override fun onShortClick() { type = type.next() }
}

@SuppressLint("ComposableNaming")
@Composable
fun ShuffleQueue(
    player: ExoPlayer,
    reorderingState: ReorderingState
): MenuIcon = object: MenuIcon, Descriptive {
    override val iconId: Int = R.drawable.shuffle
    override val messageId: Int = R.string.shuffle
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        reorderingState.coroutineScope.launch {
            reorderingState.lazyListState.smoothScrollToTop()
        }.invokeOnCompletion {
            player.shuffleQueue()
        }
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun DeleteFromQueue(
    onDeleteConfirm: ConfirmDialog.() -> Unit
): MenuIcon = object: MenuIcon, Descriptive, ConfirmDialog {
    override val iconId: Int = R.drawable.trash
    override val messageId: Int = R.string.remove_from_queue
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
    override val dialogTitle: String
        @Composable
        get() = "Do you really want to clean queue?"

    override var isActive: Boolean by rememberSaveable { mutableStateOf(false) }

    override fun onShortClick() { isActive = !isActive }

    override fun onConfirm() = onDeleteConfirm()
}

@SuppressLint("ComposableNaming")
@Composable
fun QueueArrow(
    onShortClick: () -> Unit
): Icon = object: Icon {
    override val isEnabled: Boolean by Preferences.PLAYER_ACTION_OPEN_QUEUE_ARROW
    override val iconId: Int = R.drawable.chevron_down

    override fun onShortClick() = onShortClick()
}