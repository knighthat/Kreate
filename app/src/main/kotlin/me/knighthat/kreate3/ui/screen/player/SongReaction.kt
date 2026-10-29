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

package me.knighthat.kreate3.ui.screen.player

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.model.UserSongView
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.notification.MessageDuration
import me.knighthat.kreate3.notification.undoMessage
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.playback.songTitle
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicLibrary
import me.knighthat.kreate3.provider.opensubsonic.ServerException
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.provider.youtube.YouTubeLibrary
import me.knighthat.kreate3.source.SongStore
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.origin
import me.knighthat.kreate3.ui.screen.detail.serverFailure
import me.knighthat.kreate3.ui.screen.detail.youTubeFailure
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Instant

private const val TAG = "SongReaction"

/** What the user thinks of a song. */
enum class Reaction { NEUTRAL, LIKED, DISLIKED }

/**
 * The user's [reaction] to a song, `null` while it isn't known yet, and for a song that can't be
 * stored. Each toggle sets its reaction, or takes it away when it is the one set.
 */
@Immutable
class SongReaction(
    val reaction: Reaction?,
    val toggleLike: () -> Unit,
    val toggleDislike: () -> Unit,
)

/** What the library has of a song, [song] is `null` when it isn't there. */
private class Lookup(val song: UserSongView?)

/**
 * The reaction of the active user to the song of [item], following the database. A change is
 * saved on the app's scope, so it lands even when the player closes right away. A song of the
 * OpenSubsonic server also gets its star changed there, see [OpenSubsonicLibrary.react], and a
 * song of YouTube its like or dislike, see [YouTubeLibrary.react]; when that fails the reaction
 * goes back to what it was and a message says why.
 *
 * A song that isn't in the library has no reaction, the first one adds it there, after storing a
 * song fetched live (see [SongStore]). A dislike also takes the song out of the queue, see
 * [PlaybackController.drop]. A dislike made or taken back says so with an Undo, since the song
 * only leaves or reappears in lists; Undo puts back the reaction before, not the queue.
 */
@Composable
internal fun rememberSongReaction(item: MediaItem): SongReaction {
    val songId = item.mediaId
    val library = koinInject<UserSongRepository>()
    val songs = koinInject<SongStore>()
    val openSubsonic = koinInject<OpenSubsonicLibrary>()
    val youTube = koinInject<YouTubeLibrary>()
    val playback = koinInject<PlaybackController>()
    val scope = koinInject<CoroutineScope>()
    val notifier = LocalNotifier.current
    val resources = LocalResources.current
    val lookup by remember(library, songId) { library.find(songId).map(::Lookup) }
        .collectAsStateWithLifecycle(null)

    val song = lookup?.song
    val origin = song?.origin ?: item.origin
    val likedAt = song?.likedAt
    val reaction = when {
        // Until the library answers, and for a song nothing says where it comes from
        lookup == null || origin == null -> null
        likedAt == null -> Reaction.NEUTRAL
        likedAt.toEpochMilliseconds() < 0 -> Reaction.DISLIKED
        else -> Reaction.LIKED
    }

    /**
     * Saves what [change] makes of the song's `likedAt`, at its source too. [asked] says what it
     * does, for a message when the source doesn't take it.
     *
     * @return whether it was saved
     */
    suspend fun save(@StringRes asked: Int, change: (Instant?) -> Instant?): Boolean = try {
        // Only a song in the database can be added to the library
        if (song == null) songs.store(songId, origin)
        when (origin) {
            Origin.OPEN_SUBSONIC -> openSubsonic.react(songId, change)
            Origin.YOUTUBE -> youTube.react(songId, change)
            else -> library.update(songId) { it.copy(likedAt = change(it.likedAt)) }
        }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: ServerException) {
        // The reaction is already taken back
        notifier.post(Message(e.serverFailure(resources, asked), MessageDuration.LONG))
        false
    } catch (e: YouTubeException) {
        notifier.post(Message(e.youTubeFailure(resources, asked), MessageDuration.LONG))
        false
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't save the reaction to $songId", e)
        false
    }

    fun set(to: Reaction, likedAt: () -> Instant) = scope.launch {
        // Tapping the reaction that is set takes it away
        val removing = reaction == to
        val before = song?.likedAt
        val asked = when {
            removing && to == Reaction.LIKED -> R.string.snackbar_change_remove_like
            removing -> R.string.snackbar_change_remove_dislike
            to == Reaction.LIKED -> R.string.snackbar_change_like_song
            else -> R.string.snackbar_change_dislike_song
        }
        if (!save(asked) { if (removing) null else likedAt() }) return@launch
        // The song leaves or rejoins the lists, a like only changes its button
        if (to != Reaction.DISLIKED) return@launch

        val text = if (removing)
            resources.getString(R.string.snackbar_removed_from_dislike)
        else {
            val title = item.songTitle()?.let { resources.getString(R.string.quoted, it) }
                ?: resources.getString(R.string.snackbar_this_song)
            resources.getString(R.string.snackbar_disliked, title)
        }
        notifier.post(undoMessage(resources, text) {
            scope.launch {
                // The dislike back, it leaves the queue again
                if (removing) playback.drop(songId)
                save(
                    if (removing) R.string.snackbar_change_dislike_song
                    else R.string.snackbar_change_remove_dislike
                ) { before }
            }
        })
    }

    return SongReaction(
        reaction = reaction,
        toggleLike = { set(Reaction.LIKED) { Clock.System.now() } },
        toggleDislike = {
            // A song the user dislikes isn't worth finishing, nor hearing again
            if (reaction != Reaction.DISLIKED) playback.drop(songId)
            // Its time made negative, so the Disliked page can say when
            set(Reaction.DISLIKED) {
                Instant.fromEpochMilliseconds(-Clock.System.now().toEpochMilliseconds())
            }
        },
    )
}
