package app.kreate.android.themed.common.screens.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.kreate.android.R
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.innertube.model.InnertubeSongDetails
import me.knighthat.utils.Toaster
import timber.log.Timber

class SongDetailsViewModel(private val songId: String): ViewModel() {

    var songDetails: InnertubeSongDetails? by mutableStateOf(null)
        private set
    var songBasicInfo: InnertubeSong? by mutableStateOf(null)
        private set

    init {
        fetchDetails()
        fetchBasicInfo()
    }

    private fun fetchDetails() = CoroutineScope(Dispatchers.IO).launch {
        Innertube.songInfo( songId, CURRENT_LOCALE)
                 .onSuccess { songDetails = it }
                 .onFailure { err ->
                     Timber.tag( "SongDetails" ).e( err )
                     Toaster.e(  R.string.error_failed_to_fetch_songs_info )
                 }
    }

    private fun fetchBasicInfo() = CoroutineScope(Dispatchers.IO).launch {
        Innertube.songBasicInfo( songId, CURRENT_LOCALE)
                 .onSuccess { songBasicInfo = it }
                 .onFailure { err ->
                     Timber.tag( "SongDetails" ).e( err )
                     Toaster.e( R.string.error_failed_to_fetch_songs_info )
                 }
    }

    class Factory(private val songId: String): ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SongDetailsViewModel(songId) as T
    }
}