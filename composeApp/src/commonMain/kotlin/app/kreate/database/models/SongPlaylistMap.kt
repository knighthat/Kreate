package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey


@Immutable
@Entity(
    tableName = "song_playlist_map",
    primaryKeys = ["song_id", "playlist_id"],
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SongPlaylistMap(
    @ColumnInfo(index = true, name = "song_id")
    val songId: String,

    @ColumnInfo(index = true, name = "playlist_id")
    val playlistId: Long,

    val position: Int,

    @ColumnInfo(name = "set_video_id")
    val setVideoId: String? = null
)
