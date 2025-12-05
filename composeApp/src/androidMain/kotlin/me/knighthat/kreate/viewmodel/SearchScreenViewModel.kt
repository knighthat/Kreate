package me.knighthat.kreate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.model.InnertubeItem
import me.knighthat.innertube.model.InnertubeSearchSuggestion
import me.knighthat.innertube.request.Localization
import me.knighthat.kreate.di.SharedSearchProperties


class SearchScreenViewModel(
    sharedSearchProperties: SharedSearchProperties
) : ViewModel() {

    private val _result = MutableStateFlow<InnertubeSearchSuggestion?>( null )

    val input = sharedSearchProperties.input
    val suggestions: StateFlow<List<InnertubeSearchSuggestion.Suggestion>>
    val items: StateFlow<List<InnertubeItem>>

    init {
        viewModelScope.launch( Dispatchers.IO ) {
            sharedSearchProperties.input
                                  .filter { it.text.isNotBlank() }
                                  .collect { value ->
                                      val input = value.text
                                      val result = onSearch( input )
                                      _result.update { result }
                                  }
        }
        suggestions = _result.mapNotNull { it?.suggestions }
                             .stateIn(
                                 scope = viewModelScope,
                                 started = SharingStarted.Lazily,
                                 initialValue = emptyList()
                             )
        items = _result.mapNotNull { it?.items }
                       .stateIn(
                           scope = viewModelScope,
                           started = SharingStarted.Lazily,
                           initialValue = emptyList()
                       )
    }

    private suspend fun onSearch( input: String ) =
        Innertube.searchSuggestion( Localization.EN_US, input )
                 .onFailure { err ->
                     Logger.e( "failed to get search suggestion", err, "SearchSuggestion" )
                 }
                 .getOrNull()
}