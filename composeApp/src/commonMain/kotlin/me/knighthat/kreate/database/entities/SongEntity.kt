package me.knighthat.kreate.database.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import kotlin.time.Duration


/**
 * @param id unique identifier of this song
 * @param title of the song
 * @param artistName who performs this song
 * @param duration how long is this long
 * @param thumbnailUrl song's artwork
 * @param likeStatus `-1` represents dislike, `0` is neutral, and >0 is when this song was liked
 * @param totalListeningTime accumulated listening time
 * @param isExplicit whether this song is suitable for all listeners
 * @param isLocal whether song only exists on device
 */
@Immutable
@Entity("song")
data class SongEntity(
    @PrimaryKey
    val id: String,

    val title: String,

    @ColumnInfo("artist_name")
    val artistName: String,

    val duration: Duration = Duration.ZERO,

    @ColumnInfo("thumbnail_url")
    val thumbnailUrl: String? = null,

    @ColumnInfo("like_status")
    val likeStatus: Long? = null,

    @ColumnInfo("total_listening_time")
    val totalListeningTime: Long = 0,

    @ColumnInfo("is_explicit")
    val isExplicit: Boolean = false,

    @ColumnInfo("is_local")
    val isLocal: Boolean = false
) {

    @Ignore
    val isLiked = likeStatus != null && likeStatus > 0
}
