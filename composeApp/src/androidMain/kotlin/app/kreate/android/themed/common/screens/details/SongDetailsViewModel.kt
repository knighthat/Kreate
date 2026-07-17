package app.kreate.android.themed.common.screens.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.kreate.compose.R
import app.kreate.gateway.innertube.YouTube
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.gateway.innertube.models.InnertubeSongDetails
import app.kreate.utils.Toaster
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class SongDetailsViewModel(private val songId: String): ViewModel(), KoinComponent {

    var songDetails: InnertubeSongDetails? by mutableStateOf(null)
        private set
    var songBasicInfo: InnertubeSong? by mutableStateOf(null)
        private set

    init {
        fetchDetails()
        fetchBasicInfo()
    }

    private fun fetchDetails() = CoroutineScope(Dispatchers.IO).launch {
        get<YouTube>()
            .getSongDetails( songId )
            .onSuccess { songDetails = it }
            .onFailure { err ->
                Logger.e( "", err, "SongDetails" )
                Toaster.e(  R.string.error_failed_to_fetch_songs_info )
            }
    }

    private fun fetchBasicInfo() = CoroutineScope(Dispatchers.IO).launch {
        get<YouTube>().getSongBasicInfo( songId )
                 .onSuccess { songBasicInfo = it }
                 .onFailure { err ->
                     Logger.e( "", err, "SongDetails" )
                     Toaster.e( R.string.error_failed_to_fetch_songs_info )
                 }
    }

    class Factory(private val songId: String): ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SongDetailsViewModel(songId) as T
    }
}