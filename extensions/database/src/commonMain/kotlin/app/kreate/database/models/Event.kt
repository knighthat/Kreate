package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey


@Immutable
@Entity(
    tableName = "playback_history",
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
data class Event(
    @ColumnInfo(index = true, name = "song_id")
    val songId: String,

    @ColumnInfo(name = "created_at")
    var timestamp: Long,

    @ColumnInfo(name = "time_spent")
    val playTime: Long,

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0
)
