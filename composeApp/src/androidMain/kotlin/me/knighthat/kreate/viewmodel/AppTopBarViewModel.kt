package me.knighthat.kreate.viewmodel

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.good_afternoon
import kreate.composeapp.generated.resources.good_evening
import kreate.composeapp.generated.resources.good_morning
import kreate.composeapp.generated.resources.good_night
import me.knighthat.kreate.di.SharedSearchProperties
import me.knighthat.kreate.di.TopLayoutConfiguration
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class AppTopBarViewModel(
    val searchProperties: SharedSearchProperties,
    val topLayoutConfiguration: TopLayoutConfiguration
): ViewModel() {

    companion object {

        private const val MENU_ICON_TRANSITION_DURATION = 300
    }

    val menuIconEnterTransition: EnterTransition
    val menuIconExitTransition: ExitTransition

    var title by mutableStateOf("")
        private set

    init {
        viewModelScope.launch {
            topLayoutConfiguration.title
                // Add a small artificial delay to make the title
                // appear after the transition from splashscreen to
                // content is finished.
                .onStart { delay( 200 ) }
                .mapLatest { title ->
                    title.ifBlank {
                        val now: Int = Clock.System
                                            .now()
                                            .toLocalDateTime(TimeZone.currentSystemDefault())
                                            .hour
                        val strRes = when (now) {
                            in 5..11 -> Res.string.good_morning
                            in 12..16 -> Res.string.good_afternoon
                            in 17..20 -> Res.string.good_evening
                            else -> Res.string.good_night
                        }
                        getString(strRes)
                    }
                }
                .flowOn( Dispatchers.Default )
                .collect {
                    this@AppTopBarViewModel.title = it
                }
        }
        //<editor-fold defaultstate="collapsed" desc="titleSwapTransition">
        // Makes space then appears slowly
        menuIconEnterTransition = expandHorizontally(
            animationSpec = tween( MENU_ICON_TRANSITION_DURATION )
        ) + fadeIn(
            animationSpec = tween( delayMillis = MENU_ICON_TRANSITION_DURATION )
        )
        // Slides up with fade out THEN shrink
        menuIconExitTransition = slideOutVertically(
            animationSpec = tween( MENU_ICON_TRANSITION_DURATION ),
            targetOffsetY = { -it }
        ) + fadeOut(
            animationSpec = tween( MENU_ICON_TRANSITION_DURATION )
        ) + shrinkHorizontally(
            animationSpec = tween( delayMillis = MENU_ICON_TRANSITION_DURATION )
        )
    }
}