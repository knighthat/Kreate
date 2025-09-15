package app.kreate.android.service.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapIndexed
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import app.kreate.android.Preferences
import app.kreate.android.R
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.enums.NotificationButtons
import it.fast4x.rimusic.enums.QueueLoopType
import it.fast4x.rimusic.models.QueuedMediaItem
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.utils.mediaItems
import it.fast4x.rimusic.utils.playNext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.utils.Toaster
import timber.log.Timber

@UnstableApi
class ExoPlayerListener(
    private val player: ExoPlayer,
    private val mediaSession: MediaSession,
    private val binder: PlayerServiceModern.Binder,
    private val isNetworkAvailable: MutableStateFlow<Boolean>,
    private val waitingForNetwork: MutableStateFlow<Boolean>,
    private val sendOpenEqualizerIntent: () -> Unit,
    private val sendCloseEqualizerIntent: () -> Unit,
    private val onMediaTransition: (MediaItem?) -> Unit
): Player.Listener {

    private var volumeNormalizationJob: Job = Job()

    var loudnessEnhancer: LoudnessEnhancer? = null
        private set

    /**
     * Requires [Preferences.ENABLE_PERSISTENT_QUEUE] to be **enabled** to work.
     */
    @AnyThread
    fun saveQueueToDatabase() {
        if( !Preferences.ENABLE_PERSISTENT_QUEUE.value ) return

        CoroutineScope( Dispatchers.Default ).launch {
            val (queue, index, playerPos) = withContext(Dispatchers.Main ) {
                // Any call related to [Player] must happen on main thread
                with( player ) {
                    Triple(currentTimeline.mediaItems, currentMediaItemIndex, currentPosition)
                }
            }

            queue.fastMapIndexed { i, m ->
                QueuedMediaItem(
                    mediaItem = m,
                    position = if( i == index ) playerPos else null
                )
            }.also { list ->
                if( list.isEmpty() ) return@also

                Database.asyncTransaction {
                    queueTable.deleteAll()
                    queueTable.insert( list )
                }
            }
        }
    }

    /**
     * (Re)render media control in notification area.
     */
    @AnyThread
    fun updateMediaControl( context: Context, player: Player ) {
        CoroutineScope(Dispatchers.Default ).launch {
            var firstButton: CommandButton? = null
            var secondButton: CommandButton? = null
            val buttons = mutableListOf<CommandButton>()

            NotificationButtons.entries
                .fastMap { it to PlaybackController.makeButton( context, player, it ) }
                .fastForEach { (nBtn, cmdBtn) ->
                    when (nBtn) {
                        Preferences.MEDIA_NOTIFICATION_FIRST_ICON.value -> firstButton = cmdBtn
                        Preferences.MEDIA_NOTIFICATION_SECOND_ICON.value -> secondButton = cmdBtn
                        else -> buttons.add( cmdBtn )
                    }
                }

            val layoutButton = buildList {
                firstButton?.also( ::add )
                secondButton?.also( ::add )
                addAll( buttons )
            }

            mediaSession.setCustomLayout( layoutButton )
        }
    }

    @MainThread
    fun maybeNormalizeVolume() {
        if ( !Preferences.AUDIO_VOLUME_NORMALIZATION.value ) {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            volumeNormalizationJob.cancel()
            return
        }

        runCatching {
            if (loudnessEnhancer == null) {
                loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
            }
        }.onFailure {
            Timber.e("PlayerService maybeNormalizeVolume load loudnessEnhancer ${it.stackTraceToString()}")
            println("PlayerService maybeNormalizeVolume load loudnessEnhancer ${it.stackTraceToString()}")
            return
        }

        player.currentMediaItem
            ?.mediaId
            ?.also { songId ->
                volumeNormalizationJob.cancel()
                volumeNormalizationJob = CoroutineScope(Dispatchers.IO ).launch {
                    fun Float?.toMb() = ((this ?: 0f) * 100).toInt()

                    Database.formatTable
                        .findBySongId( songId )
                        .cancellable()
                        .collectLatest { format ->
                            val loudnessMb = format?.loudnessDb.toMb().let {
                                if (it !in -2000..2000) {
                                    Toaster.w( "Extreme loudness detected" )

                                    0
                                } else
                                    it
                            }

                            runCatching {
                                val baseGain by Preferences.AUDIO_VOLUME_NORMALIZATION_TARGET
                                loudnessEnhancer?.setTargetGain( baseGain.toMb() - loudnessMb )
                                loudnessEnhancer?.enabled = true
                            }
                        }
                }
            }
    }

    private fun loadFromRadio( reason: Int ) {
        // Don't fetch more item if:
        // - Feature is disabled
        // - When song is repeated
        // - Start new queue
        if( !Preferences.QUEUE_AUTO_APPEND.value
            || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            || reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
        ) return

        val positionToLast = player.mediaItemCount - player.currentMediaItemIndex
        // Make sure only add when about 10 songs to the last song in queue
        // TODO: Add slider in settings to let user change number of songs
        if( positionToLast <= 10 && !binder.isLoadingRadio )
            player.currentMediaItem?.let {
                binder.startRadio( it, true )
            }
    }

    override fun onPlayWhenReadyChanged( playWhenReady: Boolean, reason: Int ) = saveQueueToDatabase()

    override fun onRepeatModeChanged( repeatMode: Int ) {
        updateMediaControl( appContext(), this.player )
        Preferences.QUEUE_LOOP_TYPE.value = QueueLoopType.from( repeatMode )
    }

    override fun onMediaItemTransition( mediaItem: MediaItem?, reason: Int ) {
        if ( player.playerError != null ) player.prepare()

        maybeNormalizeVolume()
        loadFromRadio(reason)
        onMediaTransition( mediaItem )
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if ( reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED )
            saveQueueToDatabase()
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateMediaControl( appContext(), this.player )
        if (shuffleModeEnabled) {
            val shuffledIndices = IntArray(player.mediaItemCount) { it }
            shuffledIndices.shuffle()
            shuffledIndices[shuffledIndices.indexOf(player.currentMediaItemIndex)] = shuffledIndices[0]
            shuffledIndices[0] = player.currentMediaItemIndex
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)

        val connectionExceptions = listOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, //primary error code to manage
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        )

        // check if error is caused by internet connection
        val isConnectionError = (error.cause?.cause as? PlaybackException)?.errorCode in connectionExceptions

        if (!isNetworkAvailable.value || isConnectionError) {
            isNetworkAvailable.value = true
            Toaster.noInternet()
            return
        }

        val playbackHttpExeptionList = listOf(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            416 // 416 Range Not Satisfiable
        )

        if (error.errorCode in playbackHttpExeptionList) {
            Timber.e("PlayerServiceModern onPlayerError recovered occurred errorCodeName ${error.errorCodeName} cause ${error.cause?.cause}")
            println("PlayerServiceModern onPlayerError recovered occurred errorCodeName ${error.errorCodeName} cause ${error.cause?.cause}")
            player.pause()
            player.prepare()
            player.play()
            return
        }

        if ( !Preferences.PLAYBACK_SKIP_ON_ERROR.value || !player.hasNextMediaItem() )
            return

        val prev = player.currentMediaItem ?: return
        //player.seekToNextMediaItem()
        player.playNext()

        Toaster.e( R.string.skip_media_on_error_message, prev.mediaMetadata.title )
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (
            events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                sendOpenEqualizerIntent()
            } else {
                sendCloseEqualizerIntent()
                if (!player.playWhenReady) {
                    waitingForNetwork.value = false
                }
            }
        }
    }
}