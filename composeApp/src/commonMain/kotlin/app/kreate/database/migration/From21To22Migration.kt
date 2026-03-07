package app.kreate.database.migration

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec

@DeleteColumn.Entries(DeleteColumn("Artist", "info"))
class From21To22Migration : AutoMigrationSpec