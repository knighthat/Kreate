package me.knighthat.kreate.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.model.HomePage
import me.knighthat.innertube.model.Section
import me.knighthat.innertube.request.Localization
import me.knighthat.innertube.response.Continuation
import me.knighthat.kreate.component.LoadMoreContentType
import me.knighthat.kreate.di.TopLayoutConfiguration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeScreenViewModel(
    val topLayoutConfiguration: TopLayoutConfiguration
): ViewModel() {

    // These fields are placed as static fields to persist
    // even when user go to another route and comeback.
    private companion object {

        private val _homePage = MutableStateFlow<HomePage?>(null)
        private val _sections = MutableStateFlow<List<Section>>(emptyList())
        private val _continuation = MutableStateFlow<Continuation?>(null)
    }

    val homePage = _homePage.asStateFlow()
    val sections: StateFlow<List<Section>>
    val continuation: StateFlow<String?>

    var isRefreshing: Boolean by mutableStateOf( false )

    init {
        viewModelScope.launch {
            snapshotFlow { topLayoutConfiguration.lazyListState.layoutInfo. visibleItemsInfo }
                .filter { list ->
                    // Prevent this from running before home page is even loaded
                    _homePage.value != null && list.fastAny { it.contentType is LoadMoreContentType }
                }
                .collect {
                    onFetchMore()
                }
        }

        // Combining them ensures that contents of [_homePage]
        // won't be duplicated when new sections are added to [_sections]
        sections = combine( _homePage, _sections ) { homePage, sections ->
                val hpSections = homePage?.sections.orEmpty()
                hpSections + sections
            }
            .sample( 200.milliseconds )
            .mapLatest { list ->
                list.asSequence()
                    .filter { it.title == null || it.contents.isNotEmpty() }
                    .toList()
            }
            .distinctUntilChanged()
            .flowOn( Dispatchers.Default )
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed( 5.seconds.inWholeMilliseconds ),
                emptyList()
            )

        continuation = combine( _homePage, _continuation ) { homePage, c ->
                val hpContinuation = homePage?.continuations?.firstOrNull()
                hpContinuation ?: c
            }
            .mapLatest { c ->
                c?.nextContinuationData?.continuation
            }
            .distinctUntilChanged()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed( 5.seconds.inWholeMilliseconds ),
                null
            )
    }

    fun onRefresh() {
        isRefreshing = true

        viewModelScope.launch( Dispatchers.IO ) {

            val result = Innertube.homePage( Localization.EN_US )
                .onFailure { err ->
                    Logger.e( "failed to fetch home page", err, "HomeScreen" )
                }
                .getOrNull()

            _homePage.update { result }
            if( result != null ) {
                _continuation.update { result.continuations.firstOrNull() }
            }

            withContext( Dispatchers.Main ) {
                isRefreshing = false
            }
        }
    }

    fun onFetchMore() {
        val continuation = _continuation.value?.nextContinuationData?.continuation
        val visitorData = _homePage.value?.visitorData
        if( continuation == null || visitorData == null ) {
            Logger.d(
                messageString = "Can't fetch more item because continuation ($continuation) or visitorData ($visitorData) is null!",
                tag = "HomeScreen"
            )
            return
        }

        viewModelScope.launch( Dispatchers.IO ) {
            val result = Innertube.continuation( Localization.EN_US, visitorData, continuation, null )
                .onFailure { err ->
                    Logger.e( "failed to fetch continuation", err, "HomeScreen" )
                }
                .getOrNull() ?: return@launch

            _sections.update { it + result.sections }
            _continuation.update {
                result.continuations.firstOrNull()
            }
        }
    }
}