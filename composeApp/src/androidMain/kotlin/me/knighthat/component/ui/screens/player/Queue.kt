package me.knighthat.component.ui.screens.player

import android.annotation.SuppressLint
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.media3.exoplayer.ExoPlayer
import it.fast4x.compose.reordering.ReorderingState
import it.fast4x.rimusic.R
import it.fast4x.rimusic.enums.QueueLoopType
import it.fast4x.rimusic.ui.components.tab.toolbar.*
import it.fast4x.rimusic.utils.*
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
    override var isFirstColor: Boolean by rememberPreference(discoverKey, false)

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
        fun init(): Repeat = Repeat(
            rememberPreference( queueLoopTypeKey, QueueLoopType.Default )
        )
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
    override val isEnabled: Boolean by rememberPreference( showButtonPlayerArrowKey, true )
    override val iconId: Int = R.drawable.chevron_down

    override fun onShortClick() = onShortClick()
}