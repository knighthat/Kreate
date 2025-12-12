package me.knighthat.kreate.di

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate.component.TopBarTitle

class TopLayoutConfiguration {

    val lazyListState = LazyListState()
    val title = MutableStateFlow("")
    val titleSwapTransition: ContentTransform

    var background: String? by mutableStateOf( null )
    var isAppReady: Boolean by mutableStateOf( false )
        private set

    init {
        /**
         * Replaces current title with whatever key of [Title]
         * in a [androidx.compose.foundation.lazy.LazyColumn] that uses [lazyListState]
         */
        val coroutineScope = CoroutineScope(Dispatchers.Main.immediate)
        coroutineScope.launch {
            snapshotFlow { lazyListState.firstVisibleItemIndex }
                .mapNotNull { _ ->
                    lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? TopBarTitle
                }
                .collect { title ->
                    this@TopLayoutConfiguration.title.update { title.title }
                }
        }

        //<editor-fold defaultstate="collapsed" desc="titleSwapTransition">
        // Always slides up
        val inSpec =
            slideInVertically(
                animationSpec = tween( durationMillis = 800 )
            ) { it } + fadeIn(
                animationSpec = tween( durationMillis = 1000 )
            )
        val outputSpec =
            slideOutVertically(
                animationSpec = tween( durationMillis = 800 )
            ) { -it } + fadeOut(
                animationSpec = tween( durationMillis = 2000 )
            )
        titleSwapTransition = inSpec togetherWith outputSpec
        //</editor-fold>
    }

    /**
     * Mark the initialization process is over.
     * Meaning, it can now display content to user.
     */
    fun showContent() {
        isAppReady = true
    }
}