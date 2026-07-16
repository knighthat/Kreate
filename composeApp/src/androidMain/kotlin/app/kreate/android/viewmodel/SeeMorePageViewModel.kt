package app.kreate.android.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kreate.compose.R
import app.kreate.gateway.innertube.YouTube
import app.kreate.gateway.innertube.models.Section
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.utils.Toaster


class SeeMorePageViewModel(
    savedStateHandle: SavedStateHandle,
    private val youTube: YouTube
) : ViewModel() {

    private val _sections = MutableStateFlow(emptyList<Section>())
    private val _isRefreshing = MutableStateFlow(false)

    val browseId: String = savedStateHandle["browseId"]!!
    val params: String? = savedStateHandle["params"]
    val sections = _sections.asStateFlow()
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        onRefresh()
    }

    fun onRefresh() {
        _isRefreshing.update { true }

        _sections.update { emptyList() }

        viewModelScope.launch( Dispatchers.IO ) {
            youTube.getSeeMorePage( browseId, params )
                   .onFailure { err ->
                       Logger.e( "", err, "SeeMorePageViewModel" )
                       Toaster.e( R.string.error_generic_failed_to_load_page )
                   }
                   .onSuccess { sections ->
                       _sections.update { sections }
                   }

            _isRefreshing.update { false }
        }
    }
}