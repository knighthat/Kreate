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

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import me.knighthat.kreate3.database.model.PlaybackHistory
import me.knighthat.kreate3.database.model.TopSong
import me.knighthat.kreate3.database.repository.StatisticsRepository
import me.knighthat.kreate3.source.ArtistPictures
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.navigation.AlbumStatistics
import me.knighthat.kreate3.ui.navigation.ArtistStatistics
import java.util.TimeZone
import kotlin.time.Duration

/** What a [GroupStatisticsViewModel] is about. */
enum class StatisticsGroup { ALBUM, ARTIST }

/**
 * How the active user listens to the songs of an album or an artist, all of their plays.
 *
 * @property item the album or the artist, `null` while none of its songs was played
 * @property listens the times its songs were listened through added up, see `TopSong.listens`
 * @property songsPlayed the different songs of it played
 * @property firstPlayed epoch millis of the first play of one of its songs
 * @property songs its songs listened to the most, the most first
 */
data class GroupStatisticsState(
    val item: MediaItem?,
    val listens: Int,
    val plays: Int,
    val timeSpent: Duration,
    val songsPlayed: Int,
    val firstPlayed: Long?,
    val lastPlayed: Long?,
    val charts: ListeningCharts,
    val songs: List<RankedItem>,
)

/**
 * The statistics of the album of the [AlbumStatistics] route, or of the artist of the
 * [ArtistStatistics] one, whichever opened it.
 */
class GroupStatisticsViewModel(
    savedStateHandle: SavedStateHandle,
    private val context: Context,
    statistics: StatisticsRepository,
    pictures: ArtistPictures,
) : ViewModel() {

    val group: StatisticsGroup =
        if (savedStateHandle.contains(AlbumStatistics.ID)) StatisticsGroup.ALBUM
        else StatisticsGroup.ARTIST

    private val id: String = checkNotNull(
        savedStateHandle[if (group == StatisticsGroup.ALBUM) AlbumStatistics.ID else ArtistStatistics.ID]
    ) { "The album or artist id is missing from the saved state" }

    /** `null` until the album or the artist and its plays are read. */
    val state: StateFlow<GroupStatisticsState?> = run {
        // The item as a media item with the listens of all its songs
        val header: Flow<Pair<MediaItem, Double>?>
        val plays: Flow<List<PlaybackHistory>>
        val songs: Flow<List<TopSong>>
        when (group) {
            StatisticsGroup.ALBUM -> {
                header = statistics.album(id)
                    .map { it?.let { top -> top.album.toMediaItem() to top.listens } }
                plays = statistics.albumPlays(id)
                songs = statistics.albumSongs(id, TOP_SONGS)
            }

            StatisticsGroup.ARTIST -> {
                header = statistics.artist(id)
                    // Artists stored from song credits have no picture, their source has it
                    .onEach { top -> top?.let { pictures.fillMissing(listOf(it.artist)) } }
                    .map { it?.let { top -> top.artist.toMediaItem() to top.listens } }
                plays = statistics.artistPlays(id)
                songs = statistics.artistSongs(id, TOP_SONGS)
            }
        }
        combine(header, plays, songs) { header, plays, songs ->
            build(header, plays, songs, System.currentTimeMillis(), TimeZone.getDefault())
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    /** @param plays the oldest first */
    private fun build(
        header: Pair<MediaItem, Double>?,
        plays: List<PlaybackHistory>,
        songs: List<TopSong>,
        now: Long,
        zone: TimeZone,
    ) = GroupStatisticsState(
        item = header?.first,
        listens = header?.second?.toInt() ?: 0,
        plays = plays.size,
        timeSpent = plays.fold(Duration.ZERO) { total, play -> total + play.timeSpent },
        songsPlayed = plays.distinctBy { it.songId }.size,
        firstPlayed = plays.firstOrNull()?.createdAt?.toEpochMilliseconds(),
        lastPlayed = plays.lastOrNull()?.createdAt?.toEpochMilliseconds(),
        charts = context.listeningCharts(plays, now, zone),
        songs = songs.mapNotNull { top ->
            top.song.toMediaItem()?.let { RankedItem(it, top.listens, top.timeSpent) }
        },
    )

    private companion object {

        const val TOP_SONGS = 10
    }
}
