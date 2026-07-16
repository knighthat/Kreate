package it.fast4x.rimusic.ui.components.themed

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.kreate.compose.R
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.MenuState
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon

@SuppressLint("ComposableNaming")
@Composable
fun PlayNext(
    onClick: () -> Unit
): MenuIcon = object: MenuIcon, Descriptive {

    val menuState: MenuState = LocalMenuState.current
    override val iconId: Int = R.drawable.play_skip_forward
    override val messageId: Int = R.string.play_next
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        onClick()
        menuState.hide()
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun Enqueue(
    onClick: () -> Unit
): MenuIcon = object : MenuIcon, Descriptive {

    val menuState: MenuState = LocalMenuState.current
    override val iconId: Int = R.drawable.enqueue
    override val messageId: Int = R.string.enqueue
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        onClick()
        menuState.hide()
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun ListenOnYouTube(
    onClick: () -> Unit
): MenuIcon = object : MenuIcon, Descriptive {

    val menuState: MenuState = LocalMenuState.current
    override val iconId: Int = R.drawable.play
    override val messageId: Int = R.string.listen_on_youtube
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        onClick()
        menuState.hide()
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun ThumbnailPicker(
    onClick: () -> Unit
): MenuIcon = object : MenuIcon, Descriptive {

    val menuState: MenuState = LocalMenuState.current
    override val iconId: Int = R.drawable.image
    override val messageId: Int = R.string.edit_thumbnail
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        onClick()
        menuState.hide()
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun ResetThumbnail(
    onClick: () -> Unit
): MenuIcon = object : MenuIcon, Descriptive {

    val menuState: MenuState = LocalMenuState.current
    override val iconId: Int = R.drawable.image
    override val messageId: Int = R.string.reset_thumbnail
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        onClick()
        menuState.hide()
    }
}