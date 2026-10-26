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
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
internal fun normalizationDb(gain: Float?, targetLufs: Int = REFERENCE_LUFS.toInt()): Float =
    if (gain == null) 0f
    else ((targetLufs - REFERENCE_LUFS).toFloat() - gain).coerceIn(-MAX_CUT_DB, MAX_BOOST_DB)

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
    private val targetLufs: StateFlow<Int>,
    private val replayGainLufs: StateFlow<Int>,
    private val scope: CoroutineScope,
) : Player.Listener {

    private var enhancer: LoudnessEnhancer? = null
    private var following: Job? = null

    /** The format of the song playing that holds its gain. */
    private var gainFormat: SongFormat? = null
    private var adjustmentDb = 0f
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

    fun release() {
        player.removeListener(this)
        following?.cancel()
        settings.cancel()
        enhancer?.release()
        enhancer = null
    }

    private fun follow(item: MediaItem?) {
        following?.cancel()
        val songId = item?.mediaId
        // Never the last song's gain, the one of this song when it's in memory already, like
        // when it was read ahead of the song before
        gainFormat = songId?.let { gainFormatOf(formats.pendingFormatsOf(it)) }
        apply()

        songId ?: return
        following = scope.launch {
            // Its formats move from memory to the database once it is stored, for a moment it
            // has neither: a gain found stays until another one is
            merge(songs.formatsOf(songId), formats.pendingFormats(songId)).collect { found ->
                gainFormatOf(found)?.let {
                    gainFormat = it
                    apply()
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
        adjust(if (enabled.value) normalizationDb(gain, targetLufs.value) else 0f)
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
        adjustmentDb = db
        player.volume = cutToVolume(db)
        enhancer?.let {
            try {
                // Off when there is no boost, so the compressor isn't in the way for nothing
                it.enabled = db > 0f
                // The target gain is in millibels, hundredths of a dB
                if (db > 0f) it.setTargetGain((db * 100).roundToInt())
            } catch (e: RuntimeException) {
                Log.w(TAG, "Couldn't set the boost", e)
            }
        }
        Log.d(TAG, "Adjusting the volume by $db dB")
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
            Log.w(TAG, "No loudness enhancer on this device", e)
            null
        }
        adjust(adjustmentDb)
    }

    private companion object {

        const val TAG = "LoudnessNormalizer"
    }
}
