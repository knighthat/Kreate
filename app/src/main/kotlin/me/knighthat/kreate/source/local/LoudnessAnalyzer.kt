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

package me.knighthat.kreate.source.local

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.database.model.SongFormat
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes an audio file from start to end and measures its loudness with [LoudnessMeter].
 * Decoding a whole song takes a few seconds, call it off the main thread.
 */
class LoudnessAnalyzer(private val context: Context) {

    /**
     * The format of the local song [songId], whose file is at [uri], from its first audio
     * track. The bitrate is the average over the file without its cover art when the track doesn't
     * state one. `null` when the file has no audio track, is silent, or decodes to samples of a kind
     * the meter can't read.
     *
     * @throws IOException when the file can't be opened
     */
    suspend fun analyze(songId: String, uri: Uri): SongFormat? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(track)
            val mimetype = format.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(track)

            val loudness = decode(extractor, format, mimetype) ?: return null
            val length = context.contentResolver.openFileDescriptor(uri, "r")
                ?.use { it.statSize.takeIf { size -> size >= 0 } }
            return SongFormat(
                songId = songId,
                tag = Origin.LOCAL.name,
                mimetype = mimetype,
                bitrate = format.integerOrNull(MediaFormat.KEY_BIT_RATE)
                    ?: averageBitrate(length?.minus(coverSize(uri)), format),
                length = length,
                gain = (loudness - REFERENCE_LUFS).toFloat(),
            )
        } finally {
            extractor.release()
        }
    }

    private suspend fun decode(
        extractor: MediaExtractor,
        format: MediaFormat,
        mimetype: String,
    ): Double? {
        val codec = MediaCodec.createDecoderByType(mimetype)
        try {
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var meter: LoudnessMeter? = null
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var samples = FloatArray(0)
            var inputDone = false

            while (true) {
                currentCoroutineContext().ensureActive()

                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                index,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (index < 0) continue

                if (meter == null) {
                    // The decoder says what it puts out, it may differ from the file
                    val output = codec.outputFormat
                    meter = LoudnessMeter(
                        output.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        output.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    )
                    encoding = output.integerOrNull(MediaFormat.KEY_PCM_ENCODING)
                        ?: AudioFormat.ENCODING_PCM_16BIT
                }

                val buffer = codec.getOutputBuffer(index)!!
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                val count = when (encoding) {
                    AudioFormat.ENCODING_PCM_16BIT -> info.size / 2
                    AudioFormat.ENCODING_PCM_FLOAT -> info.size / 4
                    else -> return null
                }
                if (samples.size < count) samples = FloatArray(count)
                buffer.readSamples(encoding, samples, count)
                meter.add(samples, count)
                codec.releaseOutputBuffer(index, false)

                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }

            return meter?.integratedLufs()
        } finally {
            codec.release()
        }
    }

    private fun ByteBuffer.readSamples(encoding: Int, into: FloatArray, count: Int) {
        order(ByteOrder.nativeOrder())
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT)
            asFloatBuffer().get(into, 0, count)
        else {
            val shorts = asShortBuffer()
            for (i in 0 until count) into[i] = shorts.get(i) / 32768f
        }
    }

    /**
     * Bytes of the picture embedded in the file, `0` when there is none or it can't be read.
     */
    private fun coverSize(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.size?.toLong() ?: 0L
        } catch (_: RuntimeException) {
            0L
        } finally {
            retriever.release()
        }
    }

    /**
     * Bits per second over [length] bytes of audio, for formats like Opus that don't state one.
     * Pass the file size without its cover art. Tags are left in, so it reads slightly high.
     */
    private fun averageBitrate(length: Long?, format: MediaFormat): Int? {
        if (length == null || !format.containsKey(MediaFormat.KEY_DURATION)) return null
        val seconds = format.getLong(MediaFormat.KEY_DURATION) / 1_000_000.0
        if (seconds <= 0) return null
        return (length * 8 / seconds).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun MediaFormat.integerOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private companion object {

        const val TIMEOUT_US = 10_000L
    }
}
