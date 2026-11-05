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

package me.knighthat.kreate3.logging

import android.content.Context
import java.io.File
import java.io.OutputStream
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Where the app's log files are and what can be done with them as a whole.
 *
 * Logs go to the external cache folder, `Android/data/<app id>/cache/logs`, which the user can
 * open with a file manager on most devices and which the system may clear when it needs room.
 * Crash logs are kept longer, in `Android/data/<app id>/files/crashlogs`. Neither is backed up.
 * Both fall back to the app's private folders without external storage.
 */
internal object LogFiles {

    const val CURRENT_NAME = "kreate.log"

    /** How an entry of the log starts, see `FileLogWriter`. */
    const val TIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"

    /** The most [tail] reads, a clipboard can't take much more. */
    const val COPY_LIMIT_BYTES = 200 * 1024

    fun logDirectory(context: Context): File =
        (context.externalCacheDir ?: context.cacheDir).resolve("logs")

    fun crashDirectory(context: Context): File =
        (context.getExternalFilesDir(null) ?: context.filesDir).resolve("crashlogs")

    /** `kreate.log`, then `kreate.1.log` (the newest of the rolled over ones) and so on. */
    fun current(directory: File) = File(directory, CURRENT_NAME)

    fun rolled(directory: File, index: Int) = File(directory, "kreate.$index.log")

    /** The log files that exist, the newest first. */
    fun all(directory: File): List<File> {
        val files = directory.listFiles { file ->
            file.isFile && file.name.startsWith("kreate") && file.name.endsWith(".log")
        }
            .orEmpty()
        return files.sortedByDescending { it.lastModified() }
    }

    /**
     * The end of [file], at most [limit] bytes and starting on a line. Empty when it can't be read.
     */
    fun tail(file: File, limit: Int = COPY_LIMIT_BYTES): String = try {
        file.inputStream().use { input ->
            val length = file.length()
            if (length > limit) input.skip(length - limit)
            val text = input.readBytes().toString(Charsets.UTF_8)
            if (length > limit) text.substringAfter('\n', text) else text
        }
    } catch (_: Exception) {
        ""
    }

    /**
     * The latest of the log: the current file, with the end of the one before when the current one
     * is too short to hold [limit].
     */
    fun latest(directory: File, limit: Int = COPY_LIMIT_BYTES): String {
        val files = all(directory)
        val builder = StringBuilder()
        for (file in files.take(2).reversed()) builder.append(tail(file, limit))
        return builder.toString().takeLast(limit)
    }

    /**
     * The entries of the latest of the log written at or after [sinceMillis] and before
     * [untilMillis], at most [limit] bytes
     * of them. An entry starts with its time (`yyyy-MM-dd HH:mm:ss.SSS`), the lines after it, like a
     * stack trace, belong to it.
     */
    fun latestSince(
        directory: File,
        sinceMillis: Long,
        limit: Int = COPY_LIMIT_BYTES,
        untilMillis: Long = Long.MAX_VALUE,
    ): String {
        val format = SimpleDateFormat(TIME_FORMAT, Locale.ROOT)
        val kept = StringBuilder()
        var keeping = false
        latest(directory, limit).lineSequence().forEach { line ->
            val time = line.takeIf { it.length > TIME_FORMAT.length }
                ?.let { format.parse(it.substring(0, TIME_FORMAT.length), ParsePosition(0)) }
            // A line that doesn't start with a time continues the entry before it
            if (time != null) keeping = time.time in sinceMillis until untilMillis
            if (keeping) kept.append(line).append('\n')
        }
        return kept.toString().trimEnd('\n')
    }

    /** Writes every log file to [output] as a zip. Returns how many files it holds. */
    fun zip(directory: File, output: OutputStream): Int {
        val files = all(directory)
        ZipOutputStream(output).use { zip ->
            files.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return files.size
    }
}
