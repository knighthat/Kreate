package me.knighthat.database

import android.database.SQLException
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.SearchQuery
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface SearchQueryTable {

    /**
     * [searchTerm] appears in [SearchQuery.query].
     * Additionally, it's **case-insensitive**
     *
     * I.E.: `name` matches `1name_to` and `1_NaMe_to`
     *
     * @param searchTerm what to look for
     * @return all [SearchQuery]s that have [SearchQuery.query] contain [searchTerm]
     */
    @Query("""
        SELECT DISTINCT * 
        FROM SearchQuery 
        WHERE `query` LIKE '%' || :searchTerm || '%' COLLATE NOCASE
        """)
    fun findAllContain( searchTerm: String ): Flow<List<SearchQuery>>

    /**
     * Attempt to write [searchQuery] into database.
     *
     * ### Standalone use
     *
     * When error occurs and [SQLException] is thrown,
     * it'll simply be ignored.
     *
     * ### Transaction use
     *
     * When error occurs and [SQLException] is thrown,
     * it'll simply be ignored and the transaction continues.
     *
     * @param searchQuery intended to insert in to database
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( searchQuery: SearchQuery )

    /**
     * Attempt to remove a record from database.
     *
     * @param searchQuery data intended to delete from database
     */
    @Delete
    fun delete( searchQuery: SearchQuery )

    /**
     * Attempt to remove records from database.
     *
     * @param searchQuery list of [SearchQuery] to delete from database
     * @return number of rows affected by the this operation
     */
    @Delete
    fun delete( searchQuery: List<SearchQuery> ): Int

    @Query("DELETE FROM SearchQuery")
    fun deleteAll(): Int
}