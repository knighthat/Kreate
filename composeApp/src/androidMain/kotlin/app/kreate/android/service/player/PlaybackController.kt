package app.kreate.android.service.player

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.session.CommandButton
import app.kreate.android.R
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.NotificationButtons
import it.fast4x.rimusic.service.MyDownloadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object PlaybackController {

    private suspend fun getLikeIconId( songId: String ): Int =
        // Any call to [Player] must happen on main thread
        Database.songTable.findById( songId ).first()?.likedAt.let {
            when ( it ) {
                -1L -> R.drawable.heart_dislike
                null -> R.drawable.heart_outline
                else -> R.drawable.heart
            }
        }

    @OptIn(UnstableApi::class)
    private fun getDownloadIconId( songId: String ): Int {
        val state = MyDownloadHelper.instance.downloads.value[songId]?.state ?: Download.STATE_STOPPED
        return when( state ) {
            Download.STATE_COMPLETED    -> R.drawable.downloaded
            Download.STATE_DOWNLOADING,
            Download.STATE_QUEUED       -> R.drawable.download_progress
            else                        -> R.drawable.download
        }
    }

    private fun getRepeatModeIconId( repeatMode: Int ): Int =
        when( repeatMode ) {
            Player.REPEAT_MODE_ALL -> R.drawable.infinite
            Player.REPEAT_MODE_OFF -> R.drawable.repeat
            Player.REPEAT_MODE_ONE -> R.drawable.repeatone
            else -> throw IllegalStateException("Unknown repeat mode $repeatMode")
        }

    @AnyThread
    suspend fun getIconId( player: Player, button: NotificationButtons ): Int =
        when( button ) {
            NotificationButtons.Favorites -> {
                // Any call to [Player] must happen on main thread
                val songId = withContext( Dispatchers.Main ) {
                    player.currentMediaItem?.mediaId
                }
                songId?.let { getLikeIconId( it ) } ?: R.drawable.heart_outline
            }

            NotificationButtons.Download -> {
                // Any call to [Player] must happen on main thread
                val songId = withContext( Dispatchers.Main ) {
                    player.currentMediaItem?.mediaId
                }
                songId?.let( ::getDownloadIconId ) ?: R.drawable.download
            }

            NotificationButtons.Repeat ->
                getRepeatModeIconId(
                    withContext(Dispatchers.Main ) { player.repeatMode }
                )

            NotificationButtons.Shuffle -> {
                // Any call to [Player] must happen on main thread
                val isShuffleOn = withContext( Dispatchers.Main ) {
                    player.shuffleModeEnabled
                }
                if( isShuffleOn ) R.drawable.shuffle_filled else R.drawable.shuffle
            }

            NotificationButtons.Search,
            NotificationButtons.Radio -> button.iconId
        }

    suspend fun makeButton(
        context: Context,
        player: Player,
        button: NotificationButtons,
        builder: CommandButton.Builder.() -> Unit = {}
    ): CommandButton =
        CommandButton.Builder( CommandButton.ICON_UNDEFINED )
                     .setDisplayName( context.resources.getString( button.textId ) )
                     .setSessionCommand( button.sessionCommand )
                     .apply {
                         getIconId( player, button ).also( ::setCustomIconResId )
                         builder()
                     }
                     .build()
}