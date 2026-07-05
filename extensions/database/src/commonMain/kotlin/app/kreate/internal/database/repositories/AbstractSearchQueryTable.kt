package app.kreate.internal.database.repositories

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.SearchQuery
import app.kreate.database.repositories.SearchQueryTable
import kotlinx.coroutines.flow.Flow


@Dao
@RewriteQueriesToDropUnusedColumns
internal abstract class AbstractSearchQueryTable: SearchQueryTable {

    override val tableName: String
        get() = "search_history"

    @Query("""
        SELECT DISTINCT * 
        FROM search_history 
        WHERE `query` LIKE '%' || :searchTerm || '%' COLLATE NOCASE
    """)
    abstract override fun findAllContain( searchTerm: String ): Flow<List<SearchQuery>>

    @Query("DELETE FROM search_history")
    abstract override fun deleteAll(): Int
}