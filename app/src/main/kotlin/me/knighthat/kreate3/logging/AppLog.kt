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
import android.os.Process
import android.os.SystemClock
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.knighthat.kreate3.constant.LogLevel
import me.knighthat.kreate3.preferences.GlobalPreferences
import me.knighthat.kreate3.preferences.Preferences
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The app's log: [Logger] (Kermit) writing to Logcat and to files, see [LogFiles].
 *
 * [install] runs first thing when the process starts, [follow] once the settings can be read.
 * Until then nothing is dropped, what was logged waits and is filtered with the level the user
 * picked, so what a migration says while the settings load is not lost.
 *
 * Log texts are English only, they are never translated. Every text goes through [redact] before
 * it is written.
 */
object AppLog {

    private var writer: FileLogWriter? = null

    /**
     * Sets up Logcat and file logging. Safe to call again, the first call wins.
     */
    fun install(context: Context) {
        if (writer != null) return
        val file = FileLogWriter(LogFiles.logDirectory(context))
        writer = file
        Logger.setLogWriters(file)
        // Before the level is known every message is let through to wait for it
        Logger.setMinSeverity(Severity.Verbose)
    }

    /**
     * Applies the log settings now and whenever they change, once they are loaded.
     */
    fun follow(scope: CoroutineScope) {
        scope.launch {
            // A file that never loads leaves the defaults
            withTimeoutOrNull(LOAD_TIMEOUT_MILLIS) { Preferences.awaitLoaded() }
            combine(
                GlobalPreferences.LOG_LEVEL.state,
                GlobalPreferences.LOG_ROLLOVER_SIZE.state,
                GlobalPreferences.LOG_KEEP.state,
            ) { level, size, keep -> Triple(level, size, keep) }
                .collect { (level, size, keep) ->
                    // Info stays on whatever the level is: the crash log keeps recent activity, see recent
                    Logger.setMinSeverity(minOf(level.severity, Severity.Info))
                    writer?.configure(level.severity, size, keep)
                }
        }
    }

    /**
     * What was logged most recently in memory, for the crash log: the last [RECENT_ENTRIES]
     * entries at the user's level or Info, whichever is more detailed. They are never written to
     * the files for it, the files follow the user's level.
     */
    internal fun recent(): RecentLog = writer?.recent() ?: RecentLog(null, "")

    /** When this run of the app (this process) started, in epoch milliseconds. */
    internal fun sessionStart(): Long =
        System.currentTimeMillis() - (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())

    /** Waits, for at most [timeoutMillis], until what was logged so far is in the file. */
    fun flush(timeoutMillis: Long = 1_000) {
        writer?.flush(timeoutMillis)
    }

    internal val LogLevel.severity: Severity
        get() = when (this) {
            LogLevel.VERBOSE -> Severity.Verbose
            LogLevel.DEBUG -> Severity.Debug
            LogLevel.INFO -> Severity.Info
            LogLevel.WARNING -> Severity.Warn
            LogLevel.ERROR -> Severity.Error
        }

    private const val LOAD_TIMEOUT_MILLIS = 10_000L
}

/** How many entries [AppLog.recent] keeps. */
internal const val RECENT_ENTRIES = 200

/**
 * @param oldestMillis when the oldest entry of [text] was logged, `null` without any
 * @param text the entries as they are written to the log files
 */
internal class RecentLog(val oldestMillis: Long?, val text: String)

/**
 * Writes each message to Logcat and appends it to `kreate.log` in [directory], rolling the file
 * over when it grows past the size setting.
 *
 * Writing happens on a thread of its own at background priority, a call only formats nothing and
 * queues. A queue that is full (the disk stalled) drops messages rather than make the caller wait.
 */
internal class FileLogWriter(private val directory: File) : LogWriter() {

    private val logcat = platformLogWriter()

    private class Entry(
        val time: Long,
        val severity: Severity,
        val tag: String,
        val thread: String,
        val text: String,
    )

    // The user's level once it is known, read by every thread that logs
    @Volatile
    private var userLevel: Severity? = null

    // The latest entries, see AppLog.recent
    private val recent = ArrayDeque<Entry>()

    private val executor = ThreadPoolExecutor(
        1, 1, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue(QUEUE_SIZE)
    ) { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "LogWriter").apply { isDaemon = true }
    }

    // Everything below belongs to the executor's thread
    private var minimum: Severity? = null
    private var rolloverBytes = DEFAULT_ROLLOVER_BYTES
    private var keep = DEFAULT_KEEP
    private val waiting = ArrayList<Entry>()
    private var output: FileOutputStream? = null
    private var size = 0L
    private val format = SimpleDateFormat(LogFiles.TIME_FORMAT, Locale.ROOT)

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val text = redact(
            if (throwable == null) message else message + "\n" + throwable.stackTraceToString()
        )
        val level = userLevel
        // Logger lets Info through whatever the level is, for the crash log
        if (level == null || severity >= level) logcat.log(severity, text, tag, null)

        val entry =
            Entry(System.currentTimeMillis(), severity, tag, Thread.currentThread().name, text)
        // The user's level, or Info when that is less detailed
        if (severity >= minOf(level ?: Severity.Info, Severity.Info)) {
            synchronized(recent) {
                recent.addLast(entry)
                if (recent.size > RECENT_ENTRIES) recent.removeFirst()
            }
        }
        try {
            executor.execute { write(entry) }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // Full, dropped
        }
    }

    fun configure(minimum: Severity, rolloverBytes: Long, keep: Int) {
        userLevel = minimum
        try {
            executor.execute {
                this.minimum = minimum
                this.rolloverBytes = rolloverBytes.coerceAtLeast(MIN_ROLLOVER_BYTES)
                this.keep = keep.coerceAtLeast(0)
                // What waited for the level
                waiting.filter { it.severity >= minimum }.forEach(::append)
                waiting.clear()
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
        }
    }

    fun recent(): RecentLog {
        val entries = synchronized(recent) { recent.toList() }
        if (entries.isEmpty()) return RecentLog(null, "")
        val format = SimpleDateFormat(LogFiles.TIME_FORMAT, Locale.ROOT)
        return RecentLog(entries.first().time, entries.joinToString("") { it.line(format) })
    }

    fun flush(timeoutMillis: Long) {
        val done = CountDownLatch(1)
        try {
            executor.execute {
                output?.flush()
                done.countDown()
            }
            done.await(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
        }
    }

    private fun write(entry: Entry) {
        val level = minimum
        if (level == null) {
            // Not known yet, kept for when it is
            if (waiting.size < WAITING_LIMIT) waiting += entry
        } else if (entry.severity >= level) {
            append(entry)
        }
    }

    private fun append(entry: Entry) {
        try {
            val line = entry.line(format).toByteArray(Charsets.UTF_8)

            if (size > 0 && size + line.size > rolloverBytes) rollOver()
            val stream = output ?: open()
            stream.write(line)
            stream.flush()
            size += line.size
        } catch (_: Exception) {
            // No room or no folder: logging never takes the app down, Logcat still has it
            close()
        }
    }

    private fun open(): FileOutputStream {
        directory.mkdirs()
        val file = LogFiles.current(directory)
        size = file.length()
        return FileOutputStream(file, true).also { output = it }
    }

    private fun close() {
        try {
            output?.close()
        } catch (_: Exception) {
        }
        output = null
    }

    /** `kreate.log` becomes `kreate.1.log`, the others move up and those past [keep] go. */
    private fun rollOver() {
        close()
        // Files left by a larger setting
        (keep + 1..keep + EXTRA_ROLLED).forEach { LogFiles.rolled(directory, it).delete() }
        if (keep > 0) {
            for (index in keep downTo 2) {
                val older = LogFiles.rolled(directory, index - 1)
                if (older.exists()) older.renameTo(LogFiles.rolled(directory, index))
            }
            LogFiles.current(directory).renameTo(LogFiles.rolled(directory, 1))
        } else {
            LogFiles.current(directory).delete()
        }
        size = 0
    }

    private fun Entry.line(format: SimpleDateFormat): String = buildString {
        append(format.format(Date(time)))
        append(' ')
        append(severity.letter)
        append('/')
        append(tag)
        append(" [")
        append(thread)
        append("]: ")
        append(text)
        append('\n')
    }

    private val Severity.letter: Char
        get() = when (this) {
            Severity.Verbose -> 'V'
            Severity.Debug -> 'D'
            Severity.Info -> 'I'
            Severity.Warn -> 'W'
            Severity.Error -> 'E'
            Severity.Assert -> 'A'
        }

    private companion object {

        const val QUEUE_SIZE = 4_000
        const val WAITING_LIMIT = 2_000
        const val DEFAULT_ROLLOVER_BYTES = 1024L * 1024
        const val MIN_ROLLOVER_BYTES = 16L * 1024
        const val DEFAULT_KEEP = 5

        // How far past the current setting rolled files are looked for to delete
        const val EXTRA_ROLLED = 30
    }
}
