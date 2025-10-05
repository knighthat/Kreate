package me.knighthat.database.ext

import androidx.room.Embedded
import app.kreate.database.models.Format
import app.kreate.database.models.Song

data class FormatWithSong(
    @Embedded val format: Format,
    @Embedded val song: Song
)