package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey


@Immutable
@Entity(
    tableName = "song_album_map",
    primaryKeys = ["song_id", "album_id"],
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Album::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SongAlbumMap(
    @ColumnInfo(index = true, name = "song_id")
    val songId: String,

    @ColumnInfo(index = true, name = "album_id")
    val albumId: String,

    val position: Int?
)
