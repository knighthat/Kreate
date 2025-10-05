package app.kreate.android.themed.common.component.tab

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import app.kreate.android.themed.common.component.AbstractMediaDownloadDialog
import app.kreate.database.models.Song
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.bold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi
class DeleteAllDownloadedDialog(
    binder: PlayerServiceModern.Binder,
    private val context: Context,
    private val getSongs: () -> List<Song>
) : AbstractMediaDownloadDialog(binder), MenuIcon, Descriptive {

    override val messageId: Int = R.string.info_remove_all_downloaded_songs
    override val iconId: Int = R.drawable.download
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.dialog_title_are_you_sure )
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun getSongs(): List<Song> = getSongs.invoke()

    override fun onShortClick() = showDialog()

    override fun onConfirm() {
        super.onConfirm()

        CoroutineScope( Dispatchers.Default ).launch {
            getSongs().fastMap( Song::asMediaItem )
                      .onEach { MyDownloadHelper.removeDownload( it ) }
        }
    }

    @Composable
    override fun DialogBody() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy( 5.dp ),
            modifier = Modifier.fillMaxWidth( .9f )
        ) {
            Icon(
                painter = painterResource( R.drawable.warning_outline ),
                contentDescription = null,
                tint = colorResource( R.color.red_warning ),
                modifier = Modifier.size( 32.dp )
            )

            BasicText(
                text = stringResource( R.string.dialog_text_this_action_is_irreversible ),
                style = typography().s.bold.copy( colorResource( R.color.red_warning ) )
            )
        }
    }
}