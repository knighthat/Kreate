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

package me.knighthat.kreate3.ui.screen.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.musicbrainz.MusicBrainzMatch
import me.knighthat.kreate3.musicbrainz.MusicBrainzRecording
import me.knighthat.kreate3.musicbrainz.MusicBrainzRelease
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SearchButton
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.component.formatDuration
import me.knighthat.kreate3.ui.component.rememberOpenLink
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import kotlin.time.Duration.Companion.milliseconds

/**
 * What MusicBrainz knows about a song: the recording it was identified as, its credits, length,
 * codes, genres and releases. The search button of the top bar finds another recording to show
 * and keep, when the one found isn't the song.
 *
 * @param contentPadding room the list adds after its rows, e.g. so the last one can scroll clear
 * of what floats over the screen
 */
@Composable
fun SongDetailsScreen(
    onBack: () -> Unit,
    viewModel: SongDetailsViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val openLink = rememberOpenLink()

    SongDetailsContent(
        title = viewModel.title,
        state = state,
        onBack = onBack,
        onSearch = viewModel::openSearch,
        onRetry = viewModel::load,
        onOpenLink = openLink,
        modifier = modifier,
        contentPadding = contentPadding,
    )

    search?.let {
        RecordingSearchDialog(
            search = it,
            current = (state as? SongDetailsState.Loaded)?.recording?.id,
            onSearch = viewModel::searchRecordings,
            onPick = viewModel::pick,
            onDismiss = viewModel::closeSearch,
        )
    }
}

@Composable
private fun SongDetailsContent(
    title: String,
    state: SongDetailsState,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) = DetailScaffold(
    title = title.ifBlank { stringResource(R.string.screen_title_song_details) },
    onBack = onBack,
    modifier = modifier,
    contentPadding = contentPadding,
    actions = { SearchButton(onSearch) },
) { padding ->
    when (state) {
        SongDetailsState.Loading -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { CircularProgressIndicator() }

        SongDetailsState.NotFound -> Notice(padding) {
            val musicBrainz = stringResource(R.string.source_musicbrainz)
            Message(stringResource(R.string.screen_song_details_status_not_found, musicBrainz))
            SearchHint()
        }

        is SongDetailsState.Failed -> Notice(padding) {
            Message(state.message)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }

        is SongDetailsState.Loaded -> LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { RecordingHeader(state.recording) }
            item(key = "facts") { RecordingFacts(state.recording, onOpenLink) }
            if (!state.kept)
                item(key = "notKept") {
                    Text(
                        stringResource(R.string.screen_song_details_hint_not_kept),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            if (state.recording.releases.isNotEmpty()) {
                item(key = "releasesTitle") {
                    SectionTitle(stringResource(R.string.section_header_releases))
                }
                items(state.recording.releases, key = { "release:${it.id}" }) {
                    ReleaseRow(it, onClick = { onOpenLink(it.url) })
                }
            }
        }
    }
}

/** A message in the middle of the page, with what can be done about it under. */
@Composable
private fun Notice(padding: PaddingValues, content: @Composable () -> Unit) = Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 32.dp),
) { content() }

@Composable
private fun Message(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

/** Says to search with other words, with the icon of the search button in it. */
@Composable
private fun SearchHint() {
    val hint = stringResource(R.string.screen_song_details_hint_search, SEARCH_MARK)
    Text(
        text = buildAnnotatedString {
            // The icon takes the place of the mark, wherever the translation puts it
            val start = hint.indexOf(SEARCH_MARK)
            if (start < 0) append(hint)
            else {
                append(hint.substring(0, start))
                appendInlineContent(SEARCH_ICON, SEARCH_MARK)
                append(hint.substring(start + SEARCH_MARK.length))
            }
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        inlineContent = mapOf(
            SEARCH_ICON to InlineTextContent(
                Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)
            ) {
                // Described by the text around it
                Icon(painterResource(R.drawable.search), contentDescription = null)
            },
        ),
    )
}

@Composable
private fun RecordingHeader(recording: MusicBrainzRecording) = Column(
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
) {
    Text(recording.title, style = MaterialTheme.typography.titleLarge)
    recording.artists?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    recording.disambiguation?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A label and its value on each line; the id opens the recording's page. */
@Composable
private fun RecordingFacts(recording: MusicBrainzRecording, onOpenLink: (String) -> Unit) = Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
) {
    formatDuration(recording.length?.inWholeMilliseconds)?.let {
        Fact(stringResource(R.string.details_length), it)
    }
    recording.firstReleased?.let { Fact(stringResource(R.string.details_first_released), it) }
    if (recording.isrcs.isNotEmpty())
        Fact(stringResource(R.string.details_isrc), recording.isrcs.joinToString("\n"))
    if (recording.genres.isNotEmpty())
        Fact(stringResource(R.string.details_genres), recording.genres.joinToString(", "))
    Fact(
        label = stringResource(
            R.string.details_musicbrainz_id,
            stringResource(R.string.source_musicbrainz)
        ),
        value = recording.id,
        onClick = { onOpenLink(recording.url) },
    )
}

@Composable
private fun Fact(label: String, value: String, onClick: (() -> Unit)? = null) = Row {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(LABEL_WIDTH),
    )
    Text(
        value,
        style = MaterialTheme.typography.bodyMedium,
        color = if (onClick == null) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .weight(1f)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
    )
}

/** A release of the recording: its title, then when, where and what kind. */
@Composable
private fun ReleaseRow(release: MusicBrainzRelease, onClick: () -> Unit) = Column(
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 6.dp),
) {
    Text(
        release.title,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    val about = listOfNotNull(release.date, release.country, release.type, release.status)
    if (about.isNotEmpty())
        Text(
            about.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
}

/**
 * Asks for the words to search the recording with, the song's own to start with, and lists what
 * MusicBrainz found; a tap shows that recording. [current] is marked.
 */
@Composable
private fun RecordingSearchDialog(
    search: RecordingSearch,
    current: String?,
    onSearch: (title: String, artist: String) -> Unit,
    onPick: (MusicBrainzMatch) -> Unit,
    onDismiss: () -> Unit,
) {
    var typedTitle by rememberSaveable { mutableStateOf(search.title) }
    var typedArtist by rememberSaveable { mutableStateOf(search.artist) }
    val start = { if (typedTitle.isNotBlank()) onSearch(typedTitle, typedArtist) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_find_recording)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = typedTitle,
                    onValueChange = { typedTitle = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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
                    keyboardActions = KeyboardActions(onSearch = { start() }),
                )
                MatchList(search, current, onPick)
            }
        },
        confirmButton = {
            TextButton(
                onClick = start,
                enabled = typedTitle.isNotBlank(),
            ) { Text(stringResource(R.string.action_search)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun MatchList(
    search: RecordingSearch,
    current: String?,
    onPick: (MusicBrainzMatch) -> Unit,
) {
    val matches = search.matches
    when {
        matches == null -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) { CircularProgressIndicator() }

        search.error != null -> Text(search.error, color = MaterialTheme.colorScheme.error)

        matches.isEmpty() -> Text(
            stringResource(R.string.dialog_body_find_recording_no_matches),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> LazyColumn(modifier = Modifier.heightIn(max = MATCH_LIST_HEIGHT)) {
            items(matches, key = { it.id }) { match ->
                MatchRow(match, isCurrent = match.id == current, onClick = { onPick(match) })
            }
        }
    }
}

/** A recording found, marked when it is the one shown. */
@Composable
private fun MatchRow(match: MusicBrainzMatch, isCurrent: Boolean, onClick: () -> Unit) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = 6.dp),
) {
    if (isCurrent)
        Icon(
            painterResource(R.drawable.check),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    else Spacer(Modifier.size(24.dp))
    Column(modifier = Modifier.weight(1f)) {
        Text(
            match.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val about = listOfNotNull(
            match.artists,
            formatDuration(match.length?.inWholeMilliseconds),
            match.firstReleased,
            match.disambiguation,
        )
        Text(
            about.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The page of a release on the MusicBrainz site. */
private val MusicBrainzRelease.url: String get() = "https://musicbrainz.org/release/$id"

private val LABEL_WIDTH = 120.dp
private val MATCH_LIST_HEIGHT = 320.dp

/** Where the search hint shows the icon of the search button. */
private const val SEARCH_ICON = "search"
private const val SEARCH_MARK = "⌕"

@ScreenPreviews
@Composable
private fun SongDetailsPreview() = PreviewTheme {
    SongDetailsContent(
        title = "Bohemian Rhapsody",
        state = SongDetailsState.Loaded(
            recording = MusicBrainzRecording(
                id = "b1a9c0e9-d987-4042-ae91-78d6a3267d69",
                title = "Bohemian Rhapsody",
                artists = "Queen",
                length = 355_000.milliseconds,
                firstReleased = "1975-10-31",
                disambiguation = null,
                isrcs = listOf("GBUM71029604", "GBCEE7500015"),
                genres = listOf("progressive rock", "hard rock", "opera"),
                releases = listOf(
                    MusicBrainzRelease(
                        "1",
                        "Bohemian Rhapsody",
                        "1975-10-31",
                        "GB",
                        "Single",
                        "Official"
                    ),
                    MusicBrainzRelease(
                        "2",
                        "A Night at the Opera",
                        "1975-11-21",
                        "GB",
                        "Album",
                        "Official"
                    ),
                    MusicBrainzRelease(
                        "3",
                        "Greatest Hits",
                        "1981-10-26",
                        "GB",
                        "Album",
                        "Official"
                    ),
                ),
            ),
            kept = true,
        ),
        onBack = {},
        onSearch = {},
        onRetry = {},
        onOpenLink = {},
    )
}
