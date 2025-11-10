package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey


@Immutable
@Entity(
    tableName = "persistent_queue",
    foreignKeys = [ForeignKey(
        entity = Song::class,
        parentColumns = ["id"],
        childColumns = ["song_id"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE
    )]
)
data class PersistentQueue(
    @PrimaryKey
    @ColumnInfo("song_id")
    val songId: String,
    val position: Long? = null
)