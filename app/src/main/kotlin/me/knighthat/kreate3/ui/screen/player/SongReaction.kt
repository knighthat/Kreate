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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.repository.UserSongRepository
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Instant

private const val TAG = "SongReaction"

/** Kept in `liked_at` for a dislike, see `UserSong.likedAt`. */
private const val DISLIKED = -1L

/** What the user thinks of a song. */
enum class Reaction { NEUTRAL, LIKED, DISLIKED }

/**
 * The user's [reaction] to a song, `null` while the song isn't in their library and can't be
 * rated yet, like a song fetched live right before it is stored. Each toggle sets its reaction,
 * or takes it away when it is the one set.
 */
@Immutable
class SongReaction(
    val reaction: Reaction?,
    val toggleLike: () -> Unit,
    val toggleDislike: () -> Unit,
)

/**
 * The reaction of the active user to the song [songId], following the database. A change is
 * saved on the app's scope, so it lands even when the player closes right away.
 */
@Composable
internal fun rememberSongReaction(songId: String): SongReaction {
    val library = koinInject<UserSongRepository>()
    val scope = koinInject<CoroutineScope>()
    val song by remember(library, songId) { library.find(songId) }
        .collectAsStateWithLifecycle(null)

    val likedAt = song?.likedAt
    val reaction = when {
        song == null -> null
        likedAt == null -> Reaction.NEUTRAL
        likedAt.toEpochMilliseconds() == DISLIKED -> Reaction.DISLIKED
        else -> Reaction.LIKED
    }

    fun set(to: Reaction, likedAt: () -> Instant) = scope.launch {
        try {
            library.update(songId) {
                // Tapping the reaction that is set takes it away
                it.copy(likedAt = if (reaction == to) null else likedAt())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't save the reaction to $songId", e)
        }
    }

    return SongReaction(
        reaction = reaction,
        toggleLike = { set(Reaction.LIKED) { Clock.System.now() } },
        toggleDislike = { set(Reaction.DISLIKED) { Instant.fromEpochMilliseconds(DISLIKED) } },
    )
}
