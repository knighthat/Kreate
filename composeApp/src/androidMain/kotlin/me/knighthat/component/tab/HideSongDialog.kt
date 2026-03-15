package me.knighthat.component.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.DownloadService
import app.kreate.android.R
import app.kreate.di.CacheType
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.MenuState
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject

@UnstableApi
class HideSongDialog private constructor(
    activeState: MutableState<Boolean>,
    menuState: MenuState
) : DeleteSongDialog(activeState, menuState) {

    companion object {
        @Composable
        operator fun invoke() = HideSongDialog(
            remember { mutableStateOf(false) },
            LocalMenuState.current
        )
    }

    override val messageId: Int = R.string.hide
    override val iconId: Int = R.drawable.eye_off
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.hidesong )

    override fun onConfirm() {
        song.ifPresent {
            Database.asyncTransaction {
                menuState.hide()
                cache.removeResource( it.id )
                // FIXME: This is unsafe, use [DownloadService.sendRemoveDownload] instead
                downloadCache.removeResource( it.id )
                formatTable.updateContentLengthOf( it.id )
                songTable.updateTotalPlayTime( it.id, 0 )
            }
        }

        onDismiss()
    }
}