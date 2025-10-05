package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey


@Immutable
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class Lyrics(
    @PrimaryKey val songId: String,
    val fixed: String?,
    val synced: String?,
)
