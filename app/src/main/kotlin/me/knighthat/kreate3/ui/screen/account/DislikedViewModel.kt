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
import android.text.format.DateUtils
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
import me.knighthat.kreate3.database.model.DislikedSong
import me.knighthat.kreate3.database.model.UserSongView
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.notification.MessageDuration
import me.knighthat.kreate3.notification.undoMessage
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicLibrary
import me.knighthat.kreate3.provider.youtube.YouTubeLibrary
import me.knighthat.kreate3.ui.component.toMediaItem
import kotlin.time.Instant

/**
 * A disliked song: its [item], and [time] saying how long ago it was disliked.
 *
 * @property query the query the row came from, a new one for every order or filter: the same
 * song in another query is another row, the list doesn't follow it there
 */
@Immutable
class DislikedRow(val item: MediaItem, val time: String, val query: Int)

/**
 * The songs the active user disliked, which every other list hides, most recently disliked
 * first. Nothing is kept: the page opens on every origin, newest first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DislikedViewModel(
    private val context: Context,
    private val songRepository: UserSongRepository,
    private val openSubsonicLibrary: OpenSubsonicLibrary,
    private val youTubeLibrary: YouTubeLibrary,
    private val playback: PlaybackController,
    private val scope: CoroutineScope,
) : ViewModel() {

    private val _newestFirst = MutableStateFlow(true)
    private val _origins = MutableStateFlow(emptySet<Origin>())
    private var queries = 0

    /** Whether the most recently disliked song comes first. */
    val newestFirst: StateFlow<Boolean> = _newestFirst.asStateFlow()

    /** The origins shown, every origin while it is empty. */
    val origins: StateFlow<Set<Origin>> = _origins.asStateFlow()

    /**
     * The origins to offer a chip for: the ones of disliked songs and the selected ones, so they
     * can be turned off. Empty when there's nothing to choose between.
     */
    val choices: StateFlow<List<Origin>> =
        combine(songRepository.dislikedOrigins(), _origins) { stored, chosen ->
            val offered = stored + chosen
            if (offered.size <= 1 && chosen.isEmpty())
                emptyList()
            else
                Origin.entries.filter { it in offered }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The disliked songs of the origins shown, in the order picked, a page at a time. */
    val songs: Flow<PagingData<DislikedRow>> =
        combine(_origins, _newestFirst, ::Pair)
            .flatMapLatest { (origins, newestFirst) ->
                val query = ++queries
                songRepository.disliked(origins, newestFirst).map { page ->
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
     * Takes back the dislike of every song shown, of every disliked song while no origin is picked,
     * one after the other on the app's scope, so it lands even when the page closes right away.
     * Once every song is done, [onDone] gets what to say, see [undislikedMessage], with an Undo
     * that dislikes the songs done again, from the time they were disliked.
     */
    fun undislikeAll(onDone: (Message) -> Unit) {
        val origins = _origins.value
        scope.launch {
            val songs = songRepository.listDisliked(origins)
            // Only a dislike goes, a like made meanwhile stays
            val failures = songs.map { song ->
                react(
                    song.songId,
                    song.origin,
                    song.name,
                    R.string.error_remove_dislike
                ) { likedAt ->
                    likedAt?.takeUnless { it.toEpochMilliseconds() < 0 }
                }
            }
            val done = songs.filterIndexed { index, _ -> failures[index] == null }
            val undo = done.takeIf { it.isNotEmpty() }?.let { { dislikeAgain(it, onDone) } }
            undislikedMessage(context.resources, failures, undo)?.let(onDone)
        }
    }

    /**
     * Puts back the dislikes of [songs] as they were, taking them out of the queue again, unless
     * a reaction was made meanwhile. [onFailed] gets the first failure.
     */
    private fun dislikeAgain(songs: List<UserSongView>, onFailed: (Message) -> Unit) {
        scope.launch {
            val failures = songs.mapNotNull { song ->
                playback.remove(playback.queue.value.filter { it.mediaId == song.songId })
                react(song.songId, song.origin, song.name, R.string.error_dislike_song) {
                    it ?: song.likedAt
                }
            }
            failures.firstOrNull()?.let { onFailed(Message(it, MessageDuration.LONG)) }
        }
    }

    private suspend fun react(
        songId: String,
        origin: Origin,
        name: String?,
        @StringRes asked: Int,
        change: (Instant?) -> Instant?,
    ): String? = reactToSong(
        context, songRepository, openSubsonicLibrary, youTubeLibrary,
        songId, origin, name, asked, change,
    )

    /** How long ago the song was disliked, as of [now], a date once it's over a week ago. */
    private fun DislikedSong.toRow(query: Int, now: Long): DislikedRow? =
        DislikedRow(
            item = song.toMediaItem() ?: return null,
            time = relativeTime(dislikedAt.toEpochMilliseconds(), now),
            query = query,
        )
}

/** How long ago [millis] was, as of [now], a date once it's over a week ago. */
internal fun relativeTime(millis: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(
        millis,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_ALL,
    ).toString()

/**
 * What to say once the dislikes of some songs were taken back, [failures] holding each song's
 * failure, `null` for the ones done. One song: done, or why it failed. Several: how many were
 * done, and how many failed when some did. `null` when there was no song. With [undo], a message
 * saying some were done has an Undo button running it.
 */
internal fun undislikedMessage(
    resources: Resources,
    failures: List<String?>,
    undo: (() -> Unit)? = null,
): Message? = removedReactionMessage(
    resources,
    failures,
    undo,
    R.string.snackbar_removed_from_dislike,
    R.string.snackbar_removed_from_dislike_count,
    R.string.snackbar_removed_from_dislike_some_failed,
)

/**
 * [undislikedMessage] for any reaction: [done] is the text for one song done, [counted] takes
 * "3 songs" for several, [someFailed] also takes how many failed.
 */
internal fun removedReactionMessage(
    resources: Resources,
    failures: List<String?>,
    undo: (() -> Unit)?,
    @StringRes done: Int,
    @StringRes counted: Int,
    @StringRes someFailed: Int,
): Message? {
    val failed = failures.count { it != null }
    val succeeded = failures.size - failed
    val text = when {
        failures.isEmpty() -> return null
        failures.size == 1 -> failures.single()
            ?.let { return Message(it, MessageDuration.LONG) }
            ?: resources.getString(done)

        failed == 0 -> resources.getString(
            counted,
            resources.getQuantityString(R.plurals.song, succeeded, succeeded)
        )
        else -> resources.getString(
            someFailed,
            resources.getQuantityString(R.plurals.song, succeeded, succeeded),
            failed
        )
    }
    return when {
        undo != null -> undoMessage(resources, text, undo)
        failed > 0 -> Message(text, MessageDuration.LONG)
        else -> Message(text)
    }
}
