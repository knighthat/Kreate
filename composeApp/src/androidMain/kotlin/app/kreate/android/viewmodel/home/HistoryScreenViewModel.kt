package app.kreate.android.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kreate.android.R
import app.kreate.android.utils.innertube.InnertubeUtils
import app.kreate.gateway.innertube.YouTube
import app.kreate.gateway.innertube.models.InnertubeHistory
import app.kreate.preferences.Preferences
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.utils.Toaster


class HistoryScreenViewModel(
    private val youtube: YouTube
) : ViewModel() {

    private val _history = MutableStateFlow<InnertubeHistory?>(null)

    val history = _history.asStateFlow()

    fun loadHistory() {
        if( !(InnertubeUtils.isLoggedIn && Preferences.YOUTUBE_ALBUMS_SYNC.value) )
            return

        viewModelScope.launch( Dispatchers.IO ) {
            youtube.account
                   .getHistory()
                   .onFailure { err ->
                       Logger.e( "", err, "HistoryScreenViewModel" )
                       Toaster.e( R.string.error_failed_to_get_history )
                   }
                   .onSuccess { history ->
                       _history.update { history }
                   }
        }
    }
}