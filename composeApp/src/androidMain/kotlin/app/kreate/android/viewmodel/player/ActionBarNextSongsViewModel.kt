package app.kreate.android.viewmodel.player

import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import app.kreate.android.Preferences
import app.kreate.android.service.player.StatefulPlayer
import it.fast4x.rimusic.utils.mediaItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


class ActionBarNextSongsViewModel(
    private val composeScope: CoroutineScope
) : ViewModel(), KoinComponent {

    val player: StatefulPlayer by inject()
    val numSongsToShow by Preferences.MAX_NUMBER_OF_NEXT_IN_QUEUE
    val pager: PagerState
    val viewPort: PageSize

    var currentMediaItem by mutableStateOf<MediaItem?>(null)
    var currentIndex by mutableIntStateOf( 0 )
    var queue by mutableStateOf( emptyList<MediaItem>() )

    init {
        pager = PagerState { queue.size }
        viewPort = PagerViewPort( pager )

        // Modification to MutableState should happen on Main thread
        viewModelScope.launch( Dispatchers.Main ) {
            player.currentTimelineState.collectLatest {
                queue = it.mediaItems
            }
        }
        viewModelScope.launch( Dispatchers.Main ) {
            player.currentMediaItemState.collectLatest {
                currentMediaItem = it
                currentIndex = player.currentMediaItemIndex
                scrollToNext()
            }
        }
    }

    fun scrollTo( page: Int ) {
        composeScope.launch {
            pager.animateScrollToPage( page )
        }
    }

    fun scrollToNext() {
        val nextIndex = currentIndex + 1
        val currentPage = pager.currentPage
        val page = nextIndex.coerceIn( 0, pager.pageCount )

        if(
            C.INDEX_UNSET == nextIndex
            || (page > currentPage && !pager.canScrollForward)
            || (page < currentPage && !pager.canScrollBackward)
        )
            return
        else
            scrollTo( page )
    }

    inner class PagerViewPort(
        private val pagerState: PagerState
    ): PageSize {

        override fun Density.calculateMainAxisPageSize( availableSpace: Int, pageSpacing: Int ): Int {
            val canShow = minOf( numSongsToShow.toInt() , pagerState.pageCount )
            return if( canShow > 1 )
                (availableSpace - 2 * pageSpacing) / canShow
            else
                availableSpace
        }
    }
}