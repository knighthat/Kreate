package app.kreate.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.SearchQuery
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface SearchQueryTable: DatabaseTable<SearchQuery> {

    override val tableName: String
        get() = "search_history"

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
        FROM search_history 
        WHERE `query` LIKE '%' || :searchTerm || '%' COLLATE NOCASE
        """)
    fun findAllContain( searchTerm: String ): Flow<List<SearchQuery>>

    @Query("DELETE FROM search_history")
    fun deleteAll(): Int
}