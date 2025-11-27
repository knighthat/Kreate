package me.knighthat.kreate.database.tables

import androidx.room.Dao
import androidx.room.RewriteQueriesToDropUnusedColumns
import me.knighthat.kreate.database.entities.SongArtistMapEntity


@Dao
@RewriteQueriesToDropUnusedColumns
interface SongArtistMapTable : DaoTable<SongArtistMapEntity> {
}