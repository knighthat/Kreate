package app.kreate.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class From8To9Migration : Migration(8, 9) {

    override fun migrate(connection: SQLiteConnection) {
        // 1. Migrate Albums
        // 2. Insert distinct albums into the new table
        connection.execSQL("""
            BEGIN TRANSACTION;

            INSERT OR IGNORE INTO Album (id, title)
            SELECT DISTINCT browseId, text
            FROM Info
            JOIN Song ON Info.id = Song.albumId;
            
            -- Update Song table to use browseId (String) instead of Info.id (Long)
            UPDATE Song
            SET albumId = (
                SELECT browseId 
                FROM Info 
                WHERE Info.id = Song.albumId
            )
            WHERE EXISTS (
                SELECT 1 
                FROM Info 
                WHERE Info.id = Song.albumId
            );
           
            COMMIT;
        """.trimIndent())
        // 2. Update Artist Text Concatenations
        // This replaces the loop that was manually building the artist strings
        connection.execSQL("""
            BEGIN TRANSACTION;
            
            UPDATE Song
            SET artistsText = (
                SELECT GROUP_CONCAT(Info.text, '')
                FROM Info
                JOIN SongWithAuthors ON Info.id = SongWithAuthors.authorInfoId
                WHERE SongWithAuthors.songId = Song.id
            );
            
            COMMIT;
        """.trimIndent())
        // 3. Migrate Artists and Junction Table
        // Insert unique artists where a browseId exists
        connection.execSQL("""
            BEGIN TRANSACTION;
            
            INSERT OR IGNORE INTO Artist (id, name)
            SELECT DISTINCT browseId, text
            FROM Info
            JOIN SongWithAuthors ON Info.id = SongWithAuthors.authorInfoId
            WHERE browseId IS NOT NULL;
            
            -- Update SongWithAuthors to use browseId (String)
            UPDATE SongWithAuthors
            SET authorInfoId = (
                SELECT browseId 
                FROM Info 
                WHERE Info.id = SongWithAuthors.authorInfoId
            )
            WHERE EXISTS (
                SELECT 1 FROM Info 
                WHERE Info.id = SongWithAuthors.authorInfoId 
                AND browseId IS NOT NULL
            );
            
            COMMIT;
        """.trimIndent())


        connection.execSQL("INSERT INTO SongArtistMap(songId, artistId) SELECT songId, authorInfoId FROM SongWithAuthors")

        connection.execSQL("DROP TABLE Info;")
        connection.execSQL("DROP TABLE SongWithAuthors;")
    }
}