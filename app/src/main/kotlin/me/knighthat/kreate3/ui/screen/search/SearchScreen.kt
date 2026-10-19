/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.ui.screen.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.media3.common.MediaItem
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.BackButton
import me.knighthat.kreate3.ui.component.ConfigButton
import me.knighthat.kreate3.ui.component.ItemRow
import me.knighthat.kreate3.ui.component.MessageHost
import me.knighthat.kreate3.ui.component.PlaybackMiniPlayer
import me.knighthat.kreate3.ui.component.SearchButton
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.component.label
import me.knighthat.kreate3.ui.navigation.Tab
import org.koin.androidx.compose.koinViewModel
import kotlin.math.abs

/** How many matches the suggestions list before the searches. */
private const val SUGGESTION_LIMIT = 3

/** Size and opacity of an item one place away from the middle, the middle one is `1`. */
private const val SIDE_SCALE = 0.75f
private const val SIDE_ALPHA = 0.5f

/** Kinds of matches, the first one wins when there is not room for all. */
private val SUGGESTION_PRIORITY = listOf(Tab.SONGS, Tab.PLAYLISTS, Tab.ALBUMS, Tab.ARTISTS)

/** The middle of the visible part, where offsets of the items are counted from. */
private val LazyListState.middle: Float
    get() = layoutInfo.run { (viewportStartOffset + viewportEndOffset) / 2f }

/** Index of the item closest to the middle, `null` before the row is laid out. */
private fun LazyListState.centeredIndex(): Int? =
    layoutInfo.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - middle) }?.index

/** How far the item at [index] is from the middle, `0` there, `1` one place or more away. */
private fun LazyListState.distanceFromMiddle(index: Int): Float {
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return 1f
    return (abs(item.offset + item.size / 2f - middle) / item.size).coerceIn(0f, 1f)
}

/**
 * A row that keeps the [selected] item in the middle, with the items before and after it smaller
 * on either side. Each item shows once, so the first and last have a side to themselves.
 *
 * Swiping snaps the nearest item to the middle and selects it; tapping an item slides it there.
 */
@Composable
fun <T> CenteredPicker(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val selectedIndex = items.indexOf(selected).coerceAtLeast(0)
    // The padding before the first item lets the first visible one sit in the middle
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val scope = rememberCoroutineScope()
    val currentItems by rememberUpdatedState(items)
    val currentOnSelect by rememberUpdatedState(onSelect)

    // Once a swipe or a tap settles, the item in the middle is the chosen one
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .filter { !it }
            .collect {
                state.centeredIndex()?.let { currentItems.getOrNull(it)?.let(currentOnSelect) }
            }
    }

    // Chosen elsewhere: bring it to the middle
    LaunchedEffect(selectedIndex, items.size) {
        val centered = state.centeredIndex() ?: return@LaunchedEffect
        if (centered == selectedIndex || state.isScrollInProgress) return@LaunchedEffect
        state.animateScrollToItem(selectedIndex)
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Three places across: the middle one and one on each side
        val itemWidth = maxWidth / 3
        val fill = MaterialTheme.colorScheme.secondaryContainer
        val onFill = MaterialTheme.colorScheme.onSecondaryContainer

        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
            contentPadding = PaddingValues(horizontal = itemWidth),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .selectableGroup(),
        ) {
            items(count = items.size) { index ->
                val item = items[index]
                val isSelected = index == selectedIndex

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val distance = state.distanceFromMiddle(index)
                            val scale = lerp(1f, SIDE_SCALE, distance)
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(1f, SIDE_ALPHA, distance)
                        }
                        .padding(horizontal = 4.dp)
                        // The fill fades in as the item reaches the middle
                        .drawBehind {
                            val strength = 1f - state.distanceFromMiddle(index)
                            drawRoundRect(
                                color = fill.copy(alpha = fill.alpha * strength),
                                cornerRadius = CornerRadius(size.height / 2),
                            )
                        }
                        .semantics {
                            role = Role.RadioButton
                            this.selected = isSelected
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = true,
                            onClickLabel = "Select source",
                            role = Role.RadioButton,
                            onClick = { scope.launch { state.animateScrollToItem(index) } }
                        ),
                ) {
                    Text(
                        text = label(item),
                        style = MaterialTheme.typography.titleSmall,
                        color = onFill,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}


/**
 * The text box with its two buttons, it sits where the bottom bar is on other screens.
 */
@Composable
private fun SearchInputBar(
    text: String,
    placeholder: String,
    onTextChange: (String) -> Unit,
    onSearch: () -> Unit,
    onConfig: () -> Unit,
    focusRequester: FocusRequester,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text(placeholder) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            ConfigButton(onConfig)
            SearchButton(onSearch)
        }
    }
}

/**
 * Chooses where to search, and which kinds of items the suggestions show.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConfigSheet(
    origin: Origin,
    onOriginChange: (Origin) -> Unit,
    categories: Set<Tab>,
    onCategoryChange: (Tab, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SectionTitle("Source")
        // The server is only offered while it is in use
        val serverEnabled by Preferences.OPEN_SUBSONIC_ENABLED.collectAsStateWithLifecycle()
        CenteredPicker(
            items = Origin.entries.filter { it != Origin.OPEN_SUBSONIC || serverEnabled },
            selected = origin,
            onSelect = onOriginChange,
            label = { it.label },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        SectionTitle("Look for")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            // Remove ripple effect on select
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                Tab.entries.forEach {
                    FilterChip(
                        selected = it in categories,
                        onClick = { onCategoryChange(it, it !in categories) },
                        label = { Text(it.label) },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * What kind of item a suggestion is, so rows of different kinds can share one list.
 */
@Composable
private fun KindBadge(kind: Tab) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(kind.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = kind.label.removeSuffix("s"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private class Suggestion(val kind: Tab, val item: MediaItem)

/**
 * The best [SUGGESTION_LIMIT] matches of all [results]. Each kind gives its best match, then its
 * second best, and so on, so a single kind that matched fills the list by itself. Kinds in
 * [SUGGESTION_PRIORITY] come first.
 */
private fun suggestionsOf(results: Map<Tab, LazyPagingItems<MediaItem>>): List<Suggestion> {
    val picked = mutableListOf<Suggestion>()
    for (round in 0 until SUGGESTION_LIMIT)
        for (kind in SUGGESTION_PRIORITY) {
            val found = results[kind]?.takeIf { round < it.itemCount }?.peek(round)
            if (found != null && picked.size < SUGGESTION_LIMIT) picked += Suggestion(kind, found)
        }
    return picked.sortedBy { SUGGESTION_PRIORITY.indexOf(it.kind) }
}

/**
 * Suggestions while the user types: library items that match the text, and below them the
 * earlier searches that start with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenAlbum: (MediaItem) -> Unit,
    onOpenArtist: (MediaItem) -> Unit,
    onOpenPlaylist: (MediaItem) -> Unit,
    onSearch: (query: String, origin: Origin, tab: Tab) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val text by viewModel.text.collectAsState()
    val savedOrigin by Preferences.SEARCH_ORIGIN.collectAsStateWithLifecycle()
    val serverEnabled by Preferences.OPEN_SUBSONIC_ENABLED.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val origin by viewModel.origin.collectAsState()
    val recent by viewModel.recentSearches.collectAsState()
    val live by viewModel.liveSuggestions.collectAsState()
    val songs = viewModel.songs.collectAsLazyPagingItems()
    val albums = viewModel.albums.collectAsLazyPagingItems()
    val artists = viewModel.artists.collectAsLazyPagingItems()
    val playlists = viewModel.playlists.collectAsLazyPagingItems()

    // Records the search and opens its results, a blank text searches for nothing
    val search = { term: String ->
        if (term.isNotBlank()) {
            viewModel.submit()
            // Opens on the first chosen kind, from the left
            onSearch(term.trim(), origin, Tab.entries.firstOrNull { it in categories } ?: Tab.SONGS)
        }
    }

    // The saved origin, unless it is the server and the server is off now. It stays saved, so
    // the search goes back to the server once it is on again.
    LaunchedEffect(savedOrigin, serverEnabled) {
        val origin = Origin.entries.firstOrNull { it.name == savedOrigin }
            ?.takeIf { it != Origin.OPEN_SUBSONIC || serverEnabled }
        viewModel.setOrigin(origin ?: Origin.LOCAL)
    }

    var showConfig by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val suggestions = if (origin == Origin.LOCAL)
        suggestionsOf(
            mapOf(
                Tab.SONGS to songs,
                Tab.PLAYLISTS to playlists,
                Tab.ALBUMS to albums,
                Tab.ARTISTS to artists,
            ).filterKeys { it in categories }
        )
    else live.items
        // Online catalogs suggest every kind, only the chosen ones show
        .filter { (kind, _) -> kind in categories }
        .map { (kind, item) -> Suggestion(kind, item) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = { BackButton(onBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceDim,
                ),
            )
        },
        bottomBar = {
            // The search bar stays last, next to the keyboard
            Column {
                PlaybackMiniPlayer()
                SearchInputBar(
                    text = text,
                    placeholder = when (origin) {
                        Origin.LOCAL -> "Search your library"
                        Origin.YOUTUBE -> "Search YouTube"
                        Origin.OPEN_SUBSONIC -> "Search your server"
                    },
                    onTextChange = viewModel::setText,
                    onSearch = { search(text) },
                    onConfig = { showConfig = true },
                    focusRequester = focusRequester,
                )
            }
        },
        // Above the search bar, the app's scaffold would put messages over it
        snackbarHost = { MessageHost() },
        containerColor = MaterialTheme.colorScheme.surfaceDim,
        modifier = modifier,
    ) { innerPadding ->
        if (suggestions.isEmpty() && live.queries.isEmpty() && recent.isEmpty()) {
            Text(
                text = when {
                    query.isEmpty() -> "Your searches will show up here"
                    else -> "No suggestions for \"$query\""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp),
            )
        } else
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(suggestions, key = { it.kind.name + ":" + it.item.mediaId }) {
                    ItemRow(
                        item = it.item,
                        onClick = {
                            when (it.kind) {
                                Tab.ALBUMS -> onOpenAlbum(it.item)
                                Tab.ARTISTS -> onOpenArtist(it.item)
                                Tab.PLAYLISTS -> onOpenPlaylist(it.item)
                                Tab.SONGS -> {}
                            }
                        },
                        trailing = { KindBadge(it.kind) },
                    )
                }
                if (live.queries.isNotEmpty()) {
                    if (suggestions.isNotEmpty())
                        item(key = "live-title") { SectionTitle("Suggested searches") }
                    items(live.queries, key = { "live:$it" }) { suggestion ->
                        ListItem(
                            headlineContent = { Text(suggestion) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                viewModel.setText(suggestion)
                                search(suggestion)
                            },
                        )
                    }
                }
                if (recent.isNotEmpty()) {
                    if (suggestions.isNotEmpty() || live.queries.isNotEmpty())
                        item(key = "recent-title") {
                            SectionTitle(if (text.isBlank()) "Recent searches" else "Searches")
                        }
                    items(recent, key = { "recent:" + it.query }) { recentSearch ->
                        ListItem(
                            headlineContent = { Text(recentSearch.query) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                viewModel.setText(recentSearch.query)
                                search(recentSearch.query)
                            },
                        )
                    }
                }
            }
    }

    if (showConfig)
        ConfigSheet(
            origin = origin,
            onOriginChange = { Preferences.SEARCH_ORIGIN.update(it.name) },
            categories = categories,
            onCategoryChange = viewModel::setCategory,
            onDismiss = { showConfig = false },
        )
}
