package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.kreate.util.cleanPrefix
import app.kreate.util.durationTextToMillis
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.Contract


@Serializable
@Immutable
@Entity
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artistsText: String? = null,
    val durationText: String?,
    val thumbnailUrl: String?,
    val likedAt: Long? = null,
    val totalPlayTimeMs: Long = 0,

    @ColumnInfo("is_explicit")
    val isExplicit: Boolean = false,

    @ColumnInfo("is_local")
    val isLocal: Boolean = false
) {

    val formattedTotalPlayTime: String
        get() {
            val seconds = totalPlayTimeMs / 1000

            val hours = seconds / 3600

            return when {
                hours == 0L -> "${seconds / 60}m"
                hours < 24L -> "${hours}h"
                else -> "${hours / 24}d"
            }
        }

    @Contract("->new")
    fun toggleLike(): Song = copy(
        likedAt = when (likedAt) {
            -1L -> null
            null -> System.currentTimeMillis()
            else -> -1L
        }
    )

    fun cleanTitle() = cleanPrefix( this.title )

    fun cleanArtistsText() = cleanPrefix( this.artistsText ?: "" )

    fun relativePlayTime(): Double {
        val totalPlayTimeMs = durationTextToMillis( this.durationText ?: "" )
        return if(totalPlayTimeMs > 0) this.totalPlayTimeMs.toDouble() / totalPlayTimeMs.toDouble() else 0.0
    }
}