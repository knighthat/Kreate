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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import me.knighthat.kreate3.database.model.PlaybackHistory
import me.knighthat.kreate3.database.model.UserSongView
import me.knighthat.kreate3.database.repository.StatisticsRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.navigation.SongStatistics
import java.util.TimeZone
import kotlin.time.Duration

/**
 * How the active user listens to a song, all of its plays.
 *
 * @property item `null` when the song is no longer in the library or can't be shown
 * @property listens the times the song was listened through, see `TopSong.listens`
 * @property firstPlayed epoch millis of the first play, `null` while it was never played
 */
data class SongStatisticsState(
    val item: MediaItem?,
    val listens: Int,
    val plays: Int,
    val timeSpent: Duration,
    val firstPlayed: Long?,
    val lastPlayed: Long?,
    val charts: ListeningCharts,
)

/** The statistics of the song of the [SongStatistics] route. */
class SongStatisticsViewModel(
    savedStateHandle: SavedStateHandle,
    private val context: Context,
    songs: UserSongRepository,
    statistics: StatisticsRepository,
) : ViewModel() {

    private val songId: String = checkNotNull(savedStateHandle[SongStatistics.ID]) {
        "SongStatistics.ID is missing from the saved state"
    }

    /** `null` until the song and its plays are read. */
    val state: StateFlow<SongStatisticsState?> =
        combine(songs.find(songId), statistics.songPlays(songId)) { song, plays ->
            build(song, plays, System.currentTimeMillis(), TimeZone.getDefault())
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** @param plays the oldest first */
    private fun build(
        song: UserSongView?,
        plays: List<PlaybackHistory>,
        now: Long,
        zone: TimeZone,
    ): SongStatisticsState {
        val timeSpent = plays.fold(Duration.ZERO) { total, play -> total + play.timeSpent }
        val length = song?.duration?.takeIf { it.isPositive() }
        return SongStatisticsState(
            item = song?.toMediaItem(),
            listens = if (length == null) plays.size else (timeSpent / length).toInt(),
            plays = plays.size,
            timeSpent = timeSpent,
            firstPlayed = plays.firstOrNull()?.createdAt?.toEpochMilliseconds(),
            lastPlayed = plays.lastOrNull()?.createdAt?.toEpochMilliseconds(),
            charts = context.listeningCharts(plays, now, zone),
        )
    }
}
