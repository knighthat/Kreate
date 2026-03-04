package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.kreate.util.cleanPrefix


@Immutable
@Entity(tableName = "artists")
data class Artist(
    @PrimaryKey
    val id: String,

    val name: String? = null,

    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String? = null,

    @ColumnInfo(name = "created_at")
    val timestamp: Long? = null,

    @ColumnInfo(name = "bookmarked_at")
    val bookmarkedAt: Long? = null,

    @ColumnInfo(name = "youtube")
    val isYoutubeArtist: Boolean = false,
) {

    fun cleanName() = cleanPrefix( this.name ?: "" )
}
