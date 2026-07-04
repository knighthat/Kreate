package app.kreate.database.ext

import androidx.room.Embedded
import androidx.room.Relation
import app.kreate.database.models.Event
import app.kreate.database.models.Song

data class EventWithSong(
    @Embedded val event: Event,
    @Relation(
        entity = Song::class,
        parentColumn = "song_id",
        entityColumn = "id"
    )
    val song: Song
)