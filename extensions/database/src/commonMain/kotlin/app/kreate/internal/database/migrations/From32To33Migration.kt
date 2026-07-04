package app.kreate.internal.database.migrations

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec


@DeleteColumn("song_playlist_map", "dateAdded")
internal class From32To33Migration : AutoMigrationSpec