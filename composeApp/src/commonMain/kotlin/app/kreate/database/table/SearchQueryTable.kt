package app.kreate.database.table

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.SearchQuery
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface SearchQueryTable: DatabaseTable<SearchQuery> {

    override val tableName: String
        get() = SearchQuery::class.simpleName!!

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

    @Query("DELETE FROM SearchQuery")
    fun deleteAll(): Int
}