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

package me.knighthat.kreate3.database.legacy

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.database.AppDatabase
import me.knighthat.kreate3.database.entity.SongPlaylistMapEntity
import me.knighthat.kreate3.database.inTransaction
import java.io.File
import kotlin.time.Clock

/**
 * One database of the old app, whose data becomes the user [id].
 *
 * @property id id of the user to create, the same as the id of the profile it belongs to
 * @property name name of the user
 * @property database the old app's database file of that profile
 */
class LegacyProfile(val id: String, val name: String, val database: File)

/**
 * What an import wrote, totals over every user.
 *
 * @property users users created
 * @property songs different songs stored
 * @property playlists playlists created
 */
class LegacyImportResult(val users: Int, val songs: Int, val playlists: Int)

/**
 * Builds a new database from the databases of the old app, one per profile there, one user each
 * here. What is mapped and what is left out is in [mapLegacyLibrary].
 *
 * The result replaces the app's database whole, nothing is merged with what is there: build it
 * in a file of its own, then restore it like a backup (`DatabaseBackup.restore`).
 */
object LegacyImport {

    private const val TAG = "LegacyImport"

    /**
     * Writes a new database to [target], replacing the file, with [profiles]. A database that
     * can't be read stops the import and leaves no file.
     *
     * @throws LegacyImportException when a database of [profiles] is not one of the old app
     */
    suspend fun build(
        context: Context,
        profiles: List<LegacyProfile>,
        target: File
    ): LegacyImportResult =
        build(context, profiles, target, BundledSQLiteDriver(), Clock.System)

    internal suspend fun build(
        context: Context,
        profiles: List<LegacyProfile>,
        target: File,
        driver: SQLiteDriver,
        clock: Clock,
    ): LegacyImportResult = withContext(Dispatchers.IO) {
        require(profiles.map { it.id }.toSet().size == profiles.size) { "Two profiles share an id" }

        Logger.i(tag = TAG) { "Importing ${profiles.size} profile(s) of the old app" }
        target.parentFile?.mkdirs()
        deleteWithJournals(target)

        // One file when it's done, with no journal beside it
        val database = Room.databaseBuilder<AppDatabase>(context, target.path)
            .setDriver(driver)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            val now = clock.now()
            val seenSongs = HashSet<String>()
            val seenAlbums = HashSet<String>()
            val seenArtists = HashSet<String>()
            var playlistCount = 0

            for (profile in profiles) {
                val library = try {
                    driver.open(profile.database.path).use { it.readLegacyLibrary() }
                } catch (e: LegacyImportException) {
                    throw e
                } catch (e: Exception) {
                    throw LegacyImportException("A database can't be read", e)
                }
                val mapped = mapLegacyLibrary(profile.id, profile.name, library, now)

                // A song, album or artist of an earlier profile stays as it was stored
                val albums = mapped.albums.filter { seenAlbums.add(it.id) }
                val artists = mapped.artists.filter { seenArtists.add(it.id) }
                val songs = mapped.songs.filter { seenSongs.add(it.id) }

                database.inTransaction {
                    database.userDao().upsert(mapped.user)
                    database.albumDao().insertAll(albums)
                    database.artistDao().insertAll(artists)
                    database.songDao().upsertAll(songs)
                    database.songDao().linkAll(mapped.songArtists)
                    database.userSongDao().insertAll(mapped.userSongs)
                    database.userAlbumDao().insertAll(mapped.userAlbums)
                    database.userArtistDao().insertAll(mapped.userArtists)
                    mapped.formats.forEach { database.songFormatDao().upsert(it) }
                    database.playbackHistoryDao().insertAll(mapped.plays)
                    mapped.searches.forEach { database.searchHistoryDao().upsert(it) }

                    for (entry in mapped.playlists) {
                        val id = database.userPlaylistDao().upsert(entry.playlist)
                        database.userPlaylistDao().addSongs(
                            entry.songs.map { (songId, position) ->
                                SongPlaylistMapEntity(songId, id, position)
                            }
                        )
                    }
                }
                playlistCount += mapped.playlists.size
            }

            LegacyImportResult(profiles.size, seenSongs.size, playlistCount)
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Importing the old databases failed" }
            database.close()
            deleteWithJournals(target)
            throw e
        } finally {
            database.close()
        }
    }

    private fun deleteWithJournals(file: File) =
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
}
