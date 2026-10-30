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

package me.knighthat.kreate3.backup

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.DatabaseBackup
import me.knighthat.kreate3.preferences.PreferencesBackup
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * What a backup holds, so a restore can refuse what this app can't read.
 *
 * @param format version of the zip layout
 * @param appVersion version code of the app that made it
 * @param database schema version of the database
 * @param globalPreferences schema version of the global preferences file
 * @param profilePreferences schema version of the profile preferences files
 */
@Serializable
internal data class BackupManifest(
    val format: Int,
    val appVersion: Long,
    val database: Int,
    val globalPreferences: Int,
    val profilePreferences: Int,
)

/**
 * A backup that can't be restored, [messageRes] tells the user why, with the strings of
 * [argsRes] in its placeholders.
 */
class BackupException(
    @param:StringRes val messageRes: Int,
    @param:StringRes private vararg val argsRes: Int,
) : Exception() {

    fun describe(context: Context): String =
        context.getString(messageRes, *argsRes.map(context::getString).toTypedArray())
}

/**
 * Saves the library and the preferences of every profile to a zip, and restores them from one.
 * Secrets stay on the device and are never in a backup.
 *
 * Layout of the zip: `manifest.json`, `kreate.db`, then the preference files by their paths in
 * [PreferencesBackup].
 *
 * A restore takes two steps. [stage] checks the zip, unpacks it beside the app's data and backs up
 * what is there now to the cache folder. Then [applyPendingRestore] moves it in place on the next
 * start of the process, before anything opens the files, or rolls back to that backup on the start
 * after when it fails.
 */
class Backup(
    private val context: Context,
    private val database: DatabaseBackup,
    private val preferences: PreferencesBackup,
) {

    /**
     * Writes a backup to [output] and closes it.
     */
    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) {
        val snapshot = File(context.cacheDir, "backup_$DATABASE_ENTRY")
        try {
            database.export(snapshot)
            ZipOutputStream(output.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(JSON.encodeToString(currentManifest(context)).toByteArray())
                zip.closeEntry()

                zip.putFile(DATABASE_ENTRY, snapshot)
                preferences.files().forEach { (path, file) -> zip.putFile(path, file) }
            }
        } finally {
            snapshot.delete()
        }
    }

    /**
     * Checks the backup in [input], closes it, and keeps it for [applyPendingRestore], with a
     * backup of what is there now to roll back to. A backup kept before is replaced.
     *
     * @throws BackupException when it is not a backup, or one this app can't read
     */
    suspend fun stage(input: InputStream) = withContext(Dispatchers.IO) {
        val rollback = File(context.cacheDir, ROLLBACK_FILE)
        try {
            keep(context) { partial ->
                unpack(input, partial, preferences)
                export(rollback.outputStream())
            }
        } catch (e: Exception) {
            rollback.delete()
            throw e
        }
    }

    companion object {

        private const val TAG = "Backup"

        private const val FORMAT = 1

        private const val MANIFEST_ENTRY = "manifest.json"
        private const val DATABASE_ENTRY = "kreate.db"

        internal const val PENDING_DIRECTORY = "restore"

        /** Backup of what was there before a restore, in the cache folder. */
        internal const val ROLLBACK_FILE = "rollback.zip"

        private val NOT_A_BACKUP = R.string.error_backup_not_a_backup

        private val JSON = Json { ignoreUnknownKeys = true }

        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray()

        /**
         * Same as [stage] without a backup to roll back to, for an install with nothing to lose
         * yet, like during setup. It opens neither the database nor the preferences.
         *
         * @throws BackupException when it is not a backup, or one this app can't read
         */
        suspend fun stageWithoutRollback(context: Context, input: InputStream) =
            withContext(Dispatchers.IO) {
                val preferences = PreferencesBackup(context)
                keep(context) { unpack(input, it, preferences) }
            }

        /**
         * Moves a backup kept by [stage] in place. Call it when the process starts, before the
         * database or any preference is opened.
         *
         * When that fails, the backup made by [stage] is kept in its place, so the next start
         * rolls back to what was there before. A failed roll back is not tried again.
         */
        fun applyPendingRestore(context: Context) {
            val pending = File(context.noBackupFilesDir, PENDING_DIRECTORY)
            val rollback = File(context.cacheDir, ROLLBACK_FILE)
            if (!pending.isDirectory) {
                // Left by a stage that didn't finish
                rollback.delete()
                return
            }

            try {
                DatabaseBackup.restore(context, File(pending, DATABASE_ENTRY))
                PreferencesBackup(context).restore(pending)
                pending.deleteRecursively()
            } catch (e: Exception) {
                Log.e(TAG, "Restoring the backup failed", e)

                // Deleted first, so a failure here can't restart the app into the same failure
                pending.deleteRecursively()
                if (rollback.isFile)
                    try {
                        val preferences = PreferencesBackup(context)
                        keep(context) { unpack(rollback.inputStream(), it, preferences) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Keeping the roll back failed", e)
                    }
            } finally {
                rollback.delete()
            }
        }

        internal fun currentManifest(context: Context) = BackupManifest(
            format = FORMAT,
            appVersion = PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(context.packageName, 0)
            ),
            database = DatabaseBackup.VERSION,
            globalPreferences = PreferencesBackup.GLOBAL_VERSION,
            profilePreferences = PreferencesBackup.PROFILE_VERSION,
        )

        /**
         * Unpacks the backup in [input] to [target], and checks it can be restored.
         * [target] is emptied first, and left with whatever was unpacked when it throws.
         */
        internal fun unpack(input: InputStream, target: File, preferences: PreferencesBackup) {
            target.deleteRecursively()
            target.mkdirs()

            ZipInputStream(input.buffered()).use { zip ->
                generateSequence { zip.nextEntry }
                    .filterNot(ZipEntry::isDirectory)
                    .forEach { entry ->
                        // Only known paths, so nothing is written outside of target
                        val path = entry.name
                        if (path != MANIFEST_ENTRY && path != DATABASE_ENTRY && !preferences.isBackupPath(
                                path
                            )
                        )
                            throw BackupException(NOT_A_BACKUP, R.string.app_name)

                        val file = File(target, path)
                        file.parentFile?.mkdirs()
                        file.outputStream().use { zip.copyTo(it) }
                    }
            }

            val manifest = File(target, MANIFEST_ENTRY)
                .takeIf(File::isFile)
                ?.let { runCatching { JSON.decodeFromString<BackupManifest>(it.readText()) }.getOrNull() }
                ?: throw BackupException(NOT_A_BACKUP, R.string.app_name)
            if (manifest.format > FORMAT
                || manifest.database > DatabaseBackup.VERSION
                || manifest.globalPreferences > PreferencesBackup.GLOBAL_VERSION
                || manifest.profilePreferences > PreferencesBackup.PROFILE_VERSION
            )
                throw BackupException(R.string.error_backup_newer_version)

            val database = File(target, DATABASE_ENTRY)
            val header = ByteArray(SQLITE_HEADER.size)
            val read = database.takeIf(File::isFile)?.inputStream()?.use { it.read(header) }
            if (read != header.size || !header.contentEquals(SQLITE_HEADER) || !preferences.isRestorable(
                    target
                )
            )
                throw BackupException(R.string.error_backup_damaged)
        }

        /**
         * Runs [fill] on an empty folder, then keeps that folder for [applyPendingRestore] in
         * place of the one kept before.
         */
        private inline fun keep(context: Context, fill: (File) -> Unit) {
            val partial = File(context.noBackupFilesDir, "$PENDING_DIRECTORY.partial")
            val pending = File(context.noBackupFilesDir, PENDING_DIRECTORY)
            try {
                fill(partial)
                pending.deleteRecursively()
                check(partial.renameTo(pending)) { "Couldn't keep the backup" }
            } finally {
                partial.deleteRecursively()
            }
        }

        private fun ZipOutputStream.putFile(path: String, file: File) {
            putNextEntry(ZipEntry(path))
            file.inputStream().use { it.copyTo(this) }
            closeEntry()
        }
    }
}
