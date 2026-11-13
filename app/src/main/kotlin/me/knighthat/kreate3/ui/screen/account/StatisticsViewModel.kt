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

import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.flow.update
import me.knighthat.kreate3.database.model.ListeningSummary
import me.knighthat.kreate3.database.model.OriginTime
import me.knighthat.kreate3.database.model.PlayPeriod
import me.knighthat.kreate3.database.repository.StatisticsRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.source.ArtistPictures
import me.knighthat.kreate3.ui.component.toMediaItem
import java.util.Calendar
import java.util.TimeZone
import kotlin.time.Duration
import kotlin.time.Instant

/** Ready-made ranges of the Statistics page's date picker. */
enum class StatisticsPeriod { ALL_TIME, THIS_MONTH, LAST_3_MONTHS, LAST_6_MONTHS, THIS_YEAR }

/**
 * The plays the Statistics page adds up: those of [range] (every day for `null`) of songs from
 * [origins] (every origin while it's empty).
 */
@Immutable
data class StatisticsFilter(
    val range: HistoryRange? = null,
    val origins: Set<Origin> = emptySet(),
)

/**
 * A song, an album or an artist with the times it was listened through, see `TopSong.listens`,
 * and the time spent on it.
 */
data class RankedItem(val item: MediaItem, val listens: Double, val timeSpent: Duration)

/**
 * What the Statistics page shows for [filter].
 *
 * @property period the preset that picks the same days as the filter, `null` for days picked on the calendar
 * @property previous the summary of last month up to the same day and time, for [StatisticsPeriod.THIS_MONTH] only
 */
data class StatisticsState(
    val filter: StatisticsFilter,
    val period: StatisticsPeriod?,
    val summary: ListeningSummary,
    val previous: ListeningSummary?,
    val songs: List<RankedItem>,
    val albums: List<RankedItem>,
    val artists: List<RankedItem>,
    val origins: List<OriginTime>,
)

/**
 * How the active user listens: what they played the most, how long and from where, in the days
 * and from the origins picked. Disliked songs are left out. Nothing is kept, the page opens on all
 * time and every origin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    statistics: StatisticsRepository,
    songRepository: UserSongRepository,
    pictures: ArtistPictures,
) : ViewModel() {

    private val _filter = MutableStateFlow(StatisticsFilter())

    val filter: StateFlow<StatisticsFilter> = _filter.asStateFlow()

    /**
     * The origins to offer a chip for: the library's and the selected ones, so they can be turned
     * off. Offered even when there is one, the sheet always shows its source.
     */
    val choices: StateFlow<List<Origin>> =
        combine(songRepository.origins(), _filter) { stored, filter ->
            val offered = stored + filter.origins
            Origin.entries.filter { it in offered }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** `null` until the first answer of the filter. */
    val state: StateFlow<StatisticsState?> = _filter
        .flatMapLatest { filter ->
            val now = System.currentTimeMillis()
            val zone = TimeZone.getDefault()
            val origins = filter.origins
            val preset =
                StatisticsPeriod.entries.firstOrNull { it.range(now, zone) == filter.range }
            val span = filter.range?.let {
                PlayPeriod(
                    Instant.fromEpochMilliseconds(it.start(zone)),
                    Instant.fromEpochMilliseconds(it.end(zone)),
                )
            }
            // Only this month has a month to compare with
            val lastMonth =
                if (preset == StatisticsPeriod.THIS_MONTH) monthToDate(now, zone).second else null
            val totals = combine(
                statistics.summary(span, origins),
                lastMonth?.let { statistics.summary(it, origins) } ?: flowOf(null),
                statistics.timeByOrigin(span, origins),
                ::Triple,
            )
            val tops = combine(
                statistics.topSongs(span, TOP_SONGS, origins),
                statistics.topAlbums(span, TOP_ALBUMS, origins),
                // Artists stored from song credits have no picture, their source has it
                statistics.topArtists(span, TOP_ARTISTS, origins)
                    .onEach { top -> pictures.fillMissing(top.map { it.artist }) },
                ::Triple,
            )
            combine(totals, tops) { (summary, previous, byOrigin), (songs, albums, artists) ->
                StatisticsState(
                    filter = filter,
                    period = preset,
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
                    origins = byOrigin,
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Adds up the plays of [range], of every day for `null`. */
    fun pickRange(range: HistoryRange?) {
        _filter.update { it.copy(range = range) }
    }

    /** Counts the songs of [origin] when they're left out, leaves them out otherwise. */
    fun toggleOrigin(origin: Origin) {
        _filter.update {
            it.copy(origins = if (origin in it.origins) it.origins - origin else it.origins + origin)
        }
    }

    private companion object {

        const val TOP_SONGS = 5
        const val TOP_ALBUMS = 3
        const val TOP_ARTISTS = 10
    }
}

/**
 * The days of the preset at [now] in [zone], `null` for every day. A number of months goes back
 * from today, which is in it, to the day after the same date that many months ago.
 */
fun StatisticsPeriod.range(now: Long, zone: TimeZone): HistoryRange? {
    val today = utcDay(now, zone)

    fun utc(change: Calendar.() -> Unit) =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = today
            change()
        }.timeInMillis

    fun monthsBack(months: Int) = HistoryRange(
        utc {
            add(Calendar.MONTH, -months)
            add(Calendar.DAY_OF_MONTH, 1)
        },
        today,
    )

    return when (this) {
        StatisticsPeriod.ALL_TIME -> null
        StatisticsPeriod.THIS_MONTH -> HistoryRange(utc { set(Calendar.DAY_OF_MONTH, 1) }, today)
        StatisticsPeriod.LAST_3_MONTHS -> monthsBack(3)
        StatisticsPeriod.LAST_6_MONTHS -> monthsBack(6)
        StatisticsPeriod.THIS_YEAR -> HistoryRange(utc { set(Calendar.DAY_OF_YEAR, 1) }, today)
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
