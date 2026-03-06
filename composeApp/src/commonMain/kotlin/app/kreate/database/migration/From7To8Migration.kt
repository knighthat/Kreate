package app.kreate.database.migration

import androidx.room.RenameColumn
import androidx.room.migration.AutoMigrationSpec

@RenameColumn.Entries(RenameColumn("Song", "albumInfoId", "albumId"))
class From7To8Migration : AutoMigrationSpec