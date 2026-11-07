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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.musicbrainz.MusicBrainzAlbum
import me.knighthat.kreate3.musicbrainz.MusicBrainzLink
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.component.rememberOpenLink
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import me.knighthat.kreate3.ui.screen.detail.itemName

/**
 * What MusicBrainz knows about an album: who made it, what kind of release it is, when it first
 * came out, genres and its pages elsewhere.
 *
 * @param contentPadding room the list adds after its rows, e.g. so the last one can scroll clear
 * of what floats over the screen
 */
@Composable
fun AlbumDetailsScreen(
    onBack: () -> Unit,
    viewModel: AlbumDetailsViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openLink = rememberOpenLink()

    AlbumDetailsContent(
        title = viewModel.title,
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onOpenLink = openLink,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun AlbumDetailsContent(
    title: String,
    state: AlbumDetailsState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) = DetailScaffold(
    title = title.ifBlank { stringResource(R.string.screen_title_album_details) },
    onBack = onBack,
    modifier = modifier,
    contentPadding = contentPadding,
) { padding ->
    when (state) {
        AlbumDetailsState.Loading -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { CircularProgressIndicator() }

        AlbumDetailsState.NotFound -> Notice(padding) {
            val album = LocalResources.current.itemName(
                title.ifBlank { null },
                R.string.snackbar_this_album
            )
            Message(
                stringResource(
                    R.string.screen_album_details_status_not_found,
                    stringResource(R.string.source_musicbrainz),
                    album
                )
            )
        }

        is AlbumDetailsState.Failed -> Notice(padding) {
            Message(state.message)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }

        is AlbumDetailsState.Loaded -> LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { AlbumHeader(state.album) }
            item(key = "facts") { AlbumFacts(state.album, onOpenLink) }
            if (state.album.links.isNotEmpty()) {
                item(key = "linksTitle") {
                    SectionTitle(stringResource(R.string.section_header_links))
                }
                items(state.album.links, key = { "link:${it.url}" }) {
                    LinkRow(it, onClick = { onOpenLink(it.url) })
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(album: MusicBrainzAlbum) = Column(
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
) {
    Text(album.title, style = MaterialTheme.typography.titleLarge)
    album.artists?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    album.disambiguation?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A label and its value on each line; the id opens the album's page. */
@Composable
private fun AlbumFacts(album: MusicBrainzAlbum, onOpenLink: (String) -> Unit) = Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
) {
    val kind = listOfNotNull(album.type).plus(album.secondaryTypes).joinToString(" · ")

    if (kind.isNotEmpty()) Fact(stringResource(R.string.details_type), kind)
    album.firstReleased?.let { Fact(stringResource(R.string.details_first_released), it) }
    if (album.genres.isNotEmpty())
        Fact(stringResource(R.string.details_genres), album.genres.joinToString(", "))
    Fact(
        label = stringResource(
            R.string.details_musicbrainz_id,
            stringResource(R.string.source_musicbrainz)
        ),
        value = album.id,
        onClick = { onOpenLink(album.url) },
    )
}

/** A page about the album: the site it is on, then what it is. */
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

@ScreenPreviews
@Composable
private fun AlbumDetailsPreview() = PreviewTheme {
    AlbumDetailsContent(
        title = "Afterglow",
        state = AlbumDetailsState.Loaded(
            MusicBrainzAlbum(
                id = "0383dadf-2a4e-4d10-a46a-e9e041da8eb4",
                title = "Afterglow",
                artists = "Neon Coast",
                type = "Album",
                secondaryTypes = listOf("Live"),
                firstReleased = "2021-06-18",
                disambiguation = "deluxe edition",
                genres = listOf("synth-pop", "new wave"),
                links = listOf(
                    MusicBrainzLink("official homepage", "https://neoncoast.example"),
                    MusicBrainzLink("wikidata", "https://www.wikidata.org/wiki/Q2"),
                ),
            )
        ),
        onBack = {},
        onRetry = {},
        onOpenLink = {},
    )
}
