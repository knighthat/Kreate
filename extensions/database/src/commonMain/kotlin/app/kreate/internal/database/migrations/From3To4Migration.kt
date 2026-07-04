package app.kreate.internal.database.migrations

import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec


@DeleteTable.Entries(DeleteTable(tableName = "QueuedMediaItem"))
internal class From3To4Migration : AutoMigrationSpec