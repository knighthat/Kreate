package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.kreate.util.cleanPrefix


@Immutable
@Entity(tableName = "albums")
data class Album(
    @PrimaryKey
    val id: String,

    val title: String? = null,

    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String? = null,

    val year: String? = null,

    @ColumnInfo(name = "artists")
    val authorsText: String? = null,

    @ColumnInfo(name = "url")
    val shareUrl: String? = null,

    @ColumnInfo(name = "created_at")
    val timestamp: Long? = null,

    @ColumnInfo(name = "bookmarked_at")
    val bookmarkedAt: Long? = null,

    @ColumnInfo(name = "youtube")
    val isYoutubeAlbum: Boolean = false
) {

    fun cleanTitle() = cleanPrefix( this.title ?: "" )

    fun cleanAuthorsText() = cleanPrefix( this.authorsText ?: "" )

    fun cleanThumbnailUrl() = thumbnailUrl?.let { cleanPrefix( it ) }
}
