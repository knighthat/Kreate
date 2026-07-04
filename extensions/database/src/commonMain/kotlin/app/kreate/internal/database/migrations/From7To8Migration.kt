package app.kreate.internal.database.migrations

import androidx.room.RenameColumn
import androidx.room.migration.AutoMigrationSpec


@RenameColumn.Entries(RenameColumn("Song", "albumInfoId", "albumId"))
internal class From7To8Migration : AutoMigrationSpec