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

package me.knighthat.kreate3.ui.screen.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.knighthat.kreate3.backup.Backup
import me.knighthat.kreate3.backup.BackupException
import me.knighthat.kreate3.preferences.Profile
import me.knighthat.kreate3.preferences.ProfileManager
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What [BackupViewModel] is doing. */
enum class BackupTask { EXPORT, RESTORE }

/**
 * Saves a backup to a file the user picked, and restores one. The work runs on [scope], so it
 * finishes even when the screen is left.
 *
 * A backup holds every profile, so only the default profile may save or restore one.
 */
class BackupViewModel(
    private val context: Context,
    private val backup: Backup,
    private val scope: CoroutineScope,
    private val profileManager: ProfileManager,
) : ViewModel() {

    private val _working = MutableStateFlow<BackupTask?>(null)
    private val _messages = Channel<String>(Channel.BUFFERED)

    /** What is being done now, `null` when nothing. */
    val working: StateFlow<BackupTask?> = _working.asStateFlow()

    /** What came of a backup or restore, for the user to read. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Name suggested for a new backup, like `kreate_backup_1.0_2026-10-14_16-44-05.zip`. */
    fun fileName(): String {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        val time = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
        return "kreate_backup_${version}_$time.zip"
    }

    fun export(uri: Uri) = run(BackupTask.EXPORT, "Couldn't save the backup") {
        backup.export(openStream { context.contentResolver.openOutputStream(uri, "wt") })
        _messages.send("Backup saved")
    }

    /** Restores the backup at [uri], the app restarts when it can be restored. */
    fun restore(uri: Uri) = run(BackupTask.RESTORE, "Couldn't read the backup") {
        backup.stage(openStream { context.contentResolver.openInputStream(uri) })
        Backup.restart(context)
    }

    private fun <T> openStream(open: () -> T?): T =
        open() ?: throw FileNotFoundException()

    private fun run(task: BackupTask, failure: String, work: suspend () -> Unit) {
        if (_working.value != null) return
        if (profileManager.activeId.value != Profile.DEFAULT_ID) {
            _messages.trySend("Only the default profile can back up or restore")
            return
        }
        _working.value = task

        viewModelScope.launch {
            try {
                scope.async { work() }.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                _messages.send(e.message.orEmpty())
            } catch (_: IOException) {
                _messages.send(failure)
            } catch (_: SecurityException) {
                _messages.send(failure)
            } finally {
                _working.value = null
            }
        }
    }
}
