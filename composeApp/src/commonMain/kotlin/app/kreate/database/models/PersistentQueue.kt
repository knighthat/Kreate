package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey


/**
 * Represents an item in the queue.
 *
 * @param songId identifier of the song
 * @param position non-null value indicates
 * currently playing song, and timestamp
 */
@Immutable
@Entity(
    tableName = "persistent_queue",
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
data class PersistentQueue(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: String,

    val position: Long? = null
) {

    data class Item(@Embedded val song: Song, val position: Long?)

    object Tag
}
