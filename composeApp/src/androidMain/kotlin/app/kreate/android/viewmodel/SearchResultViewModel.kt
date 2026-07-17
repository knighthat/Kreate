package app.kreate.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kreate.gateway.innertube.YouTube
import app.kreate.gateway.innertube.models.InnertubeItem
import app.kreate.utils.Toaster
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.error_failed_to_get_search_results
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds


@OptIn(ExperimentalAtomicApi::class)
class SearchResultViewModel : ViewModel(), KoinComponent {

    private val logger = Logger.withTag( "SearchResultViewModel" )
    private val _searchResults = MutableStateFlow(emptyList<InnertubeItem>())
    private val _searchContinuation = MutableStateFlow<String?>(null)
    private val _isFetching = MutableStateFlow(false)
    private val getSearchResultsJob = AtomicReference<Job?>(null)

    val searchResults = _searchResults.asStateFlow()
    val hasMore = _searchContinuation.map { !it.isNullOrBlank() }
    val isFetching = _isFetching.asStateFlow()

    fun onFilterChanged( query: String, filter: String ) {
        // WHen category changed, cancel everything, even if it's fetching more
        getSearchResultsJob.exchange( null )?.cancel()

        // Remove all results when category changes
        _searchResults.update { emptyList() }

        // Return if no query provided
        if( query.isBlank() ) return

        viewModelScope.launch( Dispatchers.IO ) {
            logger.v { "Searching \"$query\" with filter: $filter" }

            _isFetching.update { true }

            get<YouTube>()
                .getSearchResults( query, null, filter )
                .onFailure { err ->
                    logger.e( "", err )
                    getString( Res.string.error_failed_to_get_search_results ).also( Toaster::e )
                }
                .onSuccess { result ->
                    _searchResults.update { result.items }
                    val continuation = result.continuations.firstNotNullOfOrNull { it.nextContinuationData }?.continuation
                    _searchContinuation.update { continuation }

                    logger.d { "Found: ${result.items.size} results for \"$query\", is continued: ${!continuation.isNullOrBlank()}" }
                }

            _isFetching.update { false }
        // Store this job
        }.also( getSearchResultsJob::store )
    }

    fun onGetMore() {
        val job = getSearchResultsJob.exchange( null )

        val continuation = _searchContinuation.value
        if( continuation.isNullOrBlank() )
            return

        viewModelScope.launch( Dispatchers.IO ) {
            // Get more must wait for previous job to finish
            // to avoid cancelling each other
            job?.join()

            withTimeoutOrNull( 30.seconds ) {
                while( _isFetching.value ) {
                    delay( 1.seconds )
                }

                get<YouTube>()
                    .getSearchResults( null, continuation, null )
                    .onFailure { err ->
                        logger.e( "", err )
                        getString( Res.string.error_failed_to_get_search_results ).also( Toaster::e )
                    }
                    .onSuccess { result ->
                        _searchResults.update { it + result.items }
                        val continuation = result.continuations.firstNotNullOfOrNull { it.nextContinuationData }?.continuation
                        _searchContinuation.update { continuation }

                        logger.d { "Appended: ${result.items.size} results to results, is continued: ${!continuation.isNullOrBlank()}" }
                    }
            }
        // Store this job
        }.also( getSearchResultsJob::store )
    }

    object GetMore
}