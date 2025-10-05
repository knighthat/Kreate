package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.kreate.util.cleanPrefix


@Immutable
@Entity
data class Playlist(
    val name: String,

    val browseId: String? = null,

    val isEditable: Boolean = true,

    val isYoutubePlaylist: Boolean = false,

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0
) {

    fun cleanName() = cleanPrefix( this.name )
}
