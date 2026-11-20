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

package me.knighthat.kreate3.patreon

import android.content.Context
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.api.PatreonFeature
import me.knighthat.kreate3.api.PatreonFileMissingException
import me.knighthat.kreate3.api.PatreonFiles
import me.knighthat.kreate3.api.RemoteFile
import me.knighthat.kreate3.preferences.GlobalPreferences
import me.knighthat.kreate3.preferences.Secrets
import java.io.File
import java.io.IOException

private const val TAG = "RemoteBackups"

/**
 * The backups kept online, asked only when the Remote tab of the Backup page opens and kept in
 * memory with the token they were asked with: they're asked again only once the token changed
 * (a refresh, another sign in), or after a failure.
 *
 * A backup is downloaded to [downloads] to restore it, and that copy is used again the next time.
 * Each time the list is asked, the copies of backups no longer online are deleted. A backup
 * deleted from here, or found gone online when downloading it, leaves the files kept without
 * asking for the list again.
 */
class RemoteBackups internal constructor(
    private val context: Context,
    private val account: PatreonAccount,
    session: PatreonSession,
    private val files: PatreonFiles,
    scope: CoroutineScope,
) {

    private class Listed(val token: String, val files: List<RemoteFile>)

    @Volatile
    private var listed: Listed? = null

    /**
     * The downloaded backups, in the app's folder of external storage (internal when there is
     * none), never in Android's own backup or device transfer (`backup_rules.xml`).
     */
    private val downloads: File
        get() = (context.getExternalFilesDir(null) ?: context.filesDir).resolve(DOWNLOADS)

    /**
     * Whether cloud backup is on: signed in, the tier has [PatreonFeature.BACKUPS] and
     * [GlobalPreferences.CLOUD_BACKUP] is on.
     */
    val active: StateFlow<Boolean> =
        combine(session.member, GlobalPreferences.CLOUD_BACKUP.state) { member, on ->
            on && member?.claims?.has(PatreonFeature.BACKUPS) == true
        }.stateIn(scope, SharingStarted.Eagerly, false)

    /** Forgets the files kept, for after one was uploaded. */
    fun forget() {
        listed = null
    }

    /**
     * The files, the newest first: those kept when the token is still the one they were asked
     * with, else asked again (see [PatreonAccount.withToken] for what it throws).
     */
    suspend fun list(): List<RemoteFile> {
        val token = Secrets.PATREON_TOKEN.current()
        listed?.takeIf { it.token == token }?.let { return it.files }

        val list = account.withToken { used ->
            files.list(used)
                .sortedByDescending { it.lastModified }
                .also { listed = Listed(used, it) }
        }
        deleteGone(list)
        return list
    }

    /**
     * The backup [file], downloaded when there is no copy of it yet (one of its size), else the
     * copy downloaded before (see [PatreonAccount.withToken] for what it throws).
     */
    suspend fun download(file: RemoteFile): File {
        require(file.name.matches(NAME)) { "Not a name the API gives" }
        val target = downloads.resolve(file.name)
        val kept = withContext(Dispatchers.IO) { target.isFile && target.length() == file.size }
        if (kept) {
            Logger.i(tag = TAG) { "Using the backup downloaded before" }
            return target
        }

        val data = try {
            account.withToken { files.download(it, file.name) }
        } catch (e: PatreonFileMissingException) {
            // Gone online already, like after the 180 days
            forget(file)
            throw e
        }
        withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()
            // Under another name until it's whole, a half written copy is never used
            val part = downloads.resolve("${file.name}$PART")
            part.writeBytes(data)
            if (!part.renameTo(target)) throw IOException("Couldn't keep the downloaded backup")
        }
        Logger.i(tag = TAG) { "Downloaded a backup" }
        return target
    }

    /**
     * Deletes [file] online, for good (see [PatreonAccount.withToken] for what it throws), then
     * [forget]s it.
     *
     * @return `false` when it was gone online already
     */
    suspend fun delete(file: RemoteFile): Boolean {
        require(file.name.matches(NAME)) { "Not a name the API gives" }
        val deleted = account.withToken { files.delete(it, file.name) }
        Logger.i(tag = TAG) {
            if (deleted) "Deleted a backup online" else "The backup was gone online already"
        }
        forget(file)
        return deleted
    }

    /**
     * Takes [file] off the files kept, without asking for the list again, and deletes its
     * downloaded copy: for one deleted, or found gone, online.
     */
    private suspend fun forget(file: RemoteFile) {
        listed?.let { listed = Listed(it.token, it.files - file) }
        withContext(Dispatchers.IO) {
            downloads.resolve(file.name).delete()
            // A download of it cut short too
            downloads.resolve("${file.name}$PART").delete()
        }
    }

    /** Deletes the copies of backups that aren't in [online] anymore, and unfinished ones. */
    private suspend fun deleteGone(online: List<RemoteFile>) = withContext(Dispatchers.IO) {
        val names = online.mapTo(mutableSetOf()) { it.name }
        downloads.listFiles().orEmpty()
            .filter { it.name !in names }
            .forEach {
                if (it.deleteRecursively())
                    Logger.i(tag = TAG) { "Deleted a downloaded backup no longer online" }
            }
    }

    companion object {

        private const val DOWNLOADS = "cloud_backups"
        private const val PART = ".part"

        /** What the API allows, so a name never leaves the folder. */
        private val NAME = Regex("[A-Za-z0-9_-][A-Za-z0-9._-]{0,127}")
    }
}
