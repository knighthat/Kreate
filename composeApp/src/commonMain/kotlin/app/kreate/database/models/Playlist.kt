package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.kreate.util.cleanPrefix


@Immutable
@Entity(tableName = "playlists")
data class Playlist(
    val name: String,

    val browseId: String? = null,

    val isEditable: Boolean = true,

    val isYoutubePlaylist: Boolean = browseId?.matches( youTubePlaylistRegex ) == true,

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,

    @ColumnInfo(name = "is_monthly")
    val isMonthly: Boolean = false
) {

    companion object {
        val youTubePlaylistRegex = Regex("(PL|UU|LL|RD|FL|OL)[a-zA-Z0-9_-]{10,48}")
    }

    fun cleanName() = cleanPrefix( this.name )
}
