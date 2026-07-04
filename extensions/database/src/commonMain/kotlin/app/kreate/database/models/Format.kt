package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey


@Immutable
@Entity(
    tableName = "formats",
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Format(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: String,

    @ColumnInfo(name = "yt_itag")
    val itag: Int? = null,

    @ColumnInfo(name = "mimetype")
    val mimeType: String? = null,

    val bitrate: Long? = null,

    @ColumnInfo(name = "length")
    val contentLength: Long? = null,

    @ColumnInfo(name = "updated_at")
    val lastModified: Long? = null,

    @ColumnInfo(name = "loudness")
    val loudnessDb: Float? = null
)
