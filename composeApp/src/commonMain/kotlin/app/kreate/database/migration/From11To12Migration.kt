package app.kreate.database.migration

import androidx.room.RenameTable
import androidx.room.migration.AutoMigrationSpec

@RenameTable("SongInPlaylist", "SongPlaylistMap")
@RenameTable("SortedSongInPlaylist", "SortedSongPlaylistMap")
class From11To12Migration : AutoMigrationSpec