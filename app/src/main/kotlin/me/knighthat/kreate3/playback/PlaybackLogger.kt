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

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import co.touchlab.kermit.Logger
import me.knighthat.kreate3.ui.component.origin
import java.io.IOException

/**
 * Writes what the player does to the log, enough to tell afterwards why a song didn't play:
 * what started and stopped, every song change and where it came from, loading problems, buffers
 * running dry and every error with its causes.
 *
 * It names songs by their id and addresses without their query, never a header, a cookie or a
 * token. Everything written also goes through the log's own redaction.
 */
@OptIn(UnstableApi::class)
class PlaybackLogger : AnalyticsListener {

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        Logger.d(tag = TAG) { "State is ${stateName(state)}, ${eventTime.describe()}" }
    }

    override fun onPlayWhenReadyChanged(
        eventTime: AnalyticsListener.EventTime,
        playWhenReady: Boolean,
        reason: Int,
    ) {
        Logger.i(tag = TAG) {
            "Play when ready is $playWhenReady, because ${
                playWhenReadyReason(
                    reason
                )
            }"
        }
    }

    override fun onIsPlayingChanged(eventTime: AnalyticsListener.EventTime, isPlaying: Boolean) {
        Logger.i(tag = TAG) { "${if (isPlaying) "Playing" else "Not playing"}, ${eventTime.describe()}" }
    }

    override fun onPlaybackSuppressionReasonChanged(
        eventTime: AnalyticsListener.EventTime,
        playbackSuppressionReason: Int,
    ) {
        Logger.i(tag = TAG) {
            if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE)
                "Playback is no longer suppressed"
            else
                "Playback is suppressed: the audio focus was lost or the output isn't suitable"
        }
    }

    override fun onMediaItemTransition(
        eventTime: AnalyticsListener.EventTime,
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        Logger.i(tag = TAG) {
            "Song is ${mediaItem.describe()} (position ${eventTime.windowIndex} of the queue), " +
                    "because ${transitionReason(reason)}"
        }
    }

    override fun onPositionDiscontinuity(
        eventTime: AnalyticsListener.EventTime,
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        Logger.d(tag = TAG) {
            "Position jumped from ${oldPosition.positionMs} ms to ${newPosition.positionMs} ms, " +
                    "because ${discontinuityReason(reason)}"
        }
    }

    override fun onRepeatModeChanged(eventTime: AnalyticsListener.EventTime, repeatMode: Int) {
        Logger.d(tag = TAG) { "Repeat mode is $repeatMode" }
    }

    override fun onShuffleModeChanged(
        eventTime: AnalyticsListener.EventTime,
        shuffleModeEnabled: Boolean,
    ) {
        Logger.d(tag = TAG) { "Shuffle is $shuffleModeEnabled" }
    }

    override fun onTimelineChanged(eventTime: AnalyticsListener.EventTime, reason: Int) {
        Logger.v(tag = TAG) { "The queue changed, now ${eventTime.timeline.windowCount} songs" }
    }

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        Logger.e(error, TAG) {
            "Error ${error.errorCodeName} (${error.errorCode}) at ${eventTime.describe()}"
        }
    }

    override fun onLoadError(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean,
    ) {
        Logger.w(error, TAG) {
            "Loading ${loadEventInfo.uri.withoutQuery()} failed after " +
                    "${loadEventInfo.bytesLoaded} bytes in ${loadEventInfo.loadDurationMs} ms" +
                    if (wasCanceled) ", the load was cancelled" else ", it goes on"
        }
    }

    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long,
    ) {
        Logger.w(tag = TAG) {
            "The audio buffer ran dry: $bufferSizeMs ms buffer, $elapsedSinceLastFeedMs ms since it was fed"
        }
    }

    override fun onAudioSinkError(
        eventTime: AnalyticsListener.EventTime,
        audioSinkError: Exception
    ) {
        Logger.e(audioSinkError, TAG) { "The audio output failed" }
    }

    override fun onAudioCodecError(
        eventTime: AnalyticsListener.EventTime,
        audioCodecError: Exception,
    ) {
        Logger.e(audioCodecError, TAG) { "The audio decoder failed" }
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        Logger.d(tag = TAG) { "Decoder $decoderName ready after $initializationDurationMs ms" }
    }

    override fun onAudioInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) {
        Logger.d(tag = TAG) {
            "Audio is ${format.sampleMimeType}, codecs ${format.codecs}, ${format.averageBitrate} bps, " +
                    "${format.sampleRate} Hz, ${format.channelCount} channels"
        }
    }

    override fun onBandwidthEstimate(
        eventTime: AnalyticsListener.EventTime,
        totalLoadTimeMs: Int,
        totalBytesLoaded: Long,
        bitrateEstimate: Long,
    ) {
        Logger.v(tag = TAG) { "Bandwidth is about $bitrateEstimate bps" }
    }

    override fun onPlayerReleased(eventTime: AnalyticsListener.EventTime) {
        Logger.i(tag = TAG) { "The player was released" }
    }

    private fun AnalyticsListener.EventTime.describe(): String =
        "position $currentPlaybackPositionMs ms, buffered ${totalBufferedDurationMs} ms"

    private fun MediaItem?.describe(): String =
        if (this == null) "none" else "$mediaId (${origin ?: "no origin"})"

    /** Where [this] is, without what could identify the user: no query, no user info. */
    private fun android.net.Uri.withoutQuery(): String =
        "${scheme}://${host ?: ""}${path ?: ""}"

    private fun stateName(state: Int) = when (state) {
        Player.STATE_IDLE -> "idle"
        Player.STATE_BUFFERING -> "buffering"
        Player.STATE_READY -> "ready"
        Player.STATE_ENDED -> "ended"
        else -> "unknown ($state)"
    }

    private fun playWhenReadyReason(reason: Int) = when (reason) {
        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "the user asked"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "the audio focus was lost"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "the output was unplugged"
        Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "a remote controller asked"
        Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "the song ended"
        Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG -> "playback was suppressed too long"
        else -> "of reason $reason"
    }

    private fun transitionReason(reason: Int) = when (reason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "the last song ended"
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "of a seek to it"
        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "it repeats"
        Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "the queue changed"
        else -> "of reason $reason"
    }

    private fun discontinuityReason(reason: Int) = when (reason) {
        Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "the song changed"
        Player.DISCONTINUITY_REASON_SEEK -> "a seek"
        Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "a seek that had to move"
        Player.DISCONTINUITY_REASON_SKIP -> "an ad or a part was skipped"
        Player.DISCONTINUITY_REASON_REMOVE -> "the song was removed"
        Player.DISCONTINUITY_REASON_INTERNAL -> "the player did"
        else -> "of reason $reason"
    }

    private companion object {

        const val TAG = "Playback"
    }
}
