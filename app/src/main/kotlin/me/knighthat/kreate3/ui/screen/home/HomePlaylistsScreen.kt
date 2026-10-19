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

package me.knighthat.kreate3.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.ui.component.CreatePlaylistButton
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.PlaylistItem
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.RandomButton
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.previewPlaylists
import me.knighthat.kreate3.ui.navigation.Tab
import org.koin.androidx.compose.koinViewModel


private const val PLAYLIST_ITEM_SIZE = 220

/**
 * Every playlist of the active user, as many to a row as fit.
 */
@Composable
fun HomePlaylistsScreen(
    onOpen: (MediaItem) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomePlaylistsViewModel = koinViewModel(),
) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val choices by viewModel.filter.choices.collectAsStateWithLifecycle()
    val selected by viewModel.filter.selected.collectAsStateWithLifecycle()
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    var creating by rememberSaveable { mutableStateOf(false) }

    // Each visit to the tab, the items of the external sources are fetched again
    LaunchedEffect(Unit) { viewModel.refresh() }

    HomePlaylistsContent(
        playlists = viewModel.playlists.collectAsLazyPagingItems(),
        live = live,
        choices = choices,
        selected = selected,
        onToggleOrigin = viewModel.filter::toggle,
        onOpen = onOpen,
        onRandom = {
            scope.launch {
                val picked = viewModel.random()
                if (picked != null) onOpen(picked)
                else notifier.post("No playlists to pick from")
            }
        },
        onCreate = { creating = true },
        onSearch = onSearch,
        onSettings = onSettings,
        modifier = modifier,
    )

    if (creating)
        CreatePlaylistDialog(
            onCreate = {
                viewModel.create(it)
                creating = false
            },
            onDismiss = { creating = false },
        )
}

/**
 * Asks for the name of a new playlist, a name is needed.
 */
@Composable
private fun CreatePlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onCreate(name) }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * @param live the playlists of the external sources, after the ones of the database
 */
@Composable
private fun HomePlaylistsContent(
    playlists: LazyPagingItems<MediaItem>,
    live: List<MediaItem>,
    choices: List<Origin>,
    selected: Set<Origin>,
    onToggleOrigin: (Origin) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onRandom: () -> Unit,
    onCreate: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeScaffold(
        title = Tab.PLAYLISTS.label,
        choices = choices,
        selected = selected,
        onToggleOrigin = onToggleOrigin,
        modifier = modifier,
        onSearch = onSearch,
        onSettings = onSettings,
        quickActions = {
            CreatePlaylistButton(onCreate)
            RandomButton(onRandom)
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = PLAYLIST_ITEM_SIZE.dp),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            items(count = playlists.itemCount, key = playlists.itemKey { it.mediaId }) { index ->
                playlists[index]?.let { PlaylistItem(it, onClick = { onOpen(it) }) }
            }
            items(live, key = { it.liveKey }) {
                PlaylistItem(it, onClick = { onOpen(it) })
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun HomePlaylistsPreview() = PreviewTheme {
    HomePlaylistsContent(
        playlists = flowOf(PagingData.from(previewPlaylists())).collectAsLazyPagingItems(),
        live = previewPlaylists(Origin.YOUTUBE).take(3),
        choices = Origin.entries,
        selected = emptySet(),
        onToggleOrigin = {},
        onOpen = {},
        onRandom = {},
        onCreate = {},
        onSearch = {},
        onSettings = {},
    )
}
