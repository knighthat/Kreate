package app.kreate.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kreate.gateway.innertube.YouTube
import app.kreate.gateway.innertube.models.InnertubeSearchSuggestion
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.error_failed_to_get_search_suggestions
import me.knighthat.utils.Toaster
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi


@OptIn(ExperimentalAtomicApi::class)
class OnlineSearchViewModel : ViewModel(), KoinComponent {

    private val _suggestion = MutableStateFlow<InnertubeSearchSuggestion?>(null)
    private val _isFetchingSuggestions = MutableStateFlow(false)
    private val getSuggestionJob = AtomicReference<Job?>(null)

    val suggestion = _suggestion.asStateFlow()
    val isFetchingSuggestions = _isFetchingSuggestions.asStateFlow()

    fun onQueryChanged( query: String ) {
        getSuggestionJob.exchange( null )?.cancel()

        if( query.isBlank() ) {
            // Remove all suggestions if no query provided
            _suggestion.update { null }
            return
        }

        viewModelScope.launch( Dispatchers.IO ) {
            _isFetchingSuggestions.update { true }

            // TODO: Apply a small delay between to reduce rate
            get<YouTube>()
                .getSearchSuggestions( query )
                .onFailure { err ->
                    Logger.e( "", err, "OnlineSearchViewModel" )
                    getString( Res.string.error_failed_to_get_search_suggestions ).also( Toaster::e )
                }
                .onSuccess { newSuggestion ->
                    _suggestion.update { newSuggestion }

                    Logger.d( tag = "OnlineSearchViewModel" ) {
                        "Suggestions for \"$query\": ${newSuggestion.suggestions.size} completions, ${newSuggestion.items.size} items"
                    }
                }

            _isFetchingSuggestions.update { false }
        // Store this job
        }.also( getSuggestionJob::store )
    }
}