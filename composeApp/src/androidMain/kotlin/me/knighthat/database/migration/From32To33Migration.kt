package me.knighthat.database.migration

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec


@DeleteColumn("song_playlist_map", "dateAdded")
class From32To33Migration : AutoMigrationSpec