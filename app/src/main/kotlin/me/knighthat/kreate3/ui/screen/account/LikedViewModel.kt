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
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.flatMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.model.UserSongView
import me.knighthat.kreate3.database.paging.SortDirection
import me.knighthat.kreate3.database.paging.SortOrder
import me.knighthat.kreate3.database.paging.UserSongSort
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.notification.MessageDuration
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicLibrary
import me.knighthat.kreate3.provider.youtube.YouTubeLibrary
import me.knighthat.kreate3.ui.component.toMediaItem
import kotlin.time.Instant

/**
 * A liked song: its [item], and [time] saying how long ago it was liked.
 *
 * @property query the query the row came from, a new one for every order or filter: the same
 * song in another query is another row, the list doesn't follow it there
 */
@Immutable
class LikedRow(val item: MediaItem, val time: String, val query: Int)

/**
 * The songs the active user liked, most recently liked first. Nothing is kept: the page opens on
 * every origin, newest first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LikedViewModel(
    private val context: Context,
    private val songRepository: UserSongRepository,
    private val openSubsonicLibrary: OpenSubsonicLibrary,
    private val youTubeLibrary: YouTubeLibrary,
    private val scope: CoroutineScope,
) : ViewModel() {

    private val _newestFirst = MutableStateFlow(true)
    private val _origins = MutableStateFlow(emptySet<Origin>())
    private var queries = 0

    /** Whether the most recently liked song comes first. */
    val newestFirst: StateFlow<Boolean> = _newestFirst.asStateFlow()

    /** The origins shown, every origin while it is empty. */
    val origins: StateFlow<Set<Origin>> = _origins.asStateFlow()

    /**
     * The origins to offer a chip for: the ones of liked songs and the selected ones, so they can
     * be turned off. Empty when there's nothing to choose between.
     */
    val choices: StateFlow<List<Origin>> =
        combine(songRepository.likedOrigins(), _origins) { stored, chosen ->
            val offered = stored + chosen
            if (offered.size <= 1 && chosen.isEmpty())
                emptyList()
            else
                Origin.entries.filter { it in offered }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * The origins of the songs Unlike all takes the like from: the ones shown, of liked songs
     * only, every one of them while no origin is picked.
     */
    val affectedOrigins: StateFlow<Set<Origin>> =
        combine(songRepository.likedOrigins(), _origins) { stored, chosen ->
            if (chosen.isEmpty()) stored else stored.intersect(chosen)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** The liked songs of the origins shown, in the order picked, a page at a time. */
    val songs: Flow<PagingData<LikedRow>> =
        combine(_origins, _newestFirst, ::Pair)
            .flatMapLatest { (origins, newestFirst) ->
                val query = ++queries
                val direction =
                    if (newestFirst) SortDirection.DESCENDING else SortDirection.ASCENDING
                songRepository.liked(SortOrder(UserSongSort.LIKED_AT, direction), origins)
                    .map { page ->
                        val now = System.currentTimeMillis()
                        page.flatMap { listOfNotNull(it.toRow(query, now)) }
                    }
            }.cachedIn(viewModelScope)

    fun toggleOrder() {
        _newestFirst.value = !_newestFirst.value
    }

    /** Shows the songs of [origin] when they're hidden, hides them otherwise. */
    fun toggleOrigin(origin: Origin) {
        _origins.value = _origins.value.let { if (origin in it) it - origin else it + origin }
    }

    /**
     * Takes back the like of every song shown, of every liked song while no origin is picked, one
     * after the other on the app's scope, so it lands even when the page closes right away. Once
     * every song is done, [onDone] gets what to say, see [unlikedMessage], with an Undo that likes
     * the songs done again, from the time they were liked.
     */
    fun unlikeAll(onDone: (Message) -> Unit) {
        val origins = _origins.value
        scope.launch {
            val songs = songRepository.listLiked(origins)
            // Only a like goes, a dislike made meanwhile stays
            val failures = songs.map { song ->
                react(song, R.string.error_remove_like) { likedAt ->
                    likedAt?.takeIf { it.toEpochMilliseconds() < 0 }
                }
            }
            val done = songs.filterIndexed { index, _ -> failures[index] == null }
            val undo = done.takeIf { it.isNotEmpty() }?.let { { likeAgain(it, onDone) } }
            unlikedMessage(context.resources, failures, undo)?.let(onDone)
        }
    }

    /**
     * Puts back the likes of [songs] as they were, unless a reaction was made meanwhile.
     * [onFailed] gets the first failure.
     */
    private fun likeAgain(songs: List<UserSongView>, onFailed: (Message) -> Unit) {
        scope.launch {
            val failures = songs.mapNotNull { song ->
                react(song, R.string.error_like_song) { it ?: song.likedAt }
            }
            failures.firstOrNull()?.let { onFailed(Message(it, MessageDuration.LONG)) }
        }
    }

    private suspend fun react(
        song: UserSongView,
        @StringRes asked: Int,
        change: (Instant?) -> Instant?,
    ): String? = reactToSong(
        context, songRepository, openSubsonicLibrary, youTubeLibrary,
        song.songId, song.origin, song.name, asked, change,
    )

    private fun UserSongView.toRow(query: Int, now: Long): LikedRow? = LikedRow(
        item = toMediaItem() ?: return null,
        time = relativeTime(likedAt?.toEpochMilliseconds() ?: now, now),
        query = query,
    )
}

/**
 * What to say once the likes of some songs were taken back, [failures] holding each song's
 * failure, `null` for the ones done, see [removedReactionMessage].
 */
internal fun unlikedMessage(
    resources: Resources,
    failures: List<String?>,
    undo: (() -> Unit)? = null,
): Message? = removedReactionMessage(
    resources,
    failures,
    undo,
    R.string.snackbar_removed_from_like,
    R.string.snackbar_removed_from_like_count,
    R.string.snackbar_removed_from_like_some_failed,
)
