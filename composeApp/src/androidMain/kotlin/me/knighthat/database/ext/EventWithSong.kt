package me.knighthat.database.ext

import androidx.room.Embedded
import androidx.room.Relation
import app.kreate.database.models.Event
import app.kreate.database.models.Song

data class EventWithSong(
    @Embedded val event: Event,
    @Relation(
        entity = Song::class,
        parentColumn = "songId",
        entityColumn = "id"
    )
    val song: Song
)