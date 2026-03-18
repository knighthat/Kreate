package app.kreate.database

import androidx.room.AutoMigration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.kreate.database.migration.From11To12Migration
import app.kreate.database.migration.From20To21Migration
import app.kreate.database.migration.From21To22Migration
import app.kreate.database.migration.From31To32Migration
import app.kreate.database.migration.From32To33Migration
import app.kreate.database.migration.From3To4Migration
import app.kreate.database.migration.From7To8Migration
import app.kreate.database.models.Album
import app.kreate.database.models.Artist
import app.kreate.database.models.Event
import app.kreate.database.models.Format
import app.kreate.database.models.Lyrics
import app.kreate.database.models.PersistentQueue
import app.kreate.database.models.Playlist
import app.kreate.database.models.SearchQuery
import app.kreate.database.models.Song
import app.kreate.database.models.SongAlbumMap
import app.kreate.database.models.SongArtistMap
import app.kreate.database.models.SongPlaylistMap


@androidx.room.Database(
    entities = [
        Song::class,
        SongPlaylistMap::class,
        Playlist::class,
        Artist::class,
        SongArtistMap::class,
        Album::class,
        SongAlbumMap::class,
        SearchQuery::class,
        PersistentQueue::class,
        Format::class,
        Event::class,
        Lyrics::class,
    ],
    version = 36,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = From3To4Migration::class),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = From7To8Migration::class),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 11, to = 12, spec = From11To12Migration::class),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21, spec = From20To21Migration::class),
        AutoMigration(from = 21, to = 22, spec = From21To22Migration::class),
        AutoMigration(from = 31, to = 32, spec = From31To32Migration::class),
        AutoMigration(from = 32, to = 33, spec = From32To33Migration::class),
        AutoMigration(from = 33, to = 34),       // Adding `onUpdate = ForeignKey.CASCADE` to several tables
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    companion object {

        // TODO: Implement profile-specific file name
        const val FILENAME = "data.db"
    }

    abstract val albumTable: AlbumTable
    abstract val artistTable: ArtistTable
    abstract val eventTable: EventTable
    abstract val formatTable: FormatTable
    abstract val lyricsTable: LyricsTable
    abstract val playlistTable: PlaylistTable
    abstract val queueTable: QueuedMediaItemTable
    abstract val searchQueryTable: SearchQueryTable
    abstract val songAlbumMapTable: SongAlbumMapTable
    abstract val songArtistMapTable: SongArtistMapTable
    abstract val songPlaylistMapTable: SongPlaylistMapTable
    abstract val songTable: SongTable
}