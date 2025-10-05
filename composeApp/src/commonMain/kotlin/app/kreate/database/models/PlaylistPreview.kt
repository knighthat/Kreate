package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.Embedded


@Immutable
data class PlaylistPreview(
    @Embedded val playlist: Playlist,
    val songCount: Int,
    val thumbnailUrl: String? = null
)

