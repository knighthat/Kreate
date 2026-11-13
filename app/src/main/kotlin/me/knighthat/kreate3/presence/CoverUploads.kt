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

package me.knighthat.kreate3.presence

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.core.graphics.scale
import co.touchlab.kermit.Logger
import coil3.size.Precision
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.auto.cover
import me.knighthat.kreate3.preferences.Preferences
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

private const val TAG = "CoverUploads"

/** Litterbox, catbox.moe's host of files that are gone after a while. */
private const val UPLOAD_URL = "https://litterbox.catbox.moe/resources/internals/api.php"

/** How long litterbox keeps an upload, one of the times it offers. */
private const val KEPT_FOR = "1h"

/** How long an upload is used again: litterbox keeps it an hour, a margin before it's gone. */
private val REUSED_FOR = 55.minutes

/** How long a cover whose upload failed isn't tried again, litterbox may refuse for a while. */
private val RETRY_AFTER = 10.minutes

/** JPEG qualities tried in turn, until the cover fits. */
private val QUALITIES = intArrayOf(90, 80, 70, 60, 50, 40)

/** What a cover is shrunk by when even the lowest quality doesn't fit. */
private const val SHRINK = 0.75f

/** Sizes of a cover uploaded for Discord, and the stops of their settings. */
internal object DiscordCovers {

    /** Stops of [Preferences.DISCORD_COVER_SIZE], in pixels. */
    val resolutions: IntProgression = 256..1536 step 256

    /** The stop of [Preferences.DISCORD_COVER_SIZE] its slider fills from, Discord's advice. */
    const val ADVISED_RESOLUTION = 1024

    /**
     * Stops of the file size settings, like [Preferences.DISCORD_COVER_BYTES_WIFI]: 128 KiB to
     * 1 MiB in units of 1024 when [binary], 100 kB to 1 MB otherwise.
     */
    fun sizes(binary: Boolean): List<Int> =
        if (binary) (1..8).map { it * 131_072 } else (1..10).map { it * 100_000 }

    /** The stop of [sizes] closest to [bytes]. */
    fun snap(bytes: Int, binary: Boolean): Int = sizes(binary).minBy { abs(it - bytes) }

    /**
     * Moves both file size settings to the closest stops of [binary]'s, for when
     * [Preferences.BINARY_SIZES] changes.
     */
    fun snapSettings(binary: Boolean) {
        listOf(Preferences.DISCORD_COVER_BYTES_WIFI, Preferences.DISCORD_COVER_BYTES_MOBILE)
            .forEach { it.update(snap(it.value, binary)) }
    }
}

/**
 * Covers that only the app can open (the OpenSubsonic server's, the device's) made into
 * addresses Discord can fetch: drawn at most [Preferences.DISCORD_COVER_SIZE], saved as a JPEG
 * no larger than the network's limit, uploaded to litterbox for an hour. A cover uploaded in the
 * last [REUSED_FOR] is used again: covers are told apart by an MD5 of their pixels, taken before
 * the JPEG is made. One whose upload failed waits [RETRY_AFTER] before it's tried again. What was
 * uploaded is remembered in memory only.
 *
 * One upload per cover per hour at most, a request each.
 */
class CoverUploads internal constructor(
    private val context: Context,
    private val http: HttpClient,
) {

    private val mutex = Mutex()
    private val uploads = HashMap<String, Upload>()
    private val failures = HashMap<String, TimeSource.Monotonic.ValueTimeMark>()

    /**
     * An address for the first of [models] (what the image loader takes) that loads, `null` when
     * none does or the upload failed. One upload at most.
     */
    suspend fun publicUrl(models: List<Any>): String? {
        val resolution = Preferences.DISCORD_COVER_SIZE.value
            .coerceIn(DiscordCovers.resolutions.first, DiscordCovers.resolutions.last)
        // A smaller picture stays as it is
        val bitmap = models.firstNotNullOfOrNull {
            context.cover(it, resolution, Precision.INEXACT)
        } ?: return null
        val hash = withContext(Dispatchers.Default) { bitmap.md5() }

        return mutex.withLock {
            uploads[hash]?.takeIf { it.at.elapsedNow() < REUSED_FOR }
                ?.let { return@withLock it.url }
            if (failures[hash]?.let { it.elapsedNow() < RETRY_AFTER } == true) return@withLock null

            val limit = DiscordCovers.snap(
                if (isMetered()) Preferences.DISCORD_COVER_BYTES_MOBILE.value
                else Preferences.DISCORD_COVER_BYTES_WIFI.value,
                Preferences.BINARY_SIZES.value,
            )
            val jpeg = withContext(Dispatchers.Default) { bitmap.jpegWithin(limit) }
            val url = upload(jpeg)
            if (url == null) {
                failures[hash] = TimeSource.Monotonic.markNow()
                return@withLock null
            }
            failures.remove(hash)
            uploads.entries.removeAll { (_, upload) -> upload.at.elapsedNow() >= REUSED_FOR }
            uploads[hash] = Upload(url, TimeSource.Monotonic.markNow())
            url
        }
    }

    private suspend fun upload(jpeg: ByteArray): String? = try {
        val response = http.submitFormWithBinaryData(
            UPLOAD_URL,
            formData {
                append("reqtype", "fileupload")
                append("time", KEPT_FOR)
                // With its file name in the part's own header, PHP takes it for a file then
                append("fileToUpload", "cover.jpg", ContentType.Image.JPEG, jpeg.size.toLong()) {
                    write(jpeg)
                }
            },
        )
        val answer = response.bodyAsText().trim()
        if (response.status.isSuccess() && answer.startsWith("https://")) {
            Logger.i(tag = TAG) { "Uploaded a cover of ${jpeg.size} bytes" }
            answer
        } else {
            Logger.w(tag = TAG) { "Litterbox didn't take a cover: ${response.status}" }
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.w(e, TAG) { "Couldn't upload a cover" }
        null
    }

    private fun isMetered(): Boolean =
        context.getSystemService<ConnectivityManager>()?.isActiveNetworkMetered ?: false

    private class Upload(val url: String, val at: TimeSource.Monotonic.ValueTimeMark)
}

/** MD5 of the picture's size and pixels, to tell covers apart. */
private fun Bitmap.md5(): String {
    val pixels = ByteBuffer.allocate(byteCount)
    copyPixelsToBuffer(pixels)
    val digest = MessageDigest.getInstance("MD5")
    digest.update("${width}x$height".toByteArray())
    digest.update(pixels.array())
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * The picture as a JPEG of [limit] bytes at most: the quality goes down until it fits, then the
 * picture shrinks. The smallest one tried when nothing fits.
 */
private fun Bitmap.jpegWithin(limit: Int): ByteArray {
    var picture = this
    while (true) {
        var smallest: ByteArray? = null
        for (quality in QUALITIES) {
            val jpeg = ByteArrayOutputStream().also {
                picture.compress(Bitmap.CompressFormat.JPEG, quality, it)
            }.toByteArray()
            if (jpeg.size <= limit) return jpeg
            smallest = jpeg
        }
        val width = (picture.width * SHRINK).toInt()
        val height = (picture.height * SHRINK).toInt()
        if (width < 64 || height < 64) return smallest!!
        picture = picture.scale(width, height)
    }
}
