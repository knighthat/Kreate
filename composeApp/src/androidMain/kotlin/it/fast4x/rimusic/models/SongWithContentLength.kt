package it.fast4x.rimusic.models

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import app.kreate.database.models.Song

@Immutable
data class SongWithContentLength(
    @Embedded val song: Song,
    val contentLength: Long?
)
