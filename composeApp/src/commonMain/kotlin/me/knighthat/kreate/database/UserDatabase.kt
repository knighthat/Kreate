package me.knighthat.kreate.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.knighthat.kreate.database.entities.AlbumEntity
import me.knighthat.kreate.database.entities.ArtistEntity
import me.knighthat.kreate.database.entities.PlaylistEntity
import me.knighthat.kreate.database.entities.SongAlbumMapEntity
import me.knighthat.kreate.database.entities.SongArtistMapEntity
import me.knighthat.kreate.database.entities.SongEntity
import me.knighthat.kreate.database.entities.SongPlaylistMapEntity
import me.knighthat.kreate.database.tables.AlbumTable
import me.knighthat.kreate.database.tables.ArtistTale
import me.knighthat.kreate.database.tables.PlaylistTable
import me.knighthat.kreate.database.tables.SongAlbumMapTable
import me.knighthat.kreate.database.tables.SongArtistMapTable
import me.knighthat.kreate.database.tables.SongPlaylistMapTable
import me.knighthat.kreate.database.tables.SongTable


@Database(
    version = 1,
    entities = [
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        SongAlbumMapEntity::class,
        SongArtistMapEntity::class,
        SongPlaylistMapEntity::class
    ]
)
@TypeConverters(DataConverter::class)
abstract class UserDatabase : RoomDatabase() {

    companion object {

        const val FILENAME = "database.db"
    }

    abstract val songs: SongTable
    abstract val artists: ArtistTale
    abstract val albums: AlbumTable
    abstract val playlists: PlaylistTable
    abstract val songArtistMaps: SongArtistMapTable
    abstract val songAlbumMaps: SongAlbumMapTable
    abstract val songPlaylistMaps: SongPlaylistMapTable
}
