package app.kreate.android.themed.common.component

import androidx.annotation.CallSuper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.media3.common.util.UnstableApi
import app.kreate.database.models.Song
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import me.knighthat.component.dialog.ConfirmDialog

@UnstableApi
abstract class AbstractMediaDownloadDialog(
    private val binder: PlayerServiceModern.Binder
): ConfirmDialog {

    override var isActive: Boolean by mutableStateOf( false )

    protected abstract fun getSongs(): List<Song>

    @CallSuper
    override fun onConfirm() {
        hideDialog()

        getSongs().fastMap( Song::id )
                  .also {
                      Database.asyncTransaction {
                          formatTable.deleteBySongId( it )
                      }
                  }
                  .fastForEach( binder.cache::removeResource )
    }
}