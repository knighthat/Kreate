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

package me.knighthat.kreate3.recognition

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.shazam.Shazam
import java.io.IOException
import java.net.URLDecoder
import kotlin.concurrent.thread
import kotlin.time.Duration

private const val TAG = "ShazamSongRecognizer"

/** Asked of the microphone first; Shazam works at this rate, nothing to convert. */
private const val PREFERRED_RATE = 16_000

/** The rate every device must record at, when [PREFERRED_RATE] isn't offered. */
private const val FALLBACK_RATE = 44_100

/** The most sound kept, past the time listening stops on its own. */
private const val MAX_SECONDS = 20

/**
 * Records the microphone as raw PCM and hands it to [Shazam] to name. Recording runs on a thread of
 * its own, from [start] until [finish] or [cancel].
 */
class ShazamSongRecognizer(
    private val context: Context,
    private val shazam: Shazam,
) : SongRecognizer {

    private var recording: Recording? = null

    override fun start() {
        recording?.discard()
        recording = Recording.start()
    }

    override fun cancel() {
        recording?.discard()
        recording = null
    }

    override suspend fun finish(heard: Duration): RecognizedSong? {
        val recording = recording ?: throw IllegalStateException("Not listening")
        this.recording = null
        val (pcm, rate) = withContext(Dispatchers.IO) { recording.stop() }
        Logger.d(tag = TAG) { "Recorded ${pcm.size} samples at $rate Hz, held for $heard" }

        val locale = context.resources.configuration.locales[0]
        return shazam.recognize(pcm, rate, locale)?.let {
            RecognizedSong(
                title = it.title,
                artist = it.artist,
                album = it.album,
                cover = it.cover?.toUri(),
                explicit = it.explicit,
                pageUrl = it.shazamUrl,
                searchQuery = it.youTubeMusicSearch?.let(::searchTerms)
                    ?: "${it.title} ${it.artist}",
            )
        }
    }

    /** The `q` of Shazam's YouTube Music search address, what Shazam itself searches for. */
    private fun searchTerms(address: String): String? =
        address.toUri().encodedQuery
            ?.split('&')
            ?.firstOrNull { it.startsWith("q=") }
            // A form value: `+` is a space, a real one is %2B, which Uri's own decoding mixes up
            ?.let { URLDecoder.decode(it.removePrefix("q="), "UTF-8") }
            ?.takeIf { it.isNotBlank() }

    /** One listening: the microphone read into memory by a thread until stopped. */
    private class Recording private constructor(
        private val record: AudioRecord?,
        private val rate: Int,
        /** Why the microphone couldn't be listened to, thrown on [stop]. */
        private val failure: Exception?,
    ) {

        private val samples = ShortArray(rate * MAX_SECONDS)

        @Volatile
        private var size = 0

        @Volatile
        private var stopped = false
        private val reader = record?.let {
            thread(name = "SongRecording") {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val chunk = ShortArray(rate / 10)
                while (!stopped && size < samples.size) {
                    val read = it.read(chunk, 0, minOf(chunk.size, samples.size - size))
                    if (read <= 0) break
                    chunk.copyInto(samples, size, 0, read)
                    size += read
                }
            }
        }

        /** Stops listening. @return what was heard and its rate */
        fun stop(): Pair<ShortArray, Int> {
            if (failure != null) throw failure
            end()
            return samples.copyOf(size) to rate
        }

        /** Stops listening and lets go of the sound. */
        fun discard() = end()

        private fun end() {
            stopped = true
            val record = record ?: return
            runCatching { record.stop() }
            reader?.join()
            record.release()
        }

        companion object {

            // The caller checked the permission, a missing one fails the start below
            @SuppressLint("MissingPermission")
            fun start(): Recording {
                for (rate in listOf(PREFERRED_RATE, FALLBACK_RATE)) {
                    val minimum = AudioRecord.getMinBufferSize(
                        rate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    if (minimum <= 0) continue

                    val record = try {
                        AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            rate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            maxOf(minimum, rate) // Half a second of 16-bit samples
                        )
                    } catch (e: Exception) {
                        Logger.w(e, TAG) { "Couldn't open the microphone at $rate Hz" }
                        continue
                    }
                    if (record.state != AudioRecord.STATE_INITIALIZED) {
                        record.release()
                        continue
                    }
                    try {
                        record.startRecording()
                    } catch (e: Exception) {
                        record.release()
                        return Recording(null, rate, IOException("The microphone didn't start", e))
                    }
                    return Recording(record, rate, null)
                }
                return Recording(null, PREFERRED_RATE, IOException("No microphone to listen to"))
            }
        }
    }
}
