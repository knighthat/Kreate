package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey


@Immutable
@Entity(
    tableName = "lyrics",
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class Lyrics(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: String,

    val fixed: String?,

    val synced: String?,
)
