package app.kreate.android.service

import androidx.annotation.AnyThread
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.kreate.android.service.player.CustomExoPlayer
import app.kreate.database.models.Album
import app.kreate.database.models.Artist
import app.kreate.util.cleanPrefix
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.utils.resize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import me.knighthat.discord.Discord
import me.knighthat.discord.ListeningActivity
import me.knighthat.innertube.Constants
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


class PlayerEventUpdateDiscord : Player.Listener, KoinComponent {

    private val player: CustomExoPlayer by inject()
    private val discord: Discord by inject()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logger = Logger.withTag(this::class.java.simpleName)

    @Volatile
    @Player.State
    private var previousPlaybackState: Int = Player.STATE_IDLE

    @AnyThread
    private suspend fun makeActivity( id: String, metadata: MediaMetadata?, timeStart: Long ): ListeningActivity {
        //<editor-fold defaultstate="collapsed" desc="Artists">
        val artists: Artist? = Database.artistTable
                                       .findBySongId( id )
                                       .firstOrNull()
                                       ?.firstOrNull()
        val artistsText = artists?.cleanName() ?: metadata?.artist?.toString()?.let( ::cleanPrefix )
        // https://music.youtube.com/channel/[channelId]
        val artistUrl = artists?.let { "${Constants.YOUTUBE_MUSIC_URL}/channel/${it.id}" }
        val artistThumbnailUrl = artists?.thumbnailUrl?.resize( 64, 64 )?.toUri()
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Album">
        val album: Album? = Database.albumTable
                                    .findBySongId( id )
                                    .firstOrNull()
        val alumTitle = album?.cleanTitle() ?: metadata?.albumTitle?.toString()?.let( ::cleanPrefix )
        //</editor-fold>
        return ListeningActivity(
            timeStart = timeStart,
            duration = metadata?.durationMs ?: 0L,
            songName = metadata?.title?.toString().orEmpty(),
            thumbnailUrl = metadata?.artworkUri,
            artistName = artistsText.orEmpty(),
            artistUrl = artistUrl,
            artistThumbnailUrl = artistThumbnailUrl,
            albumName = alumTitle.orEmpty()
        )
    }

    override fun onIsPlayingChanged( isPlaying: Boolean ) {
        // Must execute here because it's still on main thread
        val timeStart = System.currentTimeMillis() - player.currentPosition
        val mediaItem = player.currentMediaItem

        if( mediaItem == null ) {
            logger.w { "onIsPlayingChanged($isPlaying) has `null` mediaItem" }
            return
        }

        coroutineScope.launch {
            val activity = makeActivity( mediaItem.mediaId, mediaItem.mediaMetadata, timeStart )
            if( isPlaying )
                discord.listening( activity )
            else
                discord.pause( activity )
        }
    }

    override fun onMediaItemTransition( mediaItem: MediaItem?, reason: Int ) {
        // Must execute here because it's still on main thread
        val timeStart = System.currentTimeMillis() - player.currentPosition

        coroutineScope.launch {
            if( mediaItem != null ) {
                val activity = makeActivity( mediaItem.mediaId, mediaItem.mediaMetadata, timeStart )
                discord.listening(activity)
            } else
                discord.reset()
        }
    }

    override fun onPlaybackStateChanged( playbackState: Int ) {
        /**
         * [Player.STATE_BUFFERING] & [Player.STATE_READY] are useless,
         * they only tell whether the media can be played or not.
         *
         * But when they transit to either [Player.STATE_IDLE] or [Player.STATE_ENDED],
         * the player was either stopped or ran out of media to play.
         */
        if( (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED)
            &&  (previousPlaybackState == Player.STATE_BUFFERING || previousPlaybackState == Player.STATE_READY)
        )
            coroutineScope.launch { discord.reset() }

        previousPlaybackState = playbackState
    }

    @OptIn(UnstableApi::class)
    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        // Skip this handler if wasn't sought by user
        if( reason != Player.DISCONTINUITY_REASON_SEEK ) return

        // Must execute here because it's still on main thread
        val timeStart = System.currentTimeMillis() - newPosition.positionMs
        val mediaItem = newPosition.mediaItem

        if( mediaItem == null ) {
            logger.w { "onPositionDiscontinuity's newPosition has `null` mediaItem ($reason)" }
            return
        }

        coroutineScope.launch {
            val activity = makeActivity( mediaItem.mediaId, mediaItem.mediaMetadata, timeStart )
            discord.listening( activity )
        }
    }
}