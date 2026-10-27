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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import me.knighthat.kreate3.database.model.ListeningSummary
import me.knighthat.kreate3.database.model.OriginTime
import me.knighthat.kreate3.database.model.PlayPeriod
import me.knighthat.kreate3.database.repository.StatisticsRepository
import me.knighthat.kreate3.source.ArtistPictures
import me.knighthat.kreate3.ui.component.toMediaItem
import java.util.Calendar
import java.util.TimeZone
import kotlin.time.Duration
import kotlin.time.Instant

/** The plays the Statistics page adds up. */
enum class StatisticsPeriod { ALL_TIME, THIS_MONTH }

/**
 * A song, an album or an artist with the times it was listened through, see `TopSong.listens`,
 * and the time spent on it.
 */
data class RankedItem(val item: MediaItem, val listens: Double, val timeSpent: Duration)

/**
 * What the Statistics page shows for [period].
 *
 * @property previous the summary of last month up to the same day and time, for [StatisticsPeriod.THIS_MONTH] only
 */
data class StatisticsState(
    val period: StatisticsPeriod,
    val summary: ListeningSummary,
    val previous: ListeningSummary?,
    val songs: List<RankedItem>,
    val albums: List<RankedItem>,
    val artists: List<RankedItem>,
    val origins: List<OriginTime>,
)

/**
 * How the active user listens: what they played the most, how long and from where, of all time
 * or this month. Disliked songs are left out. Nothing is kept, the page opens on all time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    statistics: StatisticsRepository,
    pictures: ArtistPictures,
) : ViewModel() {

    private val _period = MutableStateFlow(StatisticsPeriod.ALL_TIME)

    val period: StateFlow<StatisticsPeriod> = _period.asStateFlow()

    /** `null` until the first answer of the period. */
    val state: StateFlow<StatisticsState?> = _period
        .flatMapLatest { period ->
            val spans = when (period) {
                StatisticsPeriod.ALL_TIME -> null
                StatisticsPeriod.THIS_MONTH ->
                    monthToDate(System.currentTimeMillis(), TimeZone.getDefault())
            }
            val totals = combine(
                statistics.summary(spans?.first),
                spans?.let { statistics.summary(it.second) } ?: flowOf(null),
                statistics.timeByOrigin(spans?.first),
                ::Triple,
            )
            val tops = combine(
                statistics.topSongs(spans?.first, TOP_SONGS),
                statistics.topAlbums(spans?.first, TOP_ALBUMS),
                // Artists stored from song credits have no picture, their source has it
                statistics.topArtists(spans?.first, TOP_ARTISTS)
                    .onEach { top -> pictures.fillMissing(top.map { it.artist }) },
                ::Triple,
            )
            combine(totals, tops) { (summary, previous, origins), (songs, albums, artists) ->
                StatisticsState(
                    period = period,
                    summary = summary,
                    previous = previous,
                    songs = songs.mapNotNull { top ->
                        top.song.toMediaItem()?.let { RankedItem(it, top.listens, top.timeSpent) }
                    },
                    albums = albums.map {
                        RankedItem(
                            it.album.toMediaItem(),
                            it.listens,
                            it.timeSpent
                        )
                    },
                    artists = artists.map {
                        RankedItem(
                            it.artist.toMediaItem(),
                            it.listens,
                            it.timeSpent
                        )
                    },
                    origins = origins,
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun pickPeriod(period: StatisticsPeriod) {
        _period.value = period
    }

    private companion object {

        const val TOP_SONGS = 5
        const val TOP_ALBUMS = 3
        const val TOP_ARTISTS = 10
    }
}

/**
 * This month in [zone] (until the next one starts, so plays made meanwhile count) and last month
 * from its first day up to as far into it as [now] is into this one, at most all of it.
 */
internal fun monthToDate(now: Long, zone: TimeZone): Pair<PlayPeriod, PlayPeriod> {
    val calendar = Calendar.getInstance(zone).apply {
        timeInMillis = now
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val thisMonth = calendar.timeInMillis
    calendar.add(Calendar.MONTH, 1)
    val nextMonth = calendar.timeInMillis
    calendar.add(Calendar.MONTH, -2)
    val lastMonth = calendar.timeInMillis

    val lastMonthUntil = (lastMonth + (now - thisMonth)).coerceAtMost(thisMonth)
    return PlayPeriod(
        Instant.fromEpochMilliseconds(thisMonth),
        Instant.fromEpochMilliseconds(nextMonth)
    ) to
            PlayPeriod(
                Instant.fromEpochMilliseconds(lastMonth),
                Instant.fromEpochMilliseconds(lastMonthUntil)
            )
}
