package me.knighthat.kreate.database.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey


/**
 * @param songId id to [SongEntity]
 * @param artistId id to [ArtistEntity]
 */
@Immutable
@Entity(
    tableName = "song_artist_map",
    primaryKeys = ["song_id", "artist_id"],
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
            deferred = true
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artist_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
            deferred = true
        )
    ]
)
data class SongArtistMapEntity(
    @ColumnInfo("song_id", index = true)
    val songId: String,

    @ColumnInfo("artist_id", index = true)
    val artistId: String
)
