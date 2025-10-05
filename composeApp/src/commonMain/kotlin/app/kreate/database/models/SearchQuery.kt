package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


@Immutable
@Entity(
    indices = [
        Index(
            value = ["query"],
            unique = true
        )
    ]
)
data class SearchQuery(
    val query: String,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0
)
