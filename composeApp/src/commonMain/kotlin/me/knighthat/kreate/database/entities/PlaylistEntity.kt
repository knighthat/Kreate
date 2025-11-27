package me.knighthat.kreate.database.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


/**
 * @param id unique identifier of this playlist
 * @param title of this playlist
 * @param thumbnailUrl playlist's artwork
 * @param browseId remote id to access this playlist online
 */
@Immutable
@Entity("playlist")
data class PlaylistEntity(
    val title: String,

    val browseId: String? = null,

    @ColumnInfo("thumbnail_url")
    val thumbnailUrl: String? = null,

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0
)
