package app.kreate.database.migration

import androidx.room.migration.Migration
import androidx.room.util.foreignKeyCheck
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.execSQL


/**
 * This migration aims to rename tables to standards.
 *
 * This includes changing their names to  match functionalities,
 * or to remove `CamelCase` with `snake_case`.
 *
 * For example:
 * - QueuedMediaItem -> persistent_queue
 * - Song -> songs
 */
class From30To31Migration : Migration(30, 31) {

    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP VIEW SortedSongPlaylistMap")
        connection.execSQL("ALTER TABLE `Song` RENAME TO `songs`")
        connection.execSQL("ALTER TABLE `Playlist` RENAME TO `playlists`")
        connection.execSQL("ALTER TABLE `Artist` RENAME TO `artists`")
        connection.execSQL("ALTER TABLE `Album` RENAME TO `albums`")
        connection.execSQL("ALTER TABLE `QueuedMediaItem` RENAME TO `persistent_queue`")
        //<editor-fold desc="song_playlist_map">
        connection.execSQL("CREATE TABLE IF NOT EXISTS `_new_song_playlist_map` (`songId` TEXT NOT NULL, `playlistId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `setVideoId` TEXT, `dateAdded` INTEGER, PRIMARY KEY(`songId`, `playlistId`), FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("INSERT INTO `_new_song_playlist_map` (`songId`,`playlistId`,`position`,`setVideoId`,`dateAdded`) SELECT `songId`,`playlistId`,`position`,`setVideoId`,`dateAdded` FROM `SongPlaylistMap`")
        connection.execSQL("DROP TABLE `SongPlaylistMap`")
        connection.execSQL("ALTER TABLE `_new_song_playlist_map` RENAME TO `song_playlist_map`")
        try {
            foreignKeyCheck(connection, "song_playlist_map")
        } catch( _: SQLiteException ) {
            connection.execSQL("""
                DELETE FROM song_playlist_map
                WHERE
                    NOT EXISTS (
                        SELECT 1 
                        FROM songs s
                        WHERE s.id = song_playlist_map.songId
                    )
                OR 
                    NOT EXISTS (
                        SELECT 1 
                        FROM playlists p
                        WHERE p.id = song_playlist_map.playlistId 
                    );
            """.trimIndent())
        }
        //</editor-fold>
        //<editor-fold desc="song_artist_map">
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_song_playlist_map_songId` ON `song_playlist_map` (`songId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_song_playlist_map_playlistId` ON `song_playlist_map` (`playlistId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `_new_song_artist_map` (`songId` TEXT NOT NULL, `artistId` TEXT NOT NULL, PRIMARY KEY(`songId`, `artistId`), FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`artistId`) REFERENCES `artists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("INSERT INTO `_new_song_artist_map` (`songId`,`artistId`) SELECT `songId`,`artistId` FROM `SongArtistMap`")
        connection.execSQL("DROP TABLE `SongArtistMap`")
        connection.execSQL("ALTER TABLE `_new_song_artist_map` RENAME TO `song_artist_map`")
        try {
            foreignKeyCheck(connection, "song_artist_map")
        } catch( _: SQLiteException ) {
            connection.execSQL("""
                DELETE FROM song_artist_map
                WHERE
                    NOT EXISTS (
                        SELECT 1 
                        FROM songs s
                        WHERE s.id = song_artist_map.songId
                    )
                OR 
                    NOT EXISTS (
                        SELECT 1 
                        FROM artists a
                        WHERE a.id = song_artist_map.artistId 
                    );
            """.trimIndent())
        }
        //</editor-fold>
        //<editor-fold desc="song_album_map">
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_song_artist_map_songId` ON `song_artist_map` (`songId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_song_artist_map_artistId` ON `song_artist_map` (`artistId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `_new_song_album_map` (`songId` TEXT NOT NULL, `albumId` TEXT NOT NULL, `position` INTEGER, PRIMARY KEY(`songId`, `albumId`), FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`albumId`) REFERENCES `albums`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("INSERT INTO `_new_song_album_map` (`songId`,`albumId`,`position`) SELECT `songId`,`albumId`,`position` FROM `SongAlbumMap`")
        connection.execSQL("DROP TABLE `SongAlbumMap`")
        connection.execSQL("ALTER TABLE `_new_song_album_map` RENAME TO `song_album_map`")
        try {
            foreignKeyCheck(connection, "song_album_map")
        } catch( _: SQLiteException ) {
            connection.execSQL("""
                DELETE FROM song_album_map
                WHERE
                    NOT EXISTS (
                        SELECT 1 
                        FROM songs s
                        WHERE s.id = song_album_map.songId
                    )
                OR 
                    NOT EXISTS (
                        SELECT 1 
                        FROM albums a
                        WHERE a.id = song_album_map.albumId 
                    );
            """.trimIndent())
        }
        //</editor-fold>
        //<editor-fold desc="search_history">
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_song_album_map_songId` ON `song_album_map` (`songId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_song_album_map_albumId` ON `song_album_map` (`albumId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `_new_search_history` (`query` TEXT NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
        connection.execSQL("INSERT INTO `_new_search_history` (`query`,`id`) SELECT `query`,`id` FROM `SearchQuery`")
        connection.execSQL("DROP TABLE `SearchQuery`")
        connection.execSQL("ALTER TABLE `_new_search_history` RENAME TO `search_history`")
        //</editor-fold>
        //<editor-fold desc="formats">
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_search_history_query` ON `search_history` (`query`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `_new_formats` (`songId` TEXT NOT NULL, `itag` INTEGER, `mimeType` TEXT, `bitrate` INTEGER, `contentLength` INTEGER, `lastModified` INTEGER, `loudnessDb` REAL, PRIMARY KEY(`songId`), FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("INSERT INTO `_new_formats` (`songId`,`itag`,`mimeType`,`bitrate`,`contentLength`,`lastModified`,`loudnessDb`) SELECT `songId`,`itag`,`mimeType`,`bitrate`,`contentLength`,`lastModified`,`loudnessDb` FROM `Format`")
        connection.execSQL("DROP TABLE `Format`")
        connection.execSQL("ALTER TABLE `_new_formats` RENAME TO `formats`")
        try {
            foreignKeyCheck(connection, "formats")
        } catch( _: SQLiteException ) {
            connection.execSQL("""
                DELETE FROM formats
                WHERE NOT EXISTS (
                    SELECT 1 
                    FROM songs s
                    WHERE s.id = formats.songId
                );
            """.trimIndent())
        }
        //</editor-fold>
        //<editor-fold desc="playback_history">
        connection.execSQL("CREATE TABLE IF NOT EXISTS `_new_playback_history` (`songId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `playTime` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("INSERT INTO `_new_playback_history` (`songId`,`timestamp`,`playTime`,`id`) SELECT `songId`,`timestamp`,`playTime`,`id` FROM `Event`")
        connection.execSQL("DROP TABLE `Event`")
        connection.execSQL("ALTER TABLE `_new_playback_history` RENAME TO `playback_history`")
        try {
            foreignKeyCheck(connection, "playback_history")
        } catch( _: SQLiteException ) {
            connection.execSQL("""
                DELETE FROM playback_history
                WHERE NOT EXISTS (
                    SELECT 1 
                    FROM songs s
                    WHERE s.id = playback_history.songId
                );
            """.trimIndent())
        }
        //</editor-fold>
        //<editor-fold desc="lyrics">
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_songId` ON `playback_history` (`songId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `_new_lyrics` (`songId` TEXT NOT NULL, `fixed` TEXT, `synced` TEXT, PRIMARY KEY(`songId`), FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("INSERT INTO `_new_lyrics` (`songId`,`fixed`,`synced`) SELECT `songId`,`fixed`,`synced` FROM `Lyrics`")
        connection.execSQL("DROP TABLE `Lyrics`")
        connection.execSQL("ALTER TABLE `_new_lyrics` RENAME TO `lyrics`")
        try {
            foreignKeyCheck(connection, "lyrics")
        } catch( _: SQLiteException ) {
            connection.execSQL("""
                DELETE FROM lyrics
                WHERE NOT EXISTS (
                    SELECT 1 
                    FROM songs s
                    WHERE s.id = lyrics.songId 
                );
            """.trimIndent())
        }
        //</editor-fold>
    }
}