package me.knighthat.kreate.viewmodel

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastDistinctBy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.model.Continued
import me.knighthat.innertube.model.InnertubeAlbum
import me.knighthat.innertube.model.InnertubeArtist
import me.knighthat.innertube.model.InnertubeContinuation
import me.knighthat.innertube.model.InnertubeItem
import me.knighthat.innertube.model.InnertubePlaylist
import me.knighthat.innertube.model.InnertubeSearch
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.innertube.request.Localization
import me.knighthat.innertube.response.Continuation
import me.knighthat.kreate.constant.SearchTab
import me.knighthat.kreate.di.SharedSearchProperties


class SearchResultViewModel(
    val sharedSearchProperties: SharedSearchProperties
): ViewModel() {

    companion object {
        const val LOAD_MORE_KEY = "79029ad30248662cb810a1e77fb08d4242e3c9c5"        // "load_more" in sha1
    }

    private val _accrue = MutableStateFlow(emptyList<InnertubeItem>())
    // Map contains SearchTab, visitorData, and continuation string
    private val _continuation = MutableStateFlow<Map<SearchTab, Pair<String?, Continuation?>>>(emptyMap())

    val lazyListState = LazyListState()
    val continuation: StateFlow<Map<SearchTab, Pair<String?, String?>>>
    val results: StateFlow<List<InnertubeItem>>

    init {
        continuation = _continuation.map { map ->
            map.mapValues { (tab, continuation) ->
                continuation.first to continuation.second?.nextContinuationData?.continuation
            }
        }.stateIn( viewModelScope, SharingStarted.Lazily, emptyMap() )

        results = combine( sharedSearchProperties.tab, _accrue ) { tab, accrue ->
                when( tab ) {
                    SearchTab.SONGS        -> accrue.filterIsInstance<InnertubeSong>()
                    SearchTab.ALBUMS       -> accrue.filterIsInstance<InnertubeAlbum>()
                    SearchTab.ARTISTS      -> accrue.filterIsInstance<InnertubeArtist>()
                    SearchTab.PLAYLISTS    -> accrue.filterIsInstance<InnertubePlaylist>()
                }
            }
            .map { it.fastDistinctBy(InnertubeItem::id) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default )
            .stateIn( viewModelScope, SharingStarted.Lazily, emptyList() )

        viewModelScope.launch {
            combine(
                sharedSearchProperties.input,
                sharedSearchProperties.tab
            ) { query, tab ->
                query.text to tab
            }.collect { (query, tab) ->
                if( query.isBlank() || continuation.value.containsKey( tab ) )
                    return@collect

                val result = onSearch( query, tab )
                if( result != null ) {
                    _accrue.update { it + result.items }
                    updateContinuation( result )
                }
            }
        }
        viewModelScope.launch {
            snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo }
                .filter { list ->
                    list.fastAny { it.key == LOAD_MORE_KEY }
                }
                .mapNotNull { onLoadMore() }
                .flowOn( Dispatchers.IO )
                .collect { result ->
                    val items = result.sections.flatMap { it.contents }
                    _accrue.update { it + items }
                    updateContinuation( result )
                }
        }
    }

    private fun updateContinuation( continued: Continued ) {
        val tab = sharedSearchProperties.tab.value
        val visitorData = continued.visitorData
        val continuation = continued.continuations.firstOrNull()
        _continuation.update {
            it.toMutableMap().apply {
                put( tab, visitorData to continuation )
            }
        }
    }

    private suspend fun onSearch( query: String, tab: SearchTab ): InnertubeSearch? =
        Innertube.search( Localization.EN_US, query, tab.params )
                 .onFailure { err ->
                     Logger.e( "failed to fetch results for search", err, "SearchResults" )
                 }
                 .getOrNull()

    private suspend fun onLoadMore(): InnertubeContinuation? {
        val tab = sharedSearchProperties.tab.value
        val (visitorData, continuation) = continuation.value.getOrDefault(tab, null to null)

        if( continuation.isNullOrBlank() || visitorData.isNullOrBlank() ) {
            Logger.w( "SearchResults" ) {
                "failed to load more because continuation string (%b) or visitorData (%b) is null"
                    .format(
                        continuation != null,
                        visitorData != null
                    )
            }
            return null
        }

        return Innertube.searchContinuation( Localization.EN_US, visitorData, continuation )
                        .onFailure { err ->
                            Logger.e( "failed to load more", err, "SearchResults" )
                        }
                        .getOrNull()
    }
}