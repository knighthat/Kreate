/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import me.knighthat.kreate3.model.LyricsProvider
import me.knighthat.kreate3.model.LyricsSync
import kotlin.time.Instant

/**
 * Lyrics of a [SongEntity].
 *
 * @property songId the [SongEntity] the lyrics belong to
 * @property lyrics the lyrics as the source gave them: plain text, LRC, TTML…
 * @property sync how finely [lyrics] are timed
 * @property source the provider of the lyrics
 * @property credit who the source names as the provider of the lyrics, `null` when it names no one
 * @property createdAt when the lyrics was first stored
 * @property updatedAt when the lyrics was last changed
 */
@Entity(
    tableName = "lyrics",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class LyricsEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: String,
    val lyrics: String,
    val sync: LyricsSync,
    val source: LyricsProvider,
    val credit: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)
