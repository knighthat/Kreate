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

package me.knighthat.kreate3.ui.screen.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.Thumbnail
import me.knighthat.kreate3.ui.component.previewAlbums
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import kotlin.time.Duration.Companion.minutes

/**
 * How the active user listens to the songs of an album or an artist, all of their plays: the
 * item, which opens its page, its numbers, the charts of [SongStatisticsScreen] and its songs
 * listened to the most, each opening its own statistics.
 *
 * @param onOpenSong opens the statistics of the song with this id
 * @param onOpenPage opens the page of the album or the artist
 * @param contentPadding room the list adds after its rows, e.g. so the last one can scroll clear
 * of what floats over the screen
 */
@Composable
fun GroupStatisticsScreen(
    onBack: () -> Unit,
    onOpenSong: (String) -> Unit,
    onOpenPage: (MediaItem) -> Unit,
    viewModel: GroupStatisticsViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    GroupStatisticsContent(
        group = viewModel.group,
        state = state,
        onBack = onBack,
        onOpenSong = onOpenSong,
        onOpenPage = onOpenPage,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

/** @param state `null` while loading */
@Composable
private fun GroupStatisticsContent(
    group: StatisticsGroup,
    state: GroupStatisticsState?,
    onBack: () -> Unit,
    onOpenSong: (String) -> Unit,
    onOpenPage: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) = DetailScaffold(
    title = state?.item?.mediaMetadata?.title?.toString()
        ?: stringResource(R.string.screen_title_statistics),
    onBack = onBack,
    modifier = modifier,
    contentPadding = contentPadding,
) { padding ->
    LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        if (state == null) return@LazyColumn

        val item = state.item
        if (item == null || state.plays == 0) {
            nothingPlayed()
            return@LazyColumn
        }

        item(key = "header") { GroupHeader(item, group, onClick = { onOpenPage(item) }) }
        item(key = "numbers") { GroupNumbers(group, state) }
        listeningCharts(state.charts)
        if (state.songs.isNotEmpty()) {
            item(key = "songs-title") {
                SectionTitle(stringResource(R.string.section_header_top_songs))
            }
            items(state.songs, key = { "song:${it.item.mediaId}" }) { song ->
                SongItem(
                    item = song.item,
                    onClick = { onOpenSong(song.item.mediaId) },
                    // Inside an album its cover and name are already on screen
                    showThumbnail = group != StatisticsGroup.ALBUM,
                    showAlbum = group != StatisticsGroup.ALBUM,
                    trailing = { MinutesText(song.timeSpent) },
                )
            }
        }
    }
}

/** The album or the artist, its picture and names; a tap opens its page. */
@Composable
private fun GroupHeader(item: MediaItem, group: StatisticsGroup, onClick: () -> Unit) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp),
) {
    Thumbnail(
        uri = item.mediaMetadata.artworkUri,
        shape = if (group == StatisticsGroup.ARTIST) CircleShape else MaterialTheme.shapes.medium,
        modifier = Modifier.size(HEADER_PICTURE),
    )
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1f)) {
        Text(
            item.mediaMetadata.title?.toString().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.mediaMetadata.subtitle?.let {
            Text(
                it.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    // Opens the page, the row says what it is
    Icon(painterResource(R.drawable.chevron_right), contentDescription = null)
}

/** The numbers of the album or the artist, a label and its value on each line. */
@Composable
private fun GroupNumbers(group: StatisticsGroup, state: GroupStatisticsState) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val album = group == StatisticsGroup.ALBUM
    NumberLines(
        listOf(
            stringResource(if (album) R.string.statistics_album_listens else R.string.statistics_artist_listens) to
                    "${state.listens}",
            stringResource(if (album) R.string.statistics_album_plays else R.string.statistics_artist_plays) to
                    "${state.plays}",
            stringResource(if (album) R.string.statistics_album_time_listened else R.string.statistics_artist_time_listened) to
                    resources.timeSpent(state.timeSpent),
            stringResource(if (album) R.string.statistics_album_songs_played else R.string.statistics_artist_songs_played) to
                    "${state.songsPlayed}",
            stringResource(if (album) R.string.statistics_album_first_discovered else R.string.statistics_artist_first_discovered) to
                    context.playDate(state.firstPlayed),
            stringResource(if (album) R.string.statistics_album_last_played else R.string.statistics_artist_last_played) to
                    context.playDate(state.lastPlayed),
        )
    )
}

private val HEADER_PICTURE = 72.dp

@ScreenPreviews
@Composable
private fun GroupStatisticsPreview() = PreviewTheme {
    GroupStatisticsContent(
        group = StatisticsGroup.ALBUM,
        state = GroupStatisticsState(
            item = previewAlbums().first(),
            listens = 64,
            plays = 80,
            timeSpent = 312.minutes,
            songsPlayed = 9,
            firstPlayed = 1_767_225_600_000,
            lastPlayed = 1_792_800_000_000,
            charts = previewListeningCharts(),
            songs = previewSongs().mapIndexed { i, it ->
                RankedItem(
                    it,
                    20.0 - i * 3,
                    (60 - i * 8).minutes
                )
            },
        ),
        onBack = {},
        onOpenSong = {},
        onOpenPage = {},
    )
}
