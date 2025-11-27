package me.knighthat.kreate.database.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.time.Instant


/**
 * @param id unique identifier of the artist
 * @param title of the artist
 * @param thumbnailUrl artist's artwork
 * @param followTimestamp is user following this artist, `null` means isn't
 */
@Immutable
@Entity("artist")
data class ArtistEntity(
    @PrimaryKey
    val id: String,

    val title: String,

    @ColumnInfo("thumbnail_url")
    val thumbnailUrl: String? = null,

    @ColumnInfo("follow_timestamp")
    val followTimestamp: Instant? = null
) {

    @Ignore
    val isFollowing = followTimestamp != null
}