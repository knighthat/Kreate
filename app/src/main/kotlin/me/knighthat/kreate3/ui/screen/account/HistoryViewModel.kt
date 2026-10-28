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
import android.text.format.DateUtils
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.flatMap
import androidx.paging.insertSeparators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.model.PeriodPlay
import me.knighthat.kreate3.database.model.PeriodSummary
import me.knighthat.kreate3.database.model.PlaybackHistory
import me.knighthat.kreate3.database.repository.HistoryRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.notification.undoMessage
import me.knighthat.kreate3.ui.component.toMediaItem
import java.util.TimeZone

private const val TAG = "HistoryViewModel"

/**
 * The songs the active user played, in groups by how long ago, see [HistoryBuckets]. Disliked
 * songs are left out. Nothing is kept: the page opens on every day and origin, newest first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val context: Context,
    private val history: HistoryRepository,
    songRepository: UserSongRepository,
    private val scope: CoroutineScope,
) : ViewModel() {

    private val _range = MutableStateFlow<HistoryRange?>(null)
    private val _newestFirst = MutableStateFlow(true)
    private val _origins = MutableStateFlow(emptySet<Origin>())
    private var queries = 0

    /** The groups the page can show, the most recent first, `null` until they're worked out. */
    private val spans: StateFlow<List<HistorySpan>?> = _range
        .map { range ->
            val zone = TimeZone.getDefault()
            HistoryBuckets(System.currentTimeMillis(), zone).spans()
                .mapNotNull { it.clip(range, zone) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The days shown, `null` for every day. */
    val range: StateFlow<HistoryRange?> = _range.asStateFlow()

    /** Whether the most recent group and song come first. */
    val newestFirst: StateFlow<Boolean> = _newestFirst.asStateFlow()

    /** The origins shown, every origin while it is empty. */
    val origins: StateFlow<Set<Origin>> = _origins.asStateFlow()

    /**
     * The origins to offer a chip for: the library's and the selected ones, so they can be turned
     * off. Offered even when there is one, the sheet always shows its source.
     */
    val choices: StateFlow<List<Origin>> =
        combine(songRepository.origins(), _origins) { stored, chosen ->
            val offered = stored + chosen
            Origin.entries.filter { it in offered }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Each group's header followed by its plays, a page at a time. With a range picked the groups
     * have no headers, their plays follow each other.
     */
    val entries: Flow<PagingData<HistoryEntry>> =
        combine(spans.filterNotNull(), _origins, _newestFirst) { spans, origins, newestFirst ->
            Triple(spans, origins, newestFirst)
        }.flatMapLatest { (spans, origins, newestFirst) ->
            val query = ++queries
            // The spans follow the range, it's the one they were clipped to
            val headers = _range.value == null
            history.plays(spans.map(HistorySpan::period), origins, newestFirst).map { page ->
                val rows = page.flatMap<PeriodPlay, HistoryEntry> {
                    listOfNotNull(it.toRow(query, withDate = !headers))
                }
                if (!headers) rows
                else rows.insertSeparators<HistoryEntry, HistoryEntry> { before, after ->
                    after?.takeIf { before?.period != it.period }
                        ?.let { HistoryHeader(it.period, spans[it.period].bucket, query) }
                }
            }
        }.cachedIn(viewModelScope)

    /** How much was played in each group, by its index. */
    val summaries: StateFlow<Map<Int, PeriodSummary>> =
        combine(spans.filterNotNull(), _origins) { spans, origins -> spans to origins }
            .flatMapLatest { (spans, origins) ->
                history.periodSummaries(spans.map(HistorySpan::period), origins)
            }
            .map { list -> list.associateBy(PeriodSummary::period) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Whether a range or an origin leaves plays out. */
    private val isFiltered: Boolean
        get() = _range.value != null || _origins.value.isNotEmpty()

    fun pickRange(range: HistoryRange?) {
        _range.value = range
    }

    fun toggleOrder() {
        _newestFirst.value = !_newestFirst.value
    }

    /** Shows the songs of [origin] when they're hidden, hides them otherwise. */
    fun toggleOrigin(origin: Origin) {
        _origins.value = _origins.value.let { if (origin in it) it - origin else it + origin }
    }

    /**
     * The songs of the group [period] in its order, of every group when `null`, once each.
     */
    suspend fun songs(period: Int? = null): List<MediaItem> =
        plays(period).map { it.second }.distinctBy(MediaItem::mediaId)

    /** The songs of the plays of [row]'s group from [row] on, in its order, a song again for every play. */
    suspend fun songsFrom(row: HistoryRow): List<MediaItem> =
        plays(row.period).dropWhile { it.first != row.playId }.map { it.second }

    /**
     * Takes the play of [row] out of the history, then [onDone] gets a message whose Undo puts it
     * back.
     */
    fun delete(row: HistoryRow, onDone: (Message) -> Unit) {
        scope.launch {
            val removed = history.deletePlay(row.playId) ?: return@launch
            onDone(undoable(R.string.snackbar_removed_from_history, listOf(removed)))
        }
    }

    /** The plays of the group [period] in its order by id, of every group when `null`. */
    private suspend fun plays(period: Int?): List<Pair<Long, MediaItem>> {
        val spans = spans.filterNotNull().first()
        val periods = if (period == null) spans else listOfNotNull(spans.getOrNull(period))
        // A long history takes long to turn into items, never on the main thread
        return withContext(Dispatchers.Default) {
            history.listPlays(periods.map(HistorySpan::period), _origins.value, _newestFirst.value)
                .mapNotNull { play -> play.song.toMediaItem()?.let { play.id to it } }
        }
    }

    /**
     * Takes the plays shown out of the history, every play while nothing is filtered out, then
     * [onDone] gets a message whose Undo puts them back.
     */
    fun clear(onDone: (Message) -> Unit) {
        val periods = spans.value.orEmpty().map(HistorySpan::period)
        val origins = _origins.value
        val filtered = isFiltered
        scope.launch {
            val removed =
                if (filtered) history.deletePlays(periods, origins) else history.clearPlays()
            if (removed.isNotEmpty())
                onDone(undoable(R.string.snackbar_history_cleared, removed))
        }
    }

    /** [text] with an Undo that puts back [plays], on the app's scope. */
    private fun undoable(@StringRes text: Int, plays: List<PlaybackHistory>): Message =
        undoMessage(context.resources, context.getString(text)) {
            scope.launch {
                try {
                    history.restorePlays(plays)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't put back ${plays.size} plays", e)
                }
            }
        }

    /** @param withDate the play's date too, for a row with no header above it saying the day */
    private fun PeriodPlay.toRow(query: Int, withDate: Boolean): HistoryRow? =
        HistoryRow(
            period = period,
            playId = id,
            item = song.toMediaItem() ?: return null,
            time = formatTime(playedAt.toEpochMilliseconds(), withDate),
            query = query,
        )

    /**
     * The time of a play, and its day with [withDate]: the group's header says how long ago, the
     * year shows only for a day of another year.
     */
    private fun formatTime(time: Long, withDate: Boolean): String =
        DateUtils.formatDateTime(
            context,
            time,
            if (withDate)
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME
            else DateUtils.FORMAT_SHOW_TIME,
        )
}
