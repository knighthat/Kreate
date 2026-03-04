package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey


@Immutable
@Entity(
    tableName = "song_artist_map",
    primaryKeys = ["song_id", "artist_id"],
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Artist::class,
            parentColumns = ["id"],
            childColumns = ["artist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SongArtistMap(
    @ColumnInfo(index = true, name = "song_id")
    val songId: String,

    @ColumnInfo(index = true, name = "artist_id")
    val artistId: String
)
