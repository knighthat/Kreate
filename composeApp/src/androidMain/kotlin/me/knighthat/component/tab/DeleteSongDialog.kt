package me.knighthat.component.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import app.kreate.android.R
import app.kreate.database.models.Song
import app.kreate.di.CacheType
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.MenuState
import it.fast4x.rimusic.ui.components.themed.DeleteDialog
import me.knighthat.utils.Toaster
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Optional
import kotlin.getValue

@UnstableApi
open class DeleteSongDialog(
    activeState: MutableState<Boolean>,
    menuState: MenuState,
) : DeleteDialog(activeState, menuState), KoinComponent {

    companion object {
        @Composable
        operator fun invoke() = DeleteSongDialog(
            remember { mutableStateOf(false) },
            LocalMenuState.current
        )
    }

    protected val cache: Cache by inject(CacheType.CACHE)
    protected val downloadCache: Cache by inject(CacheType.DOWNLOAD)

    var song = Optional.empty<Song>()

    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.delete_song )

    override fun onDismiss() {
        // Always override current value with empty Optional
        // to prevent unwanted outcomes
        song = Optional.empty()
        super.onDismiss()
    }

    override fun onConfirm() {
        song.ifPresent {
            Database.asyncTransaction {
                menuState.hide()
                cache.removeResource( it.id )
                // FIXME: This is unsafe, use [DownloadService.sendRemoveDownload] instead
                downloadCache.removeResource( it.id )
                songPlaylistMapTable.deleteBySongId( it.id )
                formatTable.deleteBySongId( it.id )
                songTable.delete( it )
            }

            Toaster.i( R.string.deleted )
        }

        onDismiss()
    }
}