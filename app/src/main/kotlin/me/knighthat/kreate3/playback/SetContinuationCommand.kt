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
package me.knighthat.kreate3.playback

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand

/**
 * The session command the app's controller gives a queue it just started its [QueueContinuation]
 * with. A command, not a direct write: the service drops the continuation when the queue is
 * replaced, so it has to be given after that, by the service. It may arrive before the songs sent
 * with it are in the queue, see [QueueContinuationState.readyAt].
 */
@OptIn(UnstableApi::class)
internal object SetContinuationCommand {

    private const val KEY_CONTINUATION = "continuation"
    private const val KEY_READY_AT = "ready_at"

    val COMMAND = SessionCommand("me.knighthat.kreate3.SET_CONTINUATION", Bundle.EMPTY)

    /**
     * Arguments of [COMMAND] for [continuation], [readyAt] the number of songs the queue has
     * once the songs sent with it are in: the service doesn't judge how many are left before.
     */
    fun args(continuation: QueueContinuation, readyAt: Int): Bundle = Bundle().apply {
        putString(KEY_CONTINUATION, continuation.encode())
        putInt(KEY_READY_AT, readyAt)
    }

    /** The queue size [args] waits for. */
    fun readyAtOf(args: Bundle): Int = args.getInt(KEY_READY_AT, 0)

    /** The continuation [args] carries, `null` when it carries none. */
    fun continuationOf(args: Bundle): QueueContinuation? =
        QueueContinuation.decode(args.getString(KEY_CONTINUATION))
}
