package app.kreate.android.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kreate.android.R
import app.kreate.gateway.innertube.YouTube
import app.kreate.gateway.innertube.models.InnertubeCharts
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.utils.Toaster
import org.koin.core.component.KoinComponent
import org.koin.core.component.get


class HomeQuickPicksViewModel : ViewModel(), KoinComponent {

    private val _charts = MutableStateFlow<InnertubeCharts?>(null)

    val charts = _charts.asStateFlow()

    fun loadCharts() {
        viewModelScope.launch( Dispatchers.IO ) {

            get<YouTube>().getCharts()
                .onFailure { err ->
                    Logger.e( "", err, "HomeQuickPicks" )
                    Toaster.e( R.string.error_failed_to_get_charts )
                }
                .onSuccess { charts ->
                    _charts.update { charts }
                }
        }
    }
}