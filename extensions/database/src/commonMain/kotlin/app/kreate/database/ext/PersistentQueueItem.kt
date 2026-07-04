package app.kreate.database.ext

import androidx.room.Embedded
import app.kreate.database.models.Song


data class PersistentQueueItem(
    @Embedded val song: Song,
    val position: Long?
)
