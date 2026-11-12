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
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.legacy.LegacyImport
import me.knighthat.kreate3.database.legacy.LegacyImportException
import me.knighthat.kreate3.database.legacy.LegacyProfile
import me.knighthat.kreate3.download.LegacyDownloads
import me.knighthat.kreate3.download.parseLegacyDownloadIds
import me.knighthat.kreate3.preferences.PreferencesBackup
import me.knighthat.kreate3.preferences.Profile
import okio.buffer
import okio.source
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.uuid.Uuid

/**
 * `manifest.json` of an archive of the old app.
 *
 * @param format version of the layout of the archive
 * @param source always [LegacyBackup.SOURCE], tells it from a backup of this app
 * @param appVersion version of the old app that made it, only for people to read
 * @param profiles one entry per profile of the old app
 */
@Serializable
internal data class LegacyManifest(
    val format: Int,
    val source: String,
    val appVersion: String? = null,
    val profiles: List<LegacyProfileEntry>,
)

/**
 * A profile in the archive.
 *
 * @param name name of the profile
 * @param database path of its database in the archive, `profiles/<number>/data.db`
 * @param default whether it is the old app's `default` profile, which becomes the default profile
 * here; the others become new ones
 * @param schema schema version of the database, only for people to read
 */
@Serializable
internal data class LegacyProfileEntry(
    val name: String,
    val database: String,
    val default: Boolean = false,
    val schema: Int? = null,
)

/**
 * Brings the databases of the old app in, from one zip that holds all its profiles:
 *
 * ```
 * manifest.json              LegacyManifest
 * profiles/<number>/data.db  one database per profile
 * downloaded.json            optional, the YouTube Music ids the old app had downloaded
 * ```
 *
 * Everything here is replaced, as with a restore of a backup: the profiles of the archive become
 * the only profiles, with default settings, and the library is built from their databases (see
 * `LegacyImport`). Secrets stay where they are. It takes the same two steps as [Backup]: [stage]
 * keeps the result for [Backup.applyPendingRestore] to move in place on the next start.
 *
 * Entries other than the ones above are skipped, so a later version of the old app can add to
 * the archive.
 */
class LegacyBackup(
    private val context: Context,
    private val backup: Backup,
    private val preferences: PreferencesBackup,
) {

    /**
     * Checks the archive in [input], closes it, and keeps what it makes for the next start, with
     * a backup of what is there now to roll back to.
     *
     * @throws BackupException when it is not an archive of the old app, or can't be read
     */
    suspend fun stage(input: InputStream) =
        backup.stageBuilt { prepare(context, preferences, input, it) }

    internal companion object {

        const val SOURCE = "kreate-legacy"

        /**
         * Same as [stage] without a backup to roll back to, for an install with nothing to lose
         * yet, like during setup. It opens neither the database nor the preferences.
         */
        suspend fun stageWithoutRollback(context: Context, input: InputStream) {
            val preferences = PreferencesBackup(context)
            Backup.stageBuiltWithoutRollback(context) { prepare(context, preferences, input, it) }
        }

        /**
         * Whether the zip in [input] is an archive of the old app and not a backup of this app,
         * told by its manifest. Reads the stream as far as the manifest, it is not closed.
         */
        fun isLegacy(input: InputStream): Boolean = runCatching {
            val zip = ZipInputStream(input)
            generateSequence { zip.nextEntry }
                .firstOrNull { it.name == MANIFEST_ENTRY }
                ?.let {
                    val manifest = zip.source().buffer()
                    // A manifest is small, a bigger one is not one of ours. Reads up to the entry's
                    // end or one byte past the limit
                    manifest.request(MAX_MANIFEST_BYTES + 1L)
                    if (manifest.buffer.size > MAX_MANIFEST_BYTES) return@runCatching false
                    val text = manifest.buffer.readUtf8()
                    JSON.parseToJsonElement(text).jsonObject["source"]?.jsonPrimitive?.contentOrNull == SOURCE
                } == true
        }.getOrDefault(false)

        /**
         * Makes what [Backup.applyPendingRestore] needs in [target], an empty folder: `kreate.db`
         * and the list of profiles.
         */
        private suspend fun prepare(
            context: Context,
            preferences: PreferencesBackup,
            input: InputStream,
            target: File,
        ) = withContext(Dispatchers.IO) {
            Logger.i(tag = TAG) { "Reading an archive of the old app" }
            val work = File(context.cacheDir, WORK_DIRECTORY)
            try {
                work.deleteRecursively()
                work.mkdirs()
                val manifest = unpack(input, work)

                // The default profile keeps its id, which is how it is told from the others
                val profiles = manifest.profiles.map {
                    LegacyProfile(
                        id = if (it.default) Profile.DEFAULT_ID else Uuid.random().toString(),
                        // The old app's `default` is not a name anyone chose
                        name = if (it.default) DEFAULT_NAME else it.name.ifBlank { DEFAULT_NAME },
                        database = File(work, it.database),
                    )
                }
                // Songs on this list keep their place in the library, to be offered again
                val downloaded = readDownloads(File(work, DOWNLOADS_ENTRY))
                try {
                    LegacyImport.build(
                        context,
                        profiles,
                        File(target, DATABASE_ENTRY),
                        downloaded.toSet()
                    )
                } catch (e: LegacyImportException) {
                    Logger.w(e, TAG) { "A database of the archive can't be read" }
                    throw BackupException(R.string.error_backup_damaged)
                }
                preferences.writeProfileList(target, profiles.map { it.id })
                keepDownloads(downloaded, File(target, LegacyDownloads.FILE_NAME))
                Logger.i(tag = TAG) { "Imported ${profiles.size} profile(s) of the old app" }
            } finally {
                work.deleteRecursively()
            }
        }

        private const val TAG = "LegacyBackup"
        private const val FORMAT = 1
        private const val MAX_MANIFEST_BYTES = 64 * 1024
        private const val WORK_DIRECTORY = "legacy_import"
        private const val MANIFEST_ENTRY = "manifest.json"
        private const val DOWNLOADS_ENTRY = "downloaded.json"
        private const val DATABASE_ENTRY = "kreate.db"
        private const val DEFAULT_NAME = "Default"

        private val DATABASE_PATH = Regex("profiles/[0-9]+/data\\.db")
        private val JSON = Json { ignoreUnknownKeys = true }

        /** The ids of `downloaded.json`, none when it is missing or can't be read. */
        private fun readDownloads(source: File): List<String> =
            source.takeIf(File::isFile)?.let { parseLegacyDownloadIds(it.readText()) }.orEmpty()

        /**
         * Keeps [ids] for the user to decide on after the restore, see [LegacyDownloads]. An
         * empty list is not kept, downloads are optional.
         */
        private fun keepDownloads(ids: List<String>, target: File) {
            if (ids.isEmpty()) return
            target.writeText(Json.encodeToString(ids))
        }

        /**
         * Unpacks the files [LegacyManifest] names from [input] into [target], and checks them.
         * Only paths of the layout are written, so nothing goes outside of [target].
         */
        internal fun unpack(input: InputStream, target: File): LegacyManifest {
            val notAnArchive =
                BackupException(R.string.error_backup_not_a_backup, R.string.app_name)

            ZipInputStream(input.buffered()).use { zip ->
                generateSequence { zip.nextEntry }
                    .filterNot(ZipEntry::isDirectory)
                    .filter {
                        it.name == MANIFEST_ENTRY || it.name == DOWNLOADS_ENTRY || DATABASE_PATH.matches(
                            it.name
                        )
                    }
                    .forEach { entry ->
                        val file = File(target, entry.name)
                        file.parentFile?.mkdirs()
                        file.outputStream().use { zip.copyTo(it) }
                    }
            }

            val manifest = File(target, MANIFEST_ENTRY)
                .takeIf(File::isFile)
                ?.let { runCatching { JSON.decodeFromString<LegacyManifest>(it.readText()) }.getOrNull() }
                ?.takeIf { it.source == SOURCE }
                ?: throw notAnArchive
            if (manifest.format > FORMAT) throw BackupException(R.string.error_backup_newer_version)

            val paths = manifest.profiles.map { it.database }
            if (manifest.profiles.isEmpty()
                || manifest.profiles.count { it.default } > 1
                || paths.toSet().size != paths.size
            )
                throw BackupException(R.string.error_backup_damaged)

            val header = ByteArray(Backup.SQLITE_HEADER.size)
            for (path in paths) {
                // Only what was unpacked is named, anything else is damage
                val file = File(target, path).takeIf { DATABASE_PATH.matches(path) && it.isFile }
                val read = file?.inputStream()?.use { it.read(header) }
                if (read != header.size || !header.contentEquals(Backup.SQLITE_HEADER))
                    throw BackupException(R.string.error_backup_damaged)
            }
            return manifest
        }
    }
}
