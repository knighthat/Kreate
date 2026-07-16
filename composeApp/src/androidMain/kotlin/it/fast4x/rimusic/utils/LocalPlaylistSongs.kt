package it.fast4x.rimusic.utils

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import app.kreate.compose.R
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.MenuState
import it.fast4x.rimusic.ui.components.themed.DeleteDialog

@SuppressLint("ComposableNaming")
@Composable
fun DeletePlaylist(
    activeState: MutableState<Boolean> = rememberSaveable { mutableStateOf( false ) },
    menuState: MenuState = LocalMenuState.current,
    onEvent: DeleteDialog.() -> Unit
): DeleteDialog = object : DeleteDialog( activeState, menuState ) {

    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.dialog_title_delete_playlist )

    override fun onConfirm() = onEvent()
}