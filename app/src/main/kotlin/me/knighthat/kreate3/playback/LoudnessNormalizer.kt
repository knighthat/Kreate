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

import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.model.SongFormat
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.provider.opensubsonic.RAW_FORMAT
import me.knighthat.kreate3.source.local.REFERENCE_LUFS
import kotlin.math.pow
import kotlin.math.roundToInt

/** Quietest a loud song is made, in dB. */
private const val MAX_CUT_DB = 24f

/** Loudest a quiet song is made, in dB. A boost can distort, so it stops well short of the cut. */
private const val MAX_BOOST_DB = 6f

/**
 * How many dB to change the volume by so a song with [gain] plays at [targetLufs], `0` when the
 * gain isn't known. A gain is the song's loudness over [REFERENCE_LUFS], positive for a louder
 * song; a song louder than the target is cut, a quieter one is boosted, up to [MAX_BOOST_DB].
 */
internal fun normalizationDb(gain: Float?, targetLufs: Float = REFERENCE_LUFS.toFloat()): Float =
    if (gain == null)
        0f
    else
        ((targetLufs - REFERENCE_LUFS).toFloat() - gain).coerceIn(-MAX_CUT_DB, MAX_BOOST_DB)

/**
 * The gain, over [REFERENCE_LUFS], of a song whose [replayGain] brings it to [taggedLufs]: a
 * ReplayGain of `-7.5` against -18 LUFS is a song at -10.5 LUFS, `3.5` over -14 LUFS.
 */
internal fun replayGainLoudness(replayGain: Float, taggedLufs: Int): Float =
    (taggedLufs - REFERENCE_LUFS).toFloat() - replayGain

/** The volume, from `0` to `1`, that lowers the sound by [db], which can't be positive. */
internal fun cutToVolume(db: Float): Float = 10f.pow(db.coerceAtMost(0f) / 20f)

/**
 * Evens out the loudness of songs by changing the volume of the player to suit the song playing.
 * The gain comes from the formats of the song, so a song that hasn't been measured plays as it is.
 * A song not stored yet has its formats in [formats] until it is, they are looked up there first,
 * so the gain is applied as soon as the song starts instead of once it is stored.
 *
 * The gain of a stored song is in the database, which answers after a cached or downloaded song
 * has started. So the gains of the songs before and after the one playing are read ahead and kept
 * in memory, and a song whose gain isn't in memory plays muted until the database has answered.
 *
 * A cut is made with the volume of the player. A boost is made by a [LoudnessEnhancer], which is
 * attached to the audio session of the player. The gain is followed while the song plays, so a
 * measurement that finishes meanwhile is applied at once.
 *
 * Songs are brought to [targetLufs]. OpenSubsonic's gain is the server's ReplayGain, read against
 * [replayGainLufs]. While [enabled] is off every song plays as it is. The settings are followed
 * while playing, a change applies to the song playing at once.
 *
 * Use it on the main thread, [scope] has to run there. Call [release] with the player.
 */
@OptIn(UnstableApi::class)
class LoudnessNormalizer(
    private val player: ExoPlayer,
    private val songs: SongRepository,
    private val formats: SongFormatRecorder,
    private val enabled: StateFlow<Boolean>,
    private val targetLufs: StateFlow<Float>,
    private val replayGainLufs: StateFlow<Int>,
    private val scope: CoroutineScope,
) : Player.Listener {

    private var enhancer: LoudnessEnhancer? = null
    private var following: Job? = null

    /** The format of the song playing that holds its gain. */
    private var gainFormat: SongFormat? = null

    /** Whether the gain of the song playing is known, or it has none. It plays muted until it is. */
    private var gainKnown = true

    /**
     * Gain formats of the songs before and after the one playing, `null` for a song with none,
     * read from the database ahead of their turn. Changed on the main thread only.
     */
    private val readAhead = HashMap<String, SongFormat?>()
    private var readingAhead: Job? = null
    private val settings = scope.launch {
        combine(enabled, targetLufs, replayGainLufs) { _, _, _ -> }.collect { apply() }
    }

    init {
        player.addListener(this)
        attach(player.audioSessionId)
        follow(player.currentMediaItem)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = follow(mediaItem)

    override fun onAudioSessionIdChanged(audioSessionId: Int) = attach(audioSessionId)

    override fun onTimelineChanged(timeline: Timeline, reason: Int) = readAhead()

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = readAhead()

    override fun onRepeatModeChanged(repeatMode: Int) = readAhead()

    fun release() {
        player.removeListener(this)
        following?.cancel()
        readingAhead?.cancel()
        settings.cancel()
        enhancer?.release()
        enhancer = null
    }

    private fun follow(item: MediaItem?) {
        following?.cancel()
        val songId = item?.mediaId
        // Never the last song's gain, the one of this song when it's in memory already: kept
        // while it isn't stored, or read ahead of its turn
        val pending = songId?.let { gainFormatOf(formats.pendingFormatsOf(it)) }
        gainKnown = songId == null || pending != null || songId in readAhead
        gainFormat = pending ?: songId?.let { readAhead[it] }
        apply()
        readAhead()

        songId ?: return
        following = scope.launch {
            try {
                // Its formats move from memory to the database once it is stored, for a moment
                // it has neither: a gain found stays until another one is
                merge(
                    songs.formatsOf(songId).map { it to true },
                    formats.pendingFormats(songId).map { it to false },
                ).collect { (found, fromDatabase) ->
                    val format = gainFormatOf(found)
                    if (format != null) gainFormat = format
                    // Memory without a gain doesn't tell, the database may still have one
                    if (format != null || fromDatabase) {
                        gainKnown = true
                        apply()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't read the gain of $songId" }
                // Never left muted
                gainKnown = true
                apply()
            }
        }
    }

    /**
     * Reads the gains of the songs before and after the one playing into [readAhead] and follows
     * them, so they are in memory when their turn comes. The ones of other songs are dropped.
     */
    private fun readAhead() {
        readingAhead?.cancel()
        val ids = listOf(player.previousMediaItemIndex, player.nextMediaItemIndex)
            .filter { it != C.INDEX_UNSET }
            .map { player.getMediaItemAt(it).mediaId }
            .toSet()
        readAhead.keys.retainAll(ids)
        if (ids.isEmpty()) return

        readingAhead = scope.launch {
            ids.forEach { id ->
                launch {
                    try {
                        songs.formatsOf(id).collect { readAhead[id] = gainFormatOf(it) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.w(e, TAG) { "Couldn't read the gain of $id ahead" }
                    }
                }
            }
        }
    }

    private fun gainFormatOf(formats: List<SongFormat>): SongFormat? =
        formats.firstOrNull { it.gain != null }

    /** Adjusts the volume to the song playing with the settings as they are now. */
    private fun apply() {
        val gain = gainFormat?.gain?.let {
            if (gainFormat?.tag == RAW_FORMAT) replayGainLoudness(it, replayGainLufs.value) else it
        }
        when {
            !enabled.value -> adjust(0f)
            gainKnown -> adjust(normalizationDb(gain, targetLufs.value))
            else -> mute()
        }
    }

    /** Silences the song until its gain is read, so it never starts at the wrong volume. */
    private fun mute() {
        player.volume = 0f
        Logger.d(tag = TAG) { "Muted until the gain is read" }
    }

    /**
     * Changes the volume by [db]. A cut goes through the player's volume and a boost through
     * the enhancer, never both, so the two don't add up.
     *
     * The cut doesn't use the enhancer too, although a negative target gain does scale the
     * signal down: the enhancer is a gain stage followed by a compressor, so it would also
     * squash loud peaks, and it may be missing on a device (a cut would then be lost). The
     * volume is a plain multiplication and always there.
     */
    private fun adjust(db: Float) {
        player.volume = cutToVolume(db)
        enhancer?.let {
            try {
                // Off when there is no boost, so the compressor isn't in the way for nothing
                it.enabled = db > 0f
                // The target gain is in millibels, hundredths of a dB
                if (db > 0f) it.setTargetGain((db * 100).roundToInt())
            } catch (e: RuntimeException) {
                Logger.w(e, TAG) { "Couldn't set the boost" }
            }
        }
        Logger.d(tag = TAG) { "Adjusting the volume by $db dB" }
    }

    /** The effect belongs to an audio session, it has to follow the player's. */
    private fun attach(audioSessionId: Int) {
        enhancer?.release()
        enhancer = null
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return

        enhancer = try {
            LoudnessEnhancer(audioSessionId)
        } catch (e: RuntimeException) {
            // Not every device has the effect, a cut still works
            Logger.w(e, TAG) { "No loudness enhancer on this device" }
            null
        }
        apply()
    }

    private companion object {

        const val TAG = "LoudnessNormalizer"
    }
}
