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

package me.knighthat.kreate3.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.knighthat.kreate3.database.repository.UserSongRepository
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Instant

/**
 * Saves the user's reactions to songs of a source and sends them to it, and keeps a fetch of the
 * source's liked songs from storing an answer given before the source got a reaction made
 * meanwhile.
 */
internal class ReactionSender(private val userSongs: UserSongRepository) {

    /** Keeps a reaction from being saved while the source's liked songs are stored. */
    private val storing = Mutex()

    /** Reactions saved but not sent yet. */
    private val sending = AtomicInteger()

    /** Counts reactions saved and sent, a fetch that saw it change may miss one of them. */
    private val reactions = AtomicLong()

    /**
     * Changes the reaction to [songId] to what [likedAt] makes of the one stored, see
     * `UserSong.likedAt`, then gives [send] the reaction before and after. When [send] fails the
     * reaction is taken back, unless another one replaced it meanwhile, and the failure is thrown.
     */
    suspend fun react(
        songId: String,
        likedAt: (Instant?) -> Instant?,
        send: suspend (before: Instant?, after: Instant?) -> Unit,
    ) {
        var before: Instant? = null
        var after: Instant? = null
        storing.withLock {
            userSongs.update(songId) { song ->
                before = song.likedAt
                song.copy(likedAt = likedAt(song.likedAt)).also { after = it.likedAt }
            }
            reactions.incrementAndGet()
            sending.incrementAndGet()
        }

        try {
            send(before, after)
        } catch (e: CancellationException) {
            // The source may have got it, the reaction stays
            throw e
        } catch (e: Exception) {
            val saved = after
            storing.withLock {
                userSongs.update(songId) {
                    if (it.likedAt == saved) it.copy(likedAt = before) else it
                }
            }
            throw e
        } finally {
            // Counted before it stops being sent, so a fetch always sees one or the other
            reactions.incrementAndGet()
            sending.decrementAndGet()
        }
    }

    /** What to give [store] once the source's liked songs are fetched. */
    fun mark(): Long = reactions.get()

    /**
     * Runs [save] to store liked songs fetched after [mark] gave [since], unless a reaction was
     * saved or sent meanwhile: the source may have answered before it got it.
     *
     * @return whether [save] ran
     */
    suspend fun store(since: Long, save: suspend () -> Boolean): Boolean = storing.withLock {
        if (sending.get() > 0 || reactions.get() != since) false else save()
    }
}

/** Whether a reaction holds a like, not a dislike or nothing, see `UserSong.likedAt`. */
internal val Instant?.isLike: Boolean
    get() = this != null && toEpochMilliseconds() != DISLIKED

/** Whether a reaction holds a dislike, see `UserSong.likedAt`. */
internal val Instant?.isDislike: Boolean
    get() = this?.toEpochMilliseconds() == DISLIKED

/** Stored as the reaction of a dislike, see `UserSong.likedAt`. */
private const val DISLIKED = -1L
