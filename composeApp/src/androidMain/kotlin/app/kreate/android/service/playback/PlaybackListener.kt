package app.kreate.android.service.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import app.kreate.android.Preferences
import app.kreate.database.models.Event
import it.fast4x.rimusic.Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


@UnstableApi
class PlaybackListener(
    private val scope: CoroutineScope
) : PlaybackStatsListener.Callback {

    override fun onPlaybackStatsReady( eventTime: AnalyticsListener.EventTime, playbackStats: PlaybackStats ) {
        // if pause listen history is enabled, don't register statistic event
        if ( Preferences.PAUSE_HISTORY.value ) return

        val timeSpent = playbackStats.totalPlayTimeMs
        if ( timeSpent <= Preferences.QUICK_PICKS_MIN_DURATION.value.asMillis )
            return

        val mediaItem: MediaItem = eventTime.timeline
                                            .getWindow(
                                                eventTime.windowIndex,
                                                Timeline.Window()
                                            )
                                            .mediaItem

        scope.launch( Dispatchers.IO ) {
            // Suspend the coroutine until the song is added to the database.
            // TODO: Cancel this if history is paused while coroutine is under suspension
            Database.songTable.findById( mediaItem.mediaId ).first { it != null }

            Database.asyncTransaction {
                songTable.updateTotalPlayTime( mediaItem.mediaId, timeSpent, true)
                val event = Event(mediaItem.mediaId, System.currentTimeMillis(), timeSpent)
                eventTable.insertIgnore( event )
            }
        }
    }
}