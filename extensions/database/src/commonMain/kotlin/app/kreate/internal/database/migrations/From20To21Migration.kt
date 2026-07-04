package app.kreate.internal.database.migrations

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec


@DeleteColumn.Entries(
    DeleteColumn("Artist", "shuffleVideoId"),
    DeleteColumn("Artist", "shufflePlaylistId"),
    DeleteColumn("Artist", "radioVideoId"),
    DeleteColumn("Artist", "radioPlaylistId"),
)
internal class From20To21Migration : AutoMigrationSpec