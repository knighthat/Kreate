package it.fast4x.rimusic.ui.screens.history

import androidx.annotation.OptIn
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastDistinctBy
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.database.models.Event
import app.kreate.util.EXPLICIT_PREFIX
import it.fast4x.compose.persist.persist
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.requests.HistoryPage
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerAwareWindowInsets
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.HistoryType
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.thumbnailShape
import it.fast4x.rimusic.ui.components.ButtonsRow
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import it.fast4x.rimusic.ui.components.themed.NonQueuedMediaItemMenuLibrary
import it.fast4x.rimusic.ui.components.themed.Title
import it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.DisposableListener
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import it.fast4x.rimusic.utils.forcePlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@kotlin.OptIn(ExperimentalTextApi::class)
@OptIn(UnstableApi::class)
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HistoryList(
    navController: NavController
) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current ?: return
    val hapticFeedback = LocalHapticFeedback.current
    val (colorPalette, typography) = LocalAppearance.current
    val menuState = LocalMenuState.current

    val parentalControlEnabled by Preferences.PARENTAL_CONTROL

    /**
     * Topology:
     *
     * Get all events with its song attached to it as.
     * On each event, calculate days since [Event.timestamp] is assigned.
     *
     * There are 5 categories of this:
     * - "Today",
     * - "Yesterday",
     * - "This week",
     * - "Last week",
     * - The rest are represented in "MMM yyyy" format
     *
     * This function will group each event into their correct category
     * and **remember** it.
     */
    val events by remember {
        val currentMillis = System.currentTimeMillis()

        Database.eventTable
                .allWithSong()
                .distinctUntilChanged()
                .map { list ->
                    list.filter { !parentalControlEnabled || it.song.title.startsWith( EXPLICIT_PREFIX, true ) }
                        .reversed()
                        .groupBy {
                            val diffMillis = currentMillis - it.event.timestamp
                            val daysAgo = TimeUnit.MILLISECONDS.toDays( diffMillis )

                            when (daysAgo) {
                                0L -> context.getString( R.string.today )
                                1L -> context.getString( R.string.yesterday )
                                in 2..6 -> context.getString( R.string.last_week )
                                in 7..13 -> context.getString( R.string.last_week )
                                else -> SimpleDateFormat( "MMM yyyy", Locale.getDefault() ).format( Date(it.event.timestamp) )
                            }
                        }
                }
    }.collectAsState( emptyMap(), Dispatchers.IO )

    val buttonsList = mutableListOf(HistoryType.History to stringResource(R.string.history))
    buttonsList += HistoryType.YTMHistory to stringResource(R.string.yt_history)

    var historyType by Preferences.HISTORY_PAGE_TYPE

    var historyPage by persist<Result<HistoryPage>>("home/historyPage")
    LaunchedEffect(Unit, historyType) {
        if (isYouTubeLoggedIn())
            historyPage = YtMusic.getHistory()
    }

    Column (
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth(
                if( NavigationBarPosition.Right.isCurrent() )
                    Dimensions.contentWidthRightBar
                else
                    1f
            )
    ) {

        var currentlyPlaying by remember { mutableStateOf(binder.player.currentMediaItem?.mediaId) }
        binder.player.DisposableListener {
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int ) {
                    currentlyPlaying = mediaItem?.mediaId
                }
            }
        }
        val songItemValues = remember( colorPalette, typography ) {
            SongItem.Values.from( colorPalette, typography )
        }

        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
            modifier = Modifier
                .background(colorPalette().background0)
                .fillMaxSize()
        ) {

            item(key = "header", contentType = 0) {
                HeaderWithIcon(
                    title = stringResource(R.string.history),
                    iconId = R.drawable.history,
                    enabled = false,
                    showIcon = false,
                    modifier = Modifier,
                    onClick = {}
                )
            }

            item(
                key = "tabList", contentType = 0,
            ) {
                ButtonsRow(
                    chips = buttonsList,
                    currentValue = historyType,
                    onValueUpdate = { historyType = it },
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp)
                )
            }

            if( historyType == HistoryType.History )
                events.forEach { (headerStr, details) ->
                    stickyHeader {
                        Title(
                            title = headerStr,
                            modifier = Modifier.background(
                                color = colorPalette().background3,
                                shape = thumbnailShape()
                            )
                        )
                    }

                    items(
                        items = details.fastDistinctBy { it.song.id },
                        key = { it.event.id }
                    ) { event ->

                        SongItem.Render(
                            song = event.song,
                            context = context,
                            binder = binder,
                            hapticFeedback = hapticFeedback,
                            isPlaying = currentlyPlaying == event.song.id,
                            values = songItemValues,
                            navController = navController,
                            onClick = {
                                binder.player.forcePlay( event.song )
                            }
                        )
                    }
                }

            if ( historyType == HistoryType.YTMHistory )
                historyPage?.getOrNull()?.sections?.forEach { section ->
                    stickyHeader {
                        Title(
                            title = section.title,
                            modifier = Modifier.background(
                                color = colorPalette().background3,
                                shape = thumbnailShape()
                            )
                        )
                    }

                    items(
                        items = section.songs
                                       .map { it.asMediaItem }
                                       .filter { it.mediaId.isNotEmpty() },
                        key = { it.mediaId }
                    ) { mediaItem ->
                        SongItem.Render(
                            song = mediaItem.asSong,
                            context = context,
                            binder = binder,
                            hapticFeedback = hapticFeedback,
                            isPlaying = currentlyPlaying == mediaItem.mediaId,
                            values = songItemValues,
                            navController = navController,
                            onClick = {
                                binder.player.forcePlay( mediaItem )
                            },
                            onLongClick = {
                                menuState.display {
                                    NonQueuedMediaItemMenuLibrary(
                                        navController = navController,
                                        mediaItem = mediaItem,
                                        onDismiss = menuState::hide
                                    )
                                }
                            }
                        )
                    }
                }
        }
    }
}

