package app.kreate.internal.database.migrations

import androidx.room.RenameTable
import androidx.room.migration.AutoMigrationSpec


@RenameTable("SongInPlaylist", "SongPlaylistMap")
@RenameTable("SortedSongInPlaylist", "SortedSongPlaylistMap")
internal class From11To12Migration : AutoMigrationSpec