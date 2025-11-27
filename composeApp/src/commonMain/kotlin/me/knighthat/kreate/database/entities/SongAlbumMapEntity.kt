package me.knighthat.kreate.database.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey


/**
 * @param songId id to [SongEntity]
 * @param albumId id to [AlbumEntity]
 * @param position where this song is in the album
 */
@Immutable
@Entity(
    tableName = "song_album_map",
    primaryKeys = ["song_id", "album_id"],
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
            deferred = true
        ),
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
            deferred = true
        )
    ]
)
data class SongAlbumMapEntity(
    @ColumnInfo("song_id", index = true)
    val songId: String,

    @ColumnInfo("album_id", index = true)
    val albumId: String,

    val position: Int? = null
)
