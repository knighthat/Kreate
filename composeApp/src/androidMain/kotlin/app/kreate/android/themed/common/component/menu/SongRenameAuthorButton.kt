package app.kreate.android.themed.common.component.menu

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.media3.common.MediaItem
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.compose.R
import app.kreate.database.Database
import app.kreate.util.MODIFIED_PREFIX
import app.kreate.util.cleanPrefix
import app.kreate.utils.Toaster
import me.knighthat.component.dialog.InputDialogConstraints


class SongRenameAuthorButton : TextInputDialog<MediaItem>(InputDialogConstraints.ALL) {

    override val allowEmpty: Boolean = false
    override val iconId: Int = R.drawable.artists_edit
    override val tooltipMessageId: Int = R.string.update_authors
    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default

    private lateinit var songId: String

    override fun onClick( menu: BottomMenu, item: MediaItem ) {
        val cleanArtists = item.mediaMetadata.artist?.toString()?.let( ::cleanPrefix ).orEmpty()
        super.value = TextFieldValue(cleanArtists)
        this.songId = item.mediaId

        super.onClick( menu, item )
    }

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        Database.asyncTransaction {
            songTable.updateArtists( songId, "$MODIFIED_PREFIX$newValue" )
            Toaster.done()
        }

        hideDialog()
    }
}