/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.playback

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.repository.HistoryRepository
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.ui.component.origin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Writes a play to the history for every song played, with the time spent actually playing it:
 * pauses, loading and seeking don't count, and the time is real time, whatever the speed.
 *
 * A [PlaybackStatsListener] reports a song once its session ends: another song starts, it leaves
 * the queue or the player is released. Stopping or reaching the end of the queue doesn't end it,
 * so the time so far is saved then, and the play is updated if the session goes on.
 *
 * The writes run in [scope], which has to outlive the player: the last plays are reported after
 * the player is released. Nothing has to be released, the recorder stops with the player.
 */
@OptIn(UnstableApi::class)
class PlaybackHistoryRecorder(
    private val player: ExoPlayer,
    private val history: HistoryRepository,
    scope: CoroutineScope,
) : AnalyticsListener {

    private val plays = Channel<Play>(Channel.UNLIMITED)
    private val stats =
        PlaybackStatsListener(false) { start, stats -> onSessionEnded(start, stats) }

    /** Number of the session plays are sent for, a play sent again with it updates the first. */
    private var session = 0L

    /** Song of the session saved before it ended, `null` when it hasn't been. */
    private var savedSongId: String? = null

    init {
        scope.launch { write() }
        // Added first, so the stats are up to date when this listener hears of the same events
        player.addAnalyticsListener(stats)
        player.addAnalyticsListener(this)
    }

    override fun onEvents(player: Player, events: AnalyticsListener.Events) {
        if (events.contains(AnalyticsListener.EVENT_PLAYBACK_STATE_CHANGED)
            && (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED)
        )
            saveOngoing()

        // The stats listener has reported every session by now
        if (events.contains(AnalyticsListener.EVENT_PLAYER_RELEASED))
            plays.close()
    }

    /** Saves the time so far of the session that is still going on. */
    private fun saveOngoing() {
        // A file another app opened isn't a stored song, it has no history
        val songId = player.currentMediaItem?.takeIf { it.origin != null }?.mediaId ?: return
        val playStats = stats.playbackStats ?: return
        send(songId, playStats, ended = false)
    }

    /** [start] is when the session began, its item is the song played. */
    private fun onSessionEnded(start: EventTime, playStats: PlaybackStats) {
        if (start.windowIndex >= start.timeline.windowCount) return
        val item = start.timeline.getWindow(start.windowIndex, Timeline.Window()).mediaItem
        if (item.origin == null) return
        send(item.mediaId, playStats, ended = true)
    }

    private fun send(songId: String, playStats: PlaybackStats, ended: Boolean) {
        if (Preferences.PLAYBACK_HISTORY_PAUSED.value) return

        // Shorter plays aren't history. That includes songs only loaded ahead of time, they are
        // never played, and the time spent is stored in whole seconds anyway.
        val minPlay = Preferences.PLAYBACK_HISTORY_MIN_SECONDS.value.coerceAtLeast(1).seconds
        val timeSpent = playStats.totalPlayTimeMs.milliseconds
        if (timeSpent < minPlay) return

        // Only the session playing can be saved early, and it is the next to end with time spent.
        // Checked in case it ended without being reported.
        if (savedSongId != null && savedSongId != songId) session++

        plays.trySend(Play(session, songId, timeSpent))
        if (ended) {
            session++
            savedSongId = null
        } else
            savedSongId = songId
    }

    /** Writes the plays in the order they were sent, [session] tells a new play from an update. */
    private suspend fun write() {
        var lastSession = -1L
        var lastId = 0L
        for (play in plays)
            try {
                if (play.session == lastSession)
                    history.updatePlay(lastId, play.timeSpent)
                else {
                    lastId = history.recordPlay(play.songId, play.timeSpent)
                    lastSession = play.session
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The song may be missing from the database, or no user is active
                Log.w(TAG, "Couldn't save the play of ${play.songId}", e)
            }
    }

    private class Play(val session: Long, val songId: String, val timeSpent: Duration)

    private companion object {

        const val TAG = "PlaybackHistory"
    }
}
