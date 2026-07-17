package me.knighthat.component.song

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import app.kreate.compose.R
import app.kreate.database.Database
import app.kreate.database.models.Song
import app.kreate.util.MODIFIED_PREFIX
import app.kreate.utils.Toaster
import me.knighthat.component.RenameDialog

class ChangeAuthorDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val getSong: () -> Song?
) : RenameDialog(activeState, valueState) {

    companion object {
        @Composable
        operator fun invoke( getSong: () -> Song? ): ChangeAuthorDialog =
            ChangeAuthorDialog(
                remember { mutableStateOf(false) },
                remember {
                    mutableStateOf( TextFieldValue(getSong()?.cleanArtistsText() ?: "") )
                },
                getSong
            )
    }

    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override val iconId: Int = R.drawable.artists_edit
    override val messageId: Int = R.string.update_authors
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
    override val dialogTitle: String
        @Composable
        get() = menuIconTitle

    override fun hideDialog() {
        super.hideDialog()
        // Always reset string so when dialog turns
        // back on it will not show previous value.
        value = TextFieldValue(getSong()?.cleanArtistsText() ?: "")
    }

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        val song = getSong() ?: return
        Database.asyncTransaction {
            songTable.updateArtists( song.id, "$MODIFIED_PREFIX$newValue" )
            Toaster.done()
        }

        hideDialog()
    }
}