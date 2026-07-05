package app.kreate.android.themed.common.component.menu

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.media3.common.MediaItem
import app.kreate.android.R
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.database.Database
import app.kreate.util.MODIFIED_PREFIX
import app.kreate.util.cleanPrefix
import me.knighthat.component.dialog.InputDialogConstraints
import me.knighthat.utils.Toaster


class SongRenameButton : TextInputDialog<MediaItem>(InputDialogConstraints.ALL) {

    override val iconId: Int = R.drawable.title_edit
    override val tooltipMessageId: Int = R.string.update_title
    override val allowEmpty: Boolean = false
    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default

    private lateinit var songId: String

    override fun onClick( menu: BottomMenu, item: MediaItem ) {
        val cleanTitle = cleanPrefix(item.mediaMetadata.title?.toString().orEmpty())
        super.value = TextFieldValue(cleanTitle)
        this.songId = item.mediaId

        super.onClick( menu, item )
    }

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        Database.asyncTransaction {
            songTable.updateTitle( songId, "$MODIFIED_PREFIX$newValue" )
            Toaster.done()
        }

        hideDialog()
    }
}