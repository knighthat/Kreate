package app.kreate.database.repositories

import app.kreate.database.models.SearchQuery
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.flow.Flow


interface SearchQueryTable: DatabaseTable<SearchQuery> {

    /**
     * [searchTerm] appears in [SearchQuery.query].
     * Additionally, it's **case-insensitive**
     *
     * I.E.: `name` matches `1name_to` and `1_NaMe_to`
     *
     * @param searchTerm what to look for
     * @return all [SearchQuery]s that have [SearchQuery.query] contain [searchTerm]
     */
    fun findAllContain( searchTerm: String ): Flow<List<SearchQuery>>

    fun deleteAll(): Int
}