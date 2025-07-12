package me.knighthat.component.playlist

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import app.kreate.android.Preferences
import app.kreate.android.R
import it.fast4x.innertube.YtMusic
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.models.Playlist
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.knighthat.component.dialog.InputDialogConstraints
import me.knighthat.component.dialog.TextInputDialog

class NewPlaylistDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val pipedState: MutableState<Boolean>
): TextInputDialog(InputDialogConstraints.ALL), MenuIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke(): NewPlaylistDialog =
            NewPlaylistDialog(
                remember { mutableStateOf(false) },
                remember {
                    mutableStateOf( TextFieldValue() )
                },
                Preferences.ENABLE_PIPED
            )
    }

    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override val iconId: Int = R.drawable.add_in_playlist
    override val messageId: Int = R.string.create_new_playlist
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.enter_the_playlist_name)
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.new_playlist )

    override var value: TextFieldValue by valueState
    override var isActive: Boolean by activeState

    override fun onShortClick() = showDialog()

    override fun hideDialog() {
        super.hideDialog()
        // TODO: Add a random name generator here
        value = value.copy( "" )
    }

    @Composable
    override fun LeadingIcon() = Icon(
        imageVector = Icons.Outlined.Edit,
        tint = colorPalette().accent,
        contentDescription = "new playlist name"
    )

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        var playlist: Playlist? = null

        if (isYouTubeSyncEnabled()) {
            CoroutineScope(Dispatchers.IO).launch {
                YtMusic.createPlaylist(newValue)
                       .getOrNull()
                       .also {
                           playlist = Playlist(
                               name = newValue,
                               browseId = it,
                               isYoutubePlaylist = true,
                               isEditable = true
                           )
                           println("Innertube YtMusic createPlaylist: $it")
                       }
            }
        } else {
            playlist = Playlist(name = newValue)
        }

        playlist?.let {
            Database.asyncTransaction {
                playlistTable.insert( it )
            }
        }

        hideDialog()
    }
}