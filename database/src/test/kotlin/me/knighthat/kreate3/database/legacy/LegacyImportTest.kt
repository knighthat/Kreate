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

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.database.TestClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
internal class LegacyImportTest {

    private val directory: File = Files.createTempDirectory("legacy").toFile()
    private val driver = AndroidSQLiteDriver()
    private val clock = TestClock()
    private val target = File(directory, "kreate.db")

    @After
    fun deleteDirectory() {
        directory.deleteRecursively()
    }

    /** An old database of version 36 with [fill] run on it. */
    private fun legacy(name: String, fill: SQLiteConnection.() -> Unit): File {
        val file = File(directory, name)
        driver.open(file.path).use { connection ->
            SCHEMA.forEach(connection::execSQL)
            connection.fill()
        }
        return file
    }

    private fun import(vararg profiles: LegacyProfile, downloaded: Set<String> = emptySet()) =
        runBlocking {
            LegacyImport.build(
                RuntimeEnvironment.getApplication(),
                profiles.toList(),
                target,
                driver,
                clock,
                downloaded,
            )
        }

    /** Rows of the new database, every value as text. */
    private fun rows(sql: String): List<List<String?>> =
        driver.open(target.path).use { connection ->
            connection.prepare(sql).use { statement ->
                buildList {
                    while (statement.step())
                        add(List(statement.getColumnCount()) { index ->
                            if (statement.isNull(index)) null else statement.getText(index)
                        })
                }
            }
        }

    @Test
    fun mapsAProfile() {
        val file = legacy("data.db") {
            execSQL(
                """
                INSERT INTO songs VALUES
                  ('liked', 'Liked', 'A', '3:25', 'http://t/1', 5000, 1000, 1, 0),
                  ('hated', 'Hated', 'A', '215', NULL, -1, 0, 0, 0),
                  ('typed', 'modified:My title', 'modified:My artist', NULL, NULL, NULL, 0, 0, 0),
                  ('lonely', 'Lonely', 'Typed text', NULL, NULL, NULL, 500, 0, 0),
                  ('untouched', 'Untouched', 'Old text', NULL, NULL, NULL, 0, 0, 0),
                  ('cover', 'Cover', NULL, NULL, 'modified:http://mine', NULL, 0, 0, 0),
                  ('saved', 'Saved', NULL, NULL, NULL, NULL, 0, 0, 0),
                  ('device', 'On device', NULL, NULL, NULL, 9, 0, 0, 1)
                """.trimIndent()
            )
            execSQL("INSERT INTO artists VALUES ('ar', 'Artist', NULL, 100, 7000, 1), ('42', 'Local', NULL, NULL, NULL, 0), ('UCflagless', 'Flag unset', NULL, NULL, NULL, 0)")
            execSQL("INSERT INTO albums VALUES ('al', 'Album', NULL, '2019', 'Artist', 'http://a', 200, NULL, 1)")
            execSQL("INSERT INTO song_artist_map VALUES ('liked', 'ar'), ('hated', 'ar'), ('typed', 'ar'), ('untouched', 'ar'), ('device', '42')")
            execSQL("INSERT INTO song_album_map VALUES ('liked', 'al', 3)")
            execSQL("INSERT INTO playlists VALUES ('modified:Mix', 'PLabcdefghijkl', 1, 1, 4, 1, 0)")
            execSQL("INSERT INTO song_playlist_map VALUES ('hated', 4, 2, NULL), ('liked', 4, 0, NULL), ('device', 4, 1, NULL), ('untouched', 4, 3, NULL)")
            execSQL("INSERT INTO formats VALUES ('liked', 140, 'audio/mp4', 128000, 5000000, 1, 3.5), ('typed', NULL, 'audio/mp4', NULL, NULL, NULL, NULL)")
            execSQL("INSERT INTO playback_history VALUES ('liked', 1000000, 90000, 1), ('device', 2000000, 1000, 2)")
            execSQL("INSERT INTO search_history VALUES ('first', 1), ('second', 2)")
            execSQL("INSERT INTO persistent_queue VALUES ('liked', 1)")
            execSQL("INSERT INTO lyrics VALUES ('liked', 'plain', NULL)")
        }

        val result =
            import(LegacyProfile("default", "Default", file), downloaded = setOf("saved", "absent"))

        assertEquals(1, result.users)
        assertEquals(7, result.songs)
        assertEquals(1, result.playlists)
        assertEquals(listOf(listOf("default", "Default")), rows("SELECT id, name FROM user"))

        // Songs: device songs are left out, a length is whole seconds, `false` is unknown
        assertEquals(
            listOf(
                listOf("cover", "Cover", null, null, null, "YOUTUBE"),
                listOf("hated", "Hated", "215", null, null, "YOUTUBE"),
                listOf("liked", "Liked", "205", "1", "al", "YOUTUBE"),
                listOf("lonely", "Lonely", null, null, null, "YOUTUBE"),
                listOf("saved", "Saved", null, null, null, "YOUTUBE"),
                listOf("typed", "My title", null, null, null, "YOUTUBE"),
                listOf("untouched", "Untouched", null, null, null, "YOUTUBE"),
            ),
            rows("SELECT id, name, duration, is_explicit, album_id, origin FROM song ORDER BY id"),
        )
        assertEquals("3", rows("SELECT album_position FROM song WHERE id = 'liked'")[0][0])

        // The user's rows: likes and dislikes as they were, typed texts as overrides. Only songs the
        // user touched (typed text, a like or dislike, play time) or downloaded get one
        assertEquals(
            listOf(
                listOf("cover", null, null, null, "http://mine"),
                listOf("hated", "-1", null, null, null),
                listOf("liked", "5000", null, null, null),
                // Played, no artist row for it: the old text is all there is
                listOf("lonely", null, null, """["Typed text"]""", null),
                // Downloaded: no artist row either
                listOf("saved", null, null, null, null),
                listOf("typed", null, "My title", """["My artist"]""", null),
            ),
            rows("SELECT song_id, liked_at, name, artists, thumbnail FROM user_song ORDER BY song_id"),
        )

        // The `youtube` flag is not what tells a device artist, its numeric id is
        assertEquals(
            listOf(listOf("UCflagless", "Flag unset"), listOf("ar", "Artist")),
            rows("SELECT id, name FROM artist ORDER BY id"),
        )
        assertEquals(
            listOf(listOf("ar", "7000")),
            rows("SELECT artist_id, follow_since FROM user_artist")
        )
        assertEquals(
            listOf(listOf("al", "Album", "2019", """["Artist"]""")),
            rows("SELECT id, name, year, artists FROM album"),
        )
        // Not bookmarked, not typed: no row of the user's
        assertEquals(0, rows("SELECT * FROM user_album").size)
        assertEquals(
            listOf("hated", "liked", "typed", "untouched"),
            rows("SELECT song_id FROM song_artist_map ORDER BY song_id").map { it[0] },
        )

        // A playlist is local, in order, without the device song
        assertEquals(
            listOf(listOf("Mix", "LOCAL", "1", "0")),
            rows("SELECT name, origin, is_pinned, is_monthly FROM user_playlist"),
        )
        assertEquals(
            listOf(listOf("liked", "0"), listOf("hated", "2"), listOf("untouched", "3")),
            rows("SELECT song_id, position FROM song_playlist_map ORDER BY position"),
        )

        // Formats need an itag; the loudness is the gain as it is
        assertEquals(
            listOf(listOf("liked", "140", "audio/mp4", "128000", "3.5")),
            rows("SELECT song_id, tag, mimetype, bitrate, gain FROM song_format"),
        )
        assertEquals(
            listOf(listOf("liked", "default", "90", "1000000")),
            rows("SELECT song_id, user_id, time_spent, created_at FROM playback_history"),
        )
        // Order is kept, later searches are more recent
        assertEquals(
            listOf("second", "first"),
            rows("SELECT query FROM search_history ORDER BY last_used DESC").map { it[0] },
        )
        assertEquals(0, rows("SELECT * FROM persistent_queue").size)
        assertEquals(0, rows("SELECT * FROM lyrics").size)
    }

    @Test
    fun usersShareTheCatalog() {
        val first = legacy("a.db") {
            execSQL("INSERT INTO songs VALUES ('same', 'Same', NULL, NULL, NULL, 10, 0, 0, 0)")
            execSQL("INSERT INTO artists VALUES ('ar', 'Artist', NULL, NULL, NULL, 1)")
        }
        val second = legacy("b.db") {
            execSQL("INSERT INTO songs VALUES ('same', 'Same', NULL, NULL, NULL, -1, 0, 0, 0), ('other', 'Other', NULL, NULL, NULL, NULL, 0, 0, 0)")
            execSQL("INSERT INTO artists VALUES ('ar', 'Artist', NULL, NULL, 9, 1)")
        }

        val result =
            import(LegacyProfile("default", "One", first), LegacyProfile("u2", "Two", second))

        assertEquals(2, result.users)
        assertEquals(2, result.songs)
        assertEquals(listOf("other", "same"), rows("SELECT id FROM song ORDER BY id").map { it[0] })
        assertEquals(1, rows("SELECT id FROM artist").size)
        assertEquals(
            listOf(
                listOf("default", "same", "10"),
                listOf("u2", "same", "-1"),
            ),
            rows("SELECT user_id, song_id, liked_at FROM user_song ORDER BY user_id, song_id"),
        )
        assertEquals(
            listOf(listOf("u2", "9")),
            rows("SELECT user_id, follow_since FROM user_artist")
        )
    }

    @Test
    fun readsWhatAnOlderDatabaseHas() {
        // Only the columns the oldest version had, and none of the other tables
        val file = File(directory, "old.db")
        driver.open(file.path).use {
            it.execSQL("CREATE TABLE songs (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
            it.execSQL("INSERT INTO songs VALUES ('a', 'Old')")
        }

        import(LegacyProfile("default", "Default", file))

        assertEquals(listOf(listOf("a", "Old")), rows("SELECT id, name FROM song"))
    }

    @Test
    fun rejectsADatabaseThatIsNotTheOldApps() {
        val file = File(directory, "other.db")
        driver.open(file.path).use { it.execSQL("CREATE TABLE unrelated (x TEXT)") }

        assertThrows(LegacyImportException::class.java) {
            import(LegacyProfile("default", "Default", file))
        }
        assertEquals(false, target.exists())
    }

    @Test
    fun namesAPlaylistThatHasNoName() {
        val file = legacy("data.db") {
            execSQL("INSERT INTO playlists VALUES ('Kept', NULL, 1, 0, 1, 0, 0), ('modified:', NULL, 1, 0, 2, 0, 0)")
        }

        import(LegacyProfile("default", "Default", file))

        assertEquals(
            listOf("Kept", "Playlist-2"),
            rows("SELECT name FROM user_playlist ORDER BY playlist_id").map { it[0] },
        )
    }

    @Test
    fun parsesLengths() {
        assertEquals(215.seconds, parseLegacyDuration("215"))
        assertEquals(205.seconds, parseLegacyDuration("3:25"))
        assertEquals(3723.seconds, parseLegacyDuration("1:02:03"))
        assertNull(parseLegacyDuration("0"))
        assertNull(parseLegacyDuration(""))
        assertNull(parseLegacyDuration("-5"))
        assertNull(parseLegacyDuration("1:2:3:4"))
        assertNull(parseLegacyDuration("abc"))
    }

    private companion object {

        /** The tables of version 36 of the old app. */
        val SCHEMA = listOf(
            "CREATE TABLE songs (id TEXT NOT NULL, title TEXT NOT NULL, artists TEXT, duration TEXT, thumbnail_url TEXT, liked_at INTEGER, total_playtime INTEGER NOT NULL, is_explicit INTEGER NOT NULL, is_local INTEGER NOT NULL, PRIMARY KEY(id))",
            "CREATE TABLE playlists (name TEXT NOT NULL, browse_id TEXT, editable INTEGER NOT NULL, youtube INTEGER NOT NULL, id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, is_pinned INTEGER NOT NULL, is_monthly INTEGER NOT NULL)",
            "CREATE TABLE song_playlist_map (song_id TEXT NOT NULL, playlist_id INTEGER NOT NULL, position INTEGER NOT NULL, set_video_id TEXT, PRIMARY KEY(song_id, playlist_id))",
            "CREATE TABLE artists (id TEXT NOT NULL, name TEXT, thumbnail_url TEXT, created_at INTEGER, bookmarked_at INTEGER, youtube INTEGER NOT NULL, PRIMARY KEY(id))",
            "CREATE TABLE song_artist_map (song_id TEXT NOT NULL, artist_id TEXT NOT NULL, PRIMARY KEY(song_id, artist_id))",
            "CREATE TABLE albums (id TEXT NOT NULL, title TEXT, thumbnail_url TEXT, year TEXT, artists TEXT, url TEXT, created_at INTEGER, bookmarked_at INTEGER, youtube INTEGER NOT NULL, PRIMARY KEY(id))",
            "CREATE TABLE song_album_map (song_id TEXT NOT NULL, album_id TEXT NOT NULL, position INTEGER, PRIMARY KEY(song_id, album_id))",
            "CREATE TABLE search_history (query TEXT NOT NULL, id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)",
            "CREATE TABLE persistent_queue (song_id TEXT NOT NULL, position INTEGER, PRIMARY KEY(song_id))",
            "CREATE TABLE formats (song_id TEXT NOT NULL, yt_itag INTEGER, mimetype TEXT, bitrate INTEGER, length INTEGER, updated_at INTEGER, loudness REAL, PRIMARY KEY(song_id))",
            "CREATE TABLE playback_history (song_id TEXT NOT NULL, created_at INTEGER NOT NULL, time_spent INTEGER NOT NULL, id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)",
            "CREATE TABLE lyrics (song_id TEXT NOT NULL, fixed TEXT, synced TEXT, PRIMARY KEY(song_id))",
        )
    }
}
