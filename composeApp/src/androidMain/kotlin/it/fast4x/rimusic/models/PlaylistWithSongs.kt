package it.fast4x.rimusic.models

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import app.kreate.database.models.Playlist
import app.kreate.database.models.Song

@Immutable
data class PlaylistWithSongs(
    @Embedded val playlist: Playlist,
    @Relation(
        entity = Song::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = SortedSongPlaylistMap::class,
            parentColumn = "playlistId",
            entityColumn = "songId"
        )
    )
    var songs: List<Song>
)
