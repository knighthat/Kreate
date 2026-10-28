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
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.flatMap
import kotlinx.coroutines.CancellationException
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
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.notification.MessageDuration
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicLibrary
import me.knighthat.kreate3.provider.opensubsonic.ServerException
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.provider.youtube.YouTubeLibrary
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.screen.detail.serverFailure
import me.knighthat.kreate3.ui.screen.detail.youTubeFailure
import kotlin.time.Instant

private const val TAG = "DislikedViewModel"

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
            if (offered.size <= 1 && chosen.isEmpty()) emptyList()
            else Origin.entries.filter { it in offered }
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
     * Once every song is done, [onDone] gets what to say, see [undislikedMessage].
     */
    fun undislikeAll(onDone: (Message) -> Unit) {
        val origins = _origins.value
        scope.launch {
            val failures = songRepository.listDisliked(origins)
                .map { undislike(it.songId, it.origin) }
            undislikedMessage(context.resources, failures)?.let(onDone)
        }
    }

    /**
     * Takes back the dislike of [songId], at its source too, like the song menu's Dislike does.
     * A source that doesn't take it gets the dislike put back.
     *
     * @return what to say when it failed, `null` once it's done
     */
    private suspend fun undislike(songId: String, origin: Origin): String? {
        // Only a dislike goes, a like made meanwhile stays
        val change = { likedAt: Instant? ->
            likedAt?.takeUnless { it.toEpochMilliseconds() < 0 }
        }
        return try {
            when (origin) {
                Origin.OPEN_SUBSONIC -> openSubsonicLibrary.react(songId, change)
                Origin.YOUTUBE -> youTubeLibrary.react(songId, change)
                else -> songRepository.update(songId) { it.copy(likedAt = change(it.likedAt)) }
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: ServerException) {
            e.serverFailure(context.resources, R.string.snackbar_change_remove_dislike)
        } catch (e: YouTubeException) {
            e.youTubeFailure(context.resources, R.string.snackbar_change_remove_dislike)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't take back the dislike of $songId", e)
            context.resources.getString(
                R.string.error_change_failed,
                context.resources.getString(R.string.snackbar_change_remove_dislike),
            )
        }
    }

    /** How long ago the song was disliked, as of [now], a date once it's over a week ago. */
    private fun DislikedSong.toRow(query: Int, now: Long): DislikedRow? =
        DislikedRow(
            item = song.toMediaItem() ?: return null,
            time = DateUtils.getRelativeTimeSpanString(
                dislikedAt.toEpochMilliseconds(),
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_ALL,
            ).toString(),
            query = query,
        )
}

/**
 * What to say once the dislikes of some songs were taken back, [failures] holding each song's
 * failure, `null` for the ones done. One song: done, or why it failed. Several: how many were
 * done, and how many failed when some did. `null` when there was no song.
 */
internal fun undislikedMessage(resources: Resources, failures: List<String?>): Message? {
    val failed = failures.count { it != null }
    val done = failures.size - failed
    return when {
        failures.isEmpty() -> null
        failures.size == 1 -> failures.single()?.let { Message(it, MessageDuration.LONG) }
            ?: Message(resources.getString(R.string.snackbar_removed_from_dislike))

        failed == 0 -> Message(
            resources.getString(
                R.string.snackbar_removed_from_dislike_count,
                done
            )
        )

        else -> Message(
            resources.getString(R.string.snackbar_removed_from_dislike_some_failed, done, failed),
            MessageDuration.LONG,
        )
    }
}
