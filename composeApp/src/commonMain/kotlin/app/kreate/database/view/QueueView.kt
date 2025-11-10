package app.kreate.database.view

import androidx.room.DatabaseView
import androidx.room.Embedded
import app.kreate.database.models.Song


@DatabaseView(
    // Adding new lines here will make the migration fail
    value = "SELECT DISTINCT s.*, position FROM persistent_queue JOIN Song s ON s.id = song_id",
    viewName = "queue_view"
)
data class QueueView(
    @Embedded val song: Song,
    val position: Long?
)