package me.knighthat.kreate.database.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.time.Instant


/**
 * @param id unique identifier of this album
 * @param title of the album
 * @param artistName who performs in this album
 * @param thumbnailUrl album's artwork
 * @param bookmarkTimestamp when is it bookmarked, `null` means it isn't bookmarked
 * @param isExplicit whether this song is suitable for all listeners
 */
@Immutable
@Entity("album")
data class AlbumEntity(
    @PrimaryKey
    val id: String,

    val title: String,

    @ColumnInfo("artist_name")
    val artistName: String,

    @ColumnInfo("thumbnail_url")
    val thumbnailUrl: String? = null,

    val year: Short? = null,

    @ColumnInfo("bookmark_timestamp")
    val bookmarkTimestamp: Instant? = null,

    @ColumnInfo("is_explicit")
    val isExplicit: Boolean = false
) {

    @Ignore
    val isBookmarked = bookmarkTimestamp != null
}
