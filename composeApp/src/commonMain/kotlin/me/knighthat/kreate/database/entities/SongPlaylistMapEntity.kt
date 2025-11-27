package me.knighthat.kreate.database.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey


/**
 * @param songId id to [SongEntity]
 * @param playlistId id to [PlaylistEntity]
 * @param position where this song is in the playlist
 */
@Immutable
@Entity(
    tableName = "song_playlist_map",
    primaryKeys = ["song_id", "playlist_id"],
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
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
            deferred = true
        )
    ]
)
data class SongPlaylistMapEntity(
    @ColumnInfo("song_id", index = true)
    val songId: String,

    @ColumnInfo("playlist_id", index = true)
    val playlistId: Long,

    val position: Int
)
