package app.kreate.internal.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
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
import app.kreate.internal.database.migrations.From11To12Migration
import app.kreate.internal.database.migrations.From20To21Migration
import app.kreate.internal.database.migrations.From21To22Migration
import app.kreate.internal.database.migrations.From31To32Migration
import app.kreate.internal.database.migrations.From32To33Migration
import app.kreate.internal.database.migrations.From3To4Migration
import app.kreate.internal.database.migrations.From7To8Migration
import app.kreate.internal.database.repositories.AbstractAlbumTable
import app.kreate.internal.database.repositories.AbstractArtistTable
import app.kreate.internal.database.repositories.AbstractEventTable
import app.kreate.internal.database.repositories.AbstractFormatTable
import app.kreate.internal.database.repositories.AbstractLyricsTable
import app.kreate.internal.database.repositories.AbstractPlaylistTable
import app.kreate.internal.database.repositories.AbstractQueuedMediaItemTable
import app.kreate.internal.database.repositories.AbstractSearchQueryTable
import app.kreate.internal.database.repositories.AbstractSongAlbumMapTable
import app.kreate.internal.database.repositories.AbstractSongArtistMapTable
import app.kreate.internal.database.repositories.AbstractSongPlaylistMapTable
import app.kreate.internal.database.repositories.AbstractSongTable


@Database(
    version = 36,
    exportSchema = true,
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
internal abstract class AbstractRoomDatabase : RoomDatabase() {

    abstract val albumTable: AbstractAlbumTable
    abstract val artistTable: AbstractArtistTable
    abstract val eventTable: AbstractEventTable
    abstract val formatTable: AbstractFormatTable
    abstract val lyricsTable: AbstractLyricsTable
    abstract val playlistTable: AbstractPlaylistTable
    abstract val queueTable: AbstractQueuedMediaItemTable
    abstract val searchQueryTable: AbstractSearchQueryTable
    abstract val songAlbumMapTable: AbstractSongAlbumMapTable
    abstract val songArtistMapTable: AbstractSongArtistMapTable
    abstract val songPlaylistMapTable: AbstractSongPlaylistMapTable
    abstract val songTable: AbstractSongTable
}