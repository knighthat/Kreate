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

package me.knighthat.kreate3.ui.screen.player

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.lyrics.LyricsFinder
import me.knighthat.kreate3.model.LyricsSync
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import org.koin.compose.koinInject

/**
 * The buttons of [LyricsPage]: lyrics of another source, a search of every source with other
 * words, how finely the lyrics are timed, how large their text is, and copying them. Those that
 * change the lyrics shown are in the primary color while they do.
 */
@Composable
internal fun LyricsButtons(
    item: MediaItem,
    controls: LyricsControls,
    modifier: Modifier = Modifier,
    finder: LyricsFinder = koinInject(),
    notifier: Notifier = koinInject(),
) = Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier,
) {
    val shown = controls.shown
    val request = controls.request
    // Songs without an origin or a title can't be looked up
    val searchable = remember(item) { item.lyricsQuery() != null }
    val textSize by Preferences.LYRICS_TEXT_SIZE.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf<LyricsMenu?>(null) }
    var searching by remember { mutableStateOf(false) }

    Box {
        ModeButton(
            icon = R.drawable.swap_horiz,
            description = stringResource(R.string.semantic_change_source),
            isOn = request?.provider != null,
            enabled = searchable,
            onClick = { menu = LyricsMenu.SOURCE },
        )
        DropdownMenu(expanded = menu == LyricsMenu.SOURCE, onDismissRequest = { menu = null }) {
            finder.sources.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source.name) },
                    // Room for the check on every row, the names line up
                    leadingIcon = {
                        if (shown?.source == source.name)
                            Icon(
                                painterResource(R.drawable.check),
                                contentDescription = stringResource(R.string.semantic_shown)
                            )
                        else
                            Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        menu = null
                        // Its stored lyrics first, typed words would always fetch
                        controls.request = LyricsRequest(provider = source.provider)
                    },
                )
            }
        }
    }
    ModeButton(
        icon = R.drawable.manage_search,
        description = stringResource(R.string.semantic_search_the_lyrics_with_other_words),
        isOn = request?.isSearch == true,
        enabled = searchable,
        onClick = { searching = true },
    )

    // Only ever less finely than they loaded, the timing can't be made up
    val loaded = shown?.content?.sync
    val timings = LyricsSync.entries.reversed().filter { loaded != null && it <= loaded }
    Box {
        ModeButton(
            icon = R.drawable.av_timer,
            description = stringResource(R.string.semantic_timing_of_the_lyrics),
            isOn = controls.sync != null,
            enabled = timings.size > 1,
            onClick = { menu = LyricsMenu.TIMING },
        )
        DropdownMenu(expanded = menu == LyricsMenu.TIMING, onDismissRequest = { menu = null }) {
            timings.forEach { timing ->
                DropdownMenuItem(
                    text = { Text(stringResource(timing.labelRes)) },
                    // Room for the check on every row, the labels line up
                    leadingIcon = {
                        if ((controls.sync ?: loaded) == timing)
                            Icon(
                                painterResource(R.drawable.check),
                                contentDescription = stringResource(R.string.semantic_shown)
                            )
                        else
                            Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        menu = null
                        controls.sync = timing.takeIf { it != loaded }
                    },
                )
            }
        }
    }

    Box {
        ModeButton(
            icon = R.drawable.format_size,
            description = stringResource(R.string.semantic_text_size),
            isOn = textSize != Preferences.LYRICS_TEXT_SIZE.default,
            // Nothing to see the size on while there are no lyrics
            enabled = shown != null,
            onClick = { menu = LyricsMenu.TEXT_SIZE },
        )
        DropdownMenu(expanded = menu == LyricsMenu.TEXT_SIZE, onDismissRequest = { menu = null }) {
            TextSizeStepper(textSize)
        }
    }

    val lyricsCopied = stringResource(R.string.snackbar_lyrics_copied)

    ModeButton(
        icon = R.drawable.content_copy,
        description = stringResource(R.string.semantic_copy_the_lyrics),
        isOn = false,
        enabled = shown != null,
        onClick = {
            val text = shown?.content?.text ?: return@ModeButton
            scope.launch {
                clipboard.setClipEntry(ClipData.newPlainText("Lyrics", text).toClipEntry())
            }
            // Newer versions show what was copied themselves
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) notifier.post(lyricsCopied)
        },
    )

    if (searching)
        LyricsSearchDialog(
            title = request?.title ?: item.mediaMetadata.title?.toString().orEmpty(),
            artist = request?.artist ?: item.mediaMetadata.artist?.toString().orEmpty(),
            onSearch = { title, artist ->
                searching = false
                controls.request = LyricsRequest(title = title.trim(), artist = artist)
            },
            onDismiss = { searching = false },
        )
}

/** The menus of [LyricsButtons], at most one is open. */
private enum class LyricsMenu { SOURCE, TIMING, TEXT_SIZE }

/** Smaller and larger by a step of [LYRICS_TEXT_SIZES], the size in between. */
@Composable
private fun TextSizeStepper(size: Int) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(horizontal = 8.dp),
) {
    val sizes = LYRICS_TEXT_SIZES
    IconButton(
        onClick = { Preferences.LYRICS_TEXT_SIZE.update { (it - sizes.step).coerceAtLeast(sizes.first) } },
        enabled = size > sizes.first,
    ) {
        Icon(
            painterResource(R.drawable.remove),
            contentDescription = stringResource(R.string.semantic_smaller)
        )
    }
    TextButton(
        onClick = { Preferences.LYRICS_TEXT_SIZE.reset() },
        modifier = Modifier.widthIn(min = 72.dp),
    ) {
        Text("$size%")
    }
    IconButton(
        onClick = { Preferences.LYRICS_TEXT_SIZE.update { (it + sizes.step).coerceAtMost(sizes.last) } },
        enabled = size < sizes.last,
    ) {
        Icon(
            painterResource(R.drawable.add),
            contentDescription = stringResource(R.string.semantic_larger)
        )
    }
}

/**
 * Asks for the words to search the lyrics with, the song's own to start with. A title is needed,
 * the artist may be left out.
 */
@Composable
private fun LyricsSearchDialog(
    title: String,
    artist: String,
    onSearch: (title: String, artist: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var typedTitle by rememberSaveable { mutableStateOf(title) }
    var typedArtist by rememberSaveable { mutableStateOf(artist) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val search = { if (typedTitle.isNotBlank()) onSearch(typedTitle, typedArtist) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_search_lyrics)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = typedTitle,
                    onValueChange = { typedTitle = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.focusRequester(focus),
                )
                OutlinedTextField(
                    value = typedArtist,
                    onValueChange = { typedArtist = it },
                    label = {
                        val artistName = pluralStringResource(R.plurals.unit_artist, 1)
                        Text(artistName.replaceFirstChar { it.uppercase() })
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = search,
                enabled = typedTitle.isNotBlank()
            ) { Text(stringResource(R.string.action_search)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
