package me.knighthat.component.ui.screens.player

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.kreate.compose.R
import app.kreate.player.Player
import app.kreate.preferences.Preferences
import app.kreate.preferences.QUEUE_LOOP_TYPE
import app.kreate.utils.Toaster
import co.touchlab.kermit.Logger
import it.fast4x.compose.reordering.ReorderingState
import it.fast4x.rimusic.enums.QueueLoopType
import it.fast4x.rimusic.ui.components.tab.toolbar.ConfirmDialog
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.DynamicColor
import it.fast4x.rimusic.ui.components.tab.toolbar.Icon
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.utils.mediaItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

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
    override var isFirstColor: Boolean by rememberSaveable { mutableStateOf(false) }

    override fun onShortClick() {
        isFirstColor = !isFirstColor
        Preferences.ENABLE_DISCOVER.update( isFirstColor )
        onDiscoverClick( isFirstColor )
    }

    @Composable
    override fun ToolBarButton() {
        super<MenuIcon>.ToolBarButton()

        val isEnabled by Preferences.ENABLE_DISCOVER.collectAsStateWithLifecycle()
        LaunchedEffect( isEnabled ) {
            isFirstColor = isEnabled
        }
    }
}

class Repeat private constructor(
    coroutineScope: CoroutineScope
): MenuIcon, Descriptive {

    companion object {
        @JvmStatic
        @Composable
        fun init( coroutineScope: CoroutineScope ): Repeat = Repeat(coroutineScope)
    }

    var type: QueueLoopType by mutableStateOf( Preferences.QUEUE_LOOP_TYPE.value )

    override val iconId: Int = -1   // Unused
    override val messageId: Int = R.string.repeat
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
    override val icon: Painter
        @Composable
        get() = painterResource( type.iconId )

    init {
        coroutineScope.launch {
            Preferences.QUEUE_LOOP_TYPE
                       .drop( 1 )
                       .collect { type = it }
        }
    }

    override fun onShortClick() { type = type.next() }
}

@SuppressLint("ComposableNaming")
@Composable
fun ShuffleQueue(
    player: Player,
    reorderingState: ReorderingState
): MenuIcon = object: MenuIcon, Descriptive {
    override val iconId: Int = R.drawable.shuffle
    override val messageId: Int = R.string.shuffle
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    @OptIn(UnstableApi::class)
    override fun onShortClick() {
        val index = player.currentMediaItemIndex
        reorderingState.coroutineScope.launch {
            reorderingState.lazyListState.animateScrollToItem( index )
        }.invokeOnCompletion {
            if( it != null ) {
                Logger.e( "", it, "QueueShuffler" )
                it.message?.also( Toaster::e )

                return@invokeOnCompletion
            }

            // Any calls to [Player] must happen on Main thread
            val mediaItems = player.mediaItems.toList()
            CoroutineScope( Dispatchers.Default ).launch {
                val startAt = index + 1
                val shuffled = mediaItems.subList( startAt, mediaItems.size ).shuffled()

                withContext( Dispatchers.Main.immediate ) {
                    player.removeMediaItems( startAt, mediaItems.size )
                    player.addNext( shuffled )

                    Toaster.done()
                }
            }
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
    override val isEnabled: Boolean by Preferences.PLAYER_ACTION_OPEN_QUEUE_ARROW.collectAsStateWithLifecycle()
    override val iconId: Int = R.drawable.chevron_down

    override fun onShortClick() = onShortClick()
}