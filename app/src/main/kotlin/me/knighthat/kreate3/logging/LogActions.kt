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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.notification.Notifier
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the Logging settings do with the log files, each tells the user how it went. */
internal object LogActions {

    /** Name suggested for an export, like `kreate_logs_2026-11-04_16-44-05.zip`. */
    fun exportName(): String =
        "kreate_logs_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())}.zip"

    /** Copies the latest part of the log, see [LogFiles.latest], to the clipboard. */
    suspend fun copy(context: Context, notifier: Notifier) {
        AppLog.flush()
        val text = withContext(Dispatchers.IO) { LogFiles.latest(LogFiles.logDirectory(context)) }
        if (text.isEmpty()) {
            notifier.post(context.getString(R.string.snackbar_log_empty))
            return
        }

        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Kreate log", text))
        notifier.post(context.getString(R.string.snackbar_log_copied))
    }

    /** Writes every log file to [uri] as a zip. */
    suspend fun export(context: Context, uri: Uri, notifier: Notifier) {
        AppLog.flush()
        val saved = try {
            withContext(Dispatchers.IO) {
                val output = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("No stream for the file")
                LogFiles.zip(LogFiles.logDirectory(context), output)
            }
            true
        } catch (e: IOException) {
            co.touchlab.kermit.Logger.w(e, "LogActions") { "Couldn't export the log files" }
            false
        }
        notifier.post(
            context.getString(if (saved) R.string.snackbar_log_saved else R.string.snackbar_log_save_failed)
        )
    }
}
