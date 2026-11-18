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

package me.knighthat.kreate3.ui.screen.settings.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.KreateApplication
import me.knighthat.kreate3.R
import me.knighthat.kreate3.api.PatreonFeature
import me.knighthat.kreate3.api.PatreonFileMissingException
import me.knighthat.kreate3.api.PatreonSignedOutException
import me.knighthat.kreate3.api.PatreonTierException
import me.knighthat.kreate3.api.RemoteFile
import me.knighthat.kreate3.backup.Backup
import me.knighthat.kreate3.backup.BackupException
import me.knighthat.kreate3.notification.AppChannel
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.patreon.PatreonAccount
import me.knighthat.kreate3.patreon.PatreonSession
import me.knighthat.kreate3.patreon.RemoteBackups
import java.io.FileNotFoundException
import java.io.IOException

private const val TAG = "RemoteBackups"

/** What the Remote tab of the Backup page shows. */
sealed interface RemoteBackupsState {

    data object SignedOut : RemoteBackupsState

    /** The tier doesn't have cloud backup. */
    data object Locked : RemoteBackupsState

    data object Loading : RemoteBackupsState

    /** The list couldn't be had, it may be tried again. */
    data object Failed : RemoteBackupsState

    /** @property files the newest first */
    data class Loaded(val files: List<RemoteFile>) : RemoteBackupsState
}

/**
 * The backups the user keeps online for the Remote tab, created when it opens: [RemoteBackups]
 * asks for them only when its token changed since (one request per page of 1000 files, the server
 * keeps 50 at most), again on [retry] after a failure. A tap [restore]s one; one can be [save]d
 * to a file or [delete]d too.
 */
class RemoteBackupsViewModel(
    private val context: Context,
    private val account: PatreonAccount,
    session: PatreonSession,
    private val backups: RemoteBackups,
    private val backup: Backup,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow<RemoteBackupsState>(RemoteBackupsState.Loading)
    private val _restoring = MutableStateFlow<Restoring?>(null)
    private val _deleting = MutableStateFlow<RemoteFile?>(null)
    private val _saving = MutableStateFlow<RemoteFile?>(null)
    private var loading: Job? = null

    /** A restore of [file] at [step]. */
    data class Restoring(val file: RemoteFile, val step: Step) {
        enum class Step { DOWNLOADING, READING }
    }

    /** The backup being restored, `null` while none is. */
    val restoring: StateFlow<Restoring?> = _restoring.asStateFlow()

    /** The backup being deleted, `null` while none is. */
    val deleting: StateFlow<RemoteFile?> = _deleting.asStateFlow()

    /** The backup being saved to a file the user picked, `null` while none is. */
    val saving: StateFlow<RemoteFile?> = _saving.asStateFlow()

    /** The backup whose file the user is picking now, for [save] once they did. */
    var toSave: RemoteFile? = null

    private val busy: Boolean
        get() = _restoring.value != null || _deleting.value != null || _saving.value != null

    val state: StateFlow<RemoteBackupsState> = _state.asStateFlow()

    /** Whether the membership is being asked again, see [checkMembership]. */
    val checking: StateFlow<Boolean> = account.checking

    init {
        viewModelScope.launch {
            // What keeps the list from loading, null when nothing does
            session.member
                .map {
                    when {
                        it == null -> RemoteBackupsState.SignedOut
                        !it.claims.has(PatreonFeature.BACKUPS) -> RemoteBackupsState.Locked
                        else -> null
                    }
                }
                .distinctUntilChanged()
                .collect { gate ->
                    loading?.cancel()
                    if (gate != null)
                        _state.value = gate
                    else
                        load()
                }
        }
    }

    /**
     * Asks for the tier again while it seems to lack cloud backup, the pledge may have just
     * changed: the list loads once the new token has it.
     */
    fun checkMembership() = account.checkMembership()

    /**
     * Restores [file], once the user agreed: downloaded unless a copy is kept, then restored like
     * a file picked on the Backup page, and the app restarts. Runs on the app's scope, so leaving
     * the page doesn't stop it.
     */
    fun restore(file: RemoteFile) {
        if (busy) return
        _restoring.value = Restoring(file, Restoring.Step.DOWNLOADING)

        viewModelScope.launch {
            try {
                scope.async {
                    val zip = try {
                        backups.download(file)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: PatreonFileMissingException) {
                        // Gone online already, it just leaves the list
                        drop(file)
                        return@async
                    } catch (e: IOException) {
                        Logger.w(e, TAG) { "Couldn't download the backup" }
                        notifier.post(
                            context.getString(R.string.error_remote_backup_download),
                            AppChannel.BACKUP,
                        )
                        return@async
                    }
                    _restoring.value = Restoring(file, Restoring.Step.READING)
                    zip.inputStream().use { backup.stage(it) }
                    (context.applicationContext as KreateApplication).restart()
                }.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                notifier.post(e.describe(context), AppChannel.BACKUP)
            } catch (e: IOException) {
                Logger.w(e, TAG) { "Couldn't restore the backup" }
                notifier.post(
                    context.getString(R.string.setting_description_restore_status_failed),
                    AppChannel.BACKUP,
                )
            } finally {
                _restoring.value = null
            }
        }
    }

    /**
     * Deletes [file] online once the user agreed, and takes it off the list without asking the
     * server for the list again. Runs on the app's scope, so leaving the page doesn't stop it.
     */
    fun delete(file: RemoteFile) {
        if (busy) return
        _deleting.value = file

        viewModelScope.launch {
            try {
                val deleted = scope.async { backups.delete(file) }.await()
                drop(file)
                // One gone already just leaves the list
                if (deleted) {
                    notifier.post(
                        context.getString(
                            R.string.snackbar_remote_backup_deleted,
                            context.getString(R.string.quoted, file.name)
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Logger.w(e, TAG) { "Couldn't delete the backup" }
                notifier.post(
                    context.getString(R.string.error_remote_backup_delete),
                    AppChannel.BACKUP,
                )
            } finally {
                _deleting.value = null
            }
        }
    }

    /**
     * Saves [file] to [target], a file the user picked, without restoring it: downloaded unless a
     * copy is kept. Runs on the app's scope, so leaving the page doesn't stop it.
     */
    fun save(file: RemoteFile, target: Uri) {
        if (busy) return
        _saving.value = file

        viewModelScope.launch {
            try {
                scope.async {
                    val zip = backups.download(file)
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(target, "wt")
                            ?: throw FileNotFoundException("Can't write to the file picked")
                        output.use { out -> zip.inputStream().use { it.copyTo(out) } }
                    }
                }.await()
                notifier.post(
                    context.getString(
                        R.string.snackbar_remote_backup_saved,
                        context.getString(R.string.quoted, file.name)
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: PatreonFileMissingException) {
                // Gone online already, it just leaves the list
                drop(file)
            } catch (e: IOException) {
                Logger.w(e, TAG) { "Couldn't save the backup" }
                tellSaveFailed()
            } catch (e: SecurityException) {
                Logger.w(e, TAG) { "Couldn't write the backup to the file picked" }
                tellSaveFailed()
            } finally {
                _saving.value = null
            }
        }
    }

    private fun tellSaveFailed() =
        notifier.post(context.getString(R.string.error_remote_backup_save), AppChannel.BACKUP)

    /** Takes [file] off the list shown, without asking for it again. */
    private fun drop(file: RemoteFile) {
        (_state.value as? RemoteBackupsState.Loaded)?.let {
            _state.value = RemoteBackupsState.Loaded(it.files - file)
        }
    }

    /** Asks for the list again after it failed. */
    fun retry() {
        if (_state.value == RemoteBackupsState.Failed) load()
    }

    private fun load() {
        loading?.cancel()
        _state.value = RemoteBackupsState.Loading
        loading = viewModelScope.launch {
            _state.value = try {
                RemoteBackupsState.Loaded(backups.list())
            } catch (e: CancellationException) {
                throw e
            } catch (e: PatreonTierException) {
                Logger.i(tag = TAG) { "The tier has no cloud backup: ${e.patronStatus}" }
                RemoteBackupsState.Locked
            } catch (_: PatreonSignedOutException) {
                RemoteBackupsState.SignedOut
            } catch (e: IOException) {
                Logger.w(e, TAG) { "Couldn't list the online backups" }
                RemoteBackupsState.Failed
            }
        }
    }
}
