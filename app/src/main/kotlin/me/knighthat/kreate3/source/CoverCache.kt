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

package me.knighthat.kreate3.source

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Size
import coil3.size.pxOrElse
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.roundToInt

/** Side of the covers of rows and cards, which are drawn smaller. */
internal const val SMALL_COVER = 512

/** Side of the covers of the player and the detail screens, about as tall as most phones draw it. */
internal const val LARGE_COVER = 1440

/** Quality of a cover encoded on the device, a little above what servers scale with. */
private const val COVER_QUALITY = 80

/**
 * Side of the cover to load for a picture drawn at [size], [SMALL_COVER] only when it is drawn
 * smaller than that. Coil then marks the picture it draws as scaled down, so a larger one asked
 * for later loads [LARGE_COVER] instead of stretching it.
 */
internal fun coverSize(size: Size): Int {
    val drawn = maxOf(size.width.pxOrElse { 0 }, size.height.pxOrElse { 0 })
    return if (drawn in 1..<SMALL_COVER) SMALL_COVER else LARGE_COVER
}

/** The longer side of the picture in [bytes], -1 when they aren't a picture. */
internal fun pictureSide(bytes: ByteArray): Int {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    return maxOf(bounds.outWidth, bounds.outHeight)
}

/**
 * The power of two to divide a [width] by [height] picture by while both sides stay at least
 * [side], or as they are when the picture is smaller.
 */
internal fun coverSampleSize(width: Int, height: Int, side: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= side && height / (sample * 2) >= side) sample *= 2
    return sample
}

/**
 * The picture in [original] no larger than [side], encoded as a JPEG, the fastest to encode and
 * to decode, or as a PNG when it has transparency. A picture that already fits is kept as it is
 * when that is smaller.
 *
 * @return `null` when [original] isn't a picture
 */
internal fun encodeCover(original: ByteArray, side: Int): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
    val decode = BitmapFactory.Options().apply {
        inSampleSize = coverSampleSize(bounds.outWidth, bounds.outHeight, side)
    }
    val bitmap = BitmapFactory.decodeByteArray(original, 0, original.size, decode) ?: return null

    val scale = side.toFloat() / maxOf(bitmap.width, bitmap.height)
    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).roundToInt().coerceAtLeast(1),
        (bitmap.height * scale).roundToInt().coerceAtLeast(1),
        true,
    ) else bitmap
    val encoded = try {
        val format =
            if (scaled.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        ByteArrayOutputStream().also { scaled.compress(format, COVER_QUALITY, it) }.toByteArray()
    } finally {
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
    }

    val fits = maxOf(bounds.outWidth, bounds.outHeight) <= side
    return if (fits && original.size <= encoded.size) original else encoded
}

/**
 * The cover saved under [key], `null` when there is none or the request doesn't read the disk
 * cache.
 */
internal fun DiskCache?.readCover(key: String, options: Options): SourceFetchResult? {
    if (this == null || !options.diskCachePolicy.readEnabled) return null
    return openSnapshot(key)?.let { coverResult(it, key, DataSource.DISK) }
}

/**
 * Saves [bytes] under [key] and reads them back from there. When they can't be saved, like
 * while another request saves the same cover or without a [key], they are read from memory.
 */
internal fun DiskCache?.saveCover(
    key: String?,
    bytes: ByteArray,
    options: Options,
    dataSource: DataSource,
): SourceFetchResult {
    if (this != null && key != null && options.diskCachePolicy.writeEnabled)
        save(key, bytes)?.let { return coverResult(it, key, dataSource) }
    return SourceFetchResult(
        source = ImageSource(Buffer().write(bytes), options.fileSystem),
        mimeType = null,
        dataSource = dataSource,
    )
}

private fun DiskCache.save(key: String, bytes: ByteArray): DiskCache.Snapshot? {
    val editor = openEditor(key) ?: return null
    return try {
        fileSystem.write(editor.data) { write(bytes) }
        editor.commitAndOpenSnapshot()
    } catch (_: IOException) {
        try {
            editor.abort()
        } catch (_: IOException) {
        }
        null
    }
}

private fun DiskCache.coverResult(
    snapshot: DiskCache.Snapshot,
    key: String,
    dataSource: DataSource,
) = SourceFetchResult(
    source = ImageSource(snapshot.data, fileSystem, key, snapshot),
    mimeType = null,
    dataSource = dataSource,
)
