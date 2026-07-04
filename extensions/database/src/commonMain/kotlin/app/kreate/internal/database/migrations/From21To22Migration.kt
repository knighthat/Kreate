package app.kreate.internal.database.migrations

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec


@DeleteColumn.Entries(DeleteColumn("Artist", "info"))
internal class From21To22Migration : AutoMigrationSpec