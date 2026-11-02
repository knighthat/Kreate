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

import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.musicbrainz.MusicBrainzArtist
import me.knighthat.kreate3.musicbrainz.MusicBrainzArtistMatch
import me.knighthat.kreate3.musicbrainz.MusicBrainzLink
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SearchButton
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.component.rememberOpenLink
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold

/**
 * What MusicBrainz knows about an artist: who they are, where from, when they began and ended,
 * other names, genres and their pages elsewhere. The search button of the top bar finds another
 * artist to show, when the one found isn't them.
 *
 * @param contentPadding room the list adds after its rows, e.g. so the last one can scroll clear
 * of what floats over the screen
 */
@Composable
fun ArtistDetailsScreen(
    onBack: () -> Unit,
    viewModel: ArtistDetailsViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val openLink = rememberOpenLink()

    ArtistDetailsContent(
        name = viewModel.name,
        state = state,
        onBack = onBack,
        onSearch = viewModel::openSearch,
        onRetry = viewModel::load,
        onOpenLink = openLink,
        modifier = modifier,
        contentPadding = contentPadding,
    )

    search?.let {
        ArtistSearchDialog(
            search = it,
            current = (state as? ArtistDetailsState.Loaded)?.artist?.id,
            onSearch = viewModel::searchArtists,
            onPick = viewModel::pick,
            onDismiss = viewModel::closeSearch,
        )
    }
}

@Composable
private fun ArtistDetailsContent(
    name: String,
    state: ArtistDetailsState,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) = DetailScaffold(
    title = name.ifBlank { stringResource(R.string.screen_title_artist_details) },
    onBack = onBack,
    modifier = modifier,
    contentPadding = contentPadding,
    actions = { SearchButton(onSearch) },
) { padding ->
    when (state) {
        ArtistDetailsState.Loading -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { CircularProgressIndicator() }

        ArtistDetailsState.NotFound -> Notice(padding) {
            val musicBrainz = stringResource(R.string.source_musicbrainz)
            Message(stringResource(R.string.screen_artist_details_status_not_found, musicBrainz))
            SearchHint(stringResource(R.string.screen_artist_details_hint_search, SEARCH_MARK))
        }

        is ArtistDetailsState.Failed -> Notice(padding) {
            Message(state.message)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }

        is ArtistDetailsState.Loaded -> LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { ArtistHeader(state.artist) }
            item(key = "facts") { ArtistFacts(state.artist, onOpenLink) }
            if (state.artist.links.isNotEmpty()) {
                item(key = "linksTitle") {
                    SectionTitle(stringResource(R.string.section_header_links))
                }
                items(state.artist.links, key = { "link:${it.url}" }) {
                    LinkRow(it, onClick = { onOpenLink(it.url) })
                }
            }
        }
    }
}

@Composable
private fun ArtistHeader(artist: MusicBrainzArtist) = Column(
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
) {
    Text(artist.name, style = MaterialTheme.typography.titleLarge)
    artist.disambiguation?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A label and its value on each line; the id opens the artist's page. When they began and ended
 * is named by what they are: born and died for a person, formed and dissolved for a group.
 */
@Composable
private fun ArtistFacts(artist: MusicBrainzArtist, onOpenLink: (String) -> Unit) = Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
) {
    val (beganLabel, endedLabel) = when (artist.type) {
        "Person" -> R.string.details_born to R.string.details_died
        "Group", "Orchestra", "Choir" -> R.string.details_formed to R.string.details_dissolved
        else -> R.string.details_began to R.string.details_ended
    }

    artist.type?.let { Fact(stringResource(R.string.details_type), it) }
    artist.area?.let { Fact(stringResource(R.string.details_area), it) }
    whenAndWhere(artist.began, artist.beganIn)?.let { Fact(stringResource(beganLabel), it) }
    whenAndWhere(artist.ended, artist.endedIn)?.let { Fact(stringResource(endedLabel), it) }
    if (artist.aliases.isNotEmpty())
        Fact(
            stringResource(R.string.details_aliases),
            artist.aliases.take(MAX_ALIASES).joinToString(", "),
        )
    if (artist.genres.isNotEmpty())
        Fact(stringResource(R.string.details_genres), artist.genres.joinToString(", "))
    if (artist.isnis.isNotEmpty())
        Fact(stringResource(R.string.details_isni), artist.isnis.joinToString("\n"))
    Fact(
        label = stringResource(
            R.string.details_musicbrainz_id,
            stringResource(R.string.source_musicbrainz)
        ),
        value = artist.id,
        onClick = { onOpenLink(artist.url) },
    )
}

/** `1946-09-05 · Stone Town`, either alone, `null` without both. */
private fun whenAndWhere(date: String?, place: String?): String? =
    listOfNotNull(date, place).joinToString(" · ").ifEmpty { null }

/** A page about the artist: the site it is on, then what it is. */
@Composable
private fun LinkRow(link: MusicBrainzLink, onClick: () -> Unit) = Column(
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 6.dp),
) {
    Text(
        Uri.parse(link.url).host?.removePrefix("www.") ?: link.url,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (link.type.isNotEmpty())
        Text(
            link.type,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
}

/**
 * Asks for the name to search the artist with, their own to start with, and lists what
 * MusicBrainz found; a tap shows that artist. [current] is marked.
 */
@Composable
private fun ArtistSearchDialog(
    search: ArtistSearch,
    current: String?,
    onSearch: (name: String) -> Unit,
    onPick: (MusicBrainzArtistMatch) -> Unit,
    onDismiss: () -> Unit,
) {
    var typedName by rememberSaveable { mutableStateOf(search.name) }
    val start = { if (typedName.isNotBlank()) onSearch(typedName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_find_artist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = typedName,
                    onValueChange = { typedName = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { start() }),
                )
                ArtistMatchList(search, current, onPick)
            }
        },
        confirmButton = {
            TextButton(
                onClick = start,
                enabled = typedName.isNotBlank(),
            ) { Text(stringResource(R.string.action_search)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ArtistMatchList(
    search: ArtistSearch,
    current: String?,
    onPick: (MusicBrainzArtistMatch) -> Unit,
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
            stringResource(R.string.dialog_body_find_artist_no_matches),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> LazyColumn(modifier = Modifier.heightIn(max = MATCH_LIST_HEIGHT)) {
            items(matches, key = { it.id }) { match ->
                ArtistMatchRow(match, isCurrent = match.id == current, onClick = { onPick(match) })
            }
        }
    }
}

/** An artist found, marked when they are the one shown. */
@Composable
private fun ArtistMatchRow(match: MusicBrainzArtistMatch, isCurrent: Boolean, onClick: () -> Unit) =
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        if (isCurrent) {
            Icon(
                painterResource(R.drawable.check),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Spacer(Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                match.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val lifeSpan = listOfNotNull(match.began, match.ended).joinToString(" – ")
            val about = listOfNotNull(
                match.type,
                match.area,
                lifeSpan.ifEmpty { null },
                match.disambiguation,
            )
            if (about.isNotEmpty())
                Text(
                    about.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
        }
    }

/** Other names shown at most, some artists have dozens in every script. */
private const val MAX_ALIASES = 8

@ScreenPreviews
@Composable
private fun ArtistDetailsPreview() = PreviewTheme {
    ArtistDetailsContent(
        name = "Neon Coast",
        state = ArtistDetailsState.Loaded(
            MusicBrainzArtist(
                id = "0383dadf-2a4e-4d10-a46a-e9e041da8eb3",
                name = "Neon Coast",
                type = "Group",
                area = "United Kingdom",
                disambiguation = "synth-pop band",
                began = "1981-04-02",
                beganIn = "Brighton",
                ended = null,
                endedIn = null,
                aliases = listOf("The Neon Coast", "ネオン・コースト"),
                genres = listOf("synth-pop", "new wave"),
                isnis = listOf("0000000000000001"),
                links = listOf(
                    MusicBrainzLink("official homepage", "https://neoncoast.example"),
                    MusicBrainzLink("wikidata", "https://www.wikidata.org/wiki/Q1"),
                ),
            )
        ),
        onBack = {},
        onSearch = {},
        onRetry = {},
        onOpenLink = {},
    )
}
