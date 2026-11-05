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
import android.content.Intent
import android.os.Build
import android.os.Process
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.knighthat.kreate3.BuildConfig
import me.knighthat.kreate3.preferences.GlobalPreferences
import me.knighthat.kreate3.preferences.Preferences
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Catches what no code caught, saves it as a crash log in [LogFiles.crashDirectory] and opens
 * [CrashActivity] with it in a process of its own, then ends the app's process: it can't be
 * trusted to go on.
 *
 * Crash logs are written whatever the log level is. They hold the app and device versions, the
 * stack trace and the end of the log, all English and all through [redact].
 */
internal object CrashHandler {

    private const val TAG = "CrashHandler"
    private const val LOG_TAIL_BYTES = 64 * 1024

    private val handling = AtomicBoolean(false)

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // A crash while handling one, or a second thread crashing meanwhile, goes the old way
            if (!handling.compareAndSet(false, true)) {
                previous?.uncaughtException(thread, throwable)
                return@setDefaultUncaughtExceptionHandler
            }

            val saved = try {
                handle(app, thread, throwable)
            } catch (_: Throwable) {
                null
            }

            if (saved == null) {
                previous?.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    /** Writes the crash log and opens the screen for it. Returns the log, `null` if it failed. */
    private fun handle(context: Context, thread: Thread, throwable: Throwable): File? {
        // The log's end is read before the crash is logged, the report holds the trace once
        AppLog.flush()
        val directory = LogFiles.crashDirectory(context)
        directory.mkdirs()
        val now = Date()
        val file = File(
            directory,
            "crash-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(now)}.log"
        )
        file.writeText(report(context, thread, throwable, now))

        // For the log files, which have no other trace of why the app stopped
        Logger.e(throwable, TAG) { "Uncaught exception on thread ${thread.name}, see ${file.name}" }
        AppLog.flush()

        context.startActivity(
            Intent(context, CrashActivity::class.java)
                .putExtra(CrashActivity.EXTRA_FILE, file.path)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        return file
    }

    private fun report(context: Context, thread: Thread, throwable: Throwable, time: Date): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT)
        val text = buildString {
            appendLine("Time: ${format.format(time)}")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.BUILD_TYPE}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendLine(throwable.stackTraceToString())
            val recent = AppLog.recent()
            // What the files kept from before the recent entries, which hold the rest
            val earlier = LogFiles.latestSince(
                LogFiles.logDirectory(context),
                AppLog.sessionStart(),
                LOG_TAIL_BYTES,
                untilMillis = recent.oldestMillis ?: Long.MAX_VALUE,
            )
            // No heading over nothing
            if (earlier.isNotEmpty()) {
                appendLine()
                appendLine("----- Earlier in this session -----")
                append(earlier)
                appendLine()
            }
            if (recent.text.isNotEmpty()) {
                appendLine()
                appendLine("----- Recent activity, up to $RECENT_ENTRIES entries -----")
                append(recent.text)
            }
        }
        return redact(text)
    }

    /** Whether this is the process of [CrashActivity], which runs none of the app's startup. */
    fun isCrashProcess(): Boolean = try {
        File("/proc/self/cmdline").readText().trim('\u0000').endsWith(":crash")
    } catch (_: Exception) {
        false
    }

    /**
     * Deletes the crash logs older than [GlobalPreferences.CRASH_LOG_RETENTION_DAYS] once the
     * settings are loaded, run when the app starts.
     */
    fun deleteExpired(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            withTimeoutOrNull(10_000) { Preferences.awaitLoaded() }
            val days = GlobalPreferences.CRASH_LOG_RETENTION_DAYS.value
            val deleted = deleteOlderThan(context, days)
            if (deleted > 0) Logger.i(tag = TAG) { "Deleted $deleted crash logs older than $days days" }
        }
    }

    /**
     * Deletes the crash logs older than [days] days. Returns how many it deleted.
     */
    fun deleteOlderThan(context: Context, days: Int): Int {
        val limit = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        val old = LogFiles.crashDirectory(context)
            .listFiles { file -> file.isFile && file.lastModified() < limit }
            .orEmpty()
        return old.count { it.delete() }
    }
}
