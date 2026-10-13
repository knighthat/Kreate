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

package me.knighthat.kreate.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * [PlaybackController] that talks to [PlaybackService] with a [MediaController]. Connecting also
 * starts the service, so nothing else has to.
 *
 * A controller must be used on the thread that made it, so all work runs on [scope], which has
 * to run on the main thread. Calls return before the work is done, and a failure is logged.
 */
class PlaybackControllerImpl(
    private val context: Context,
    private val scope: CoroutineScope,
) : PlaybackController {

    private var connection: ListenableFuture<MediaController>? = null

    override fun play(item: MediaItem) = run("play ${item.mediaId}") { controller ->
        val queued = (0 until controller.mediaItemCount)
            .firstOrNull { controller.getMediaItemAt(it).mediaId == item.mediaId }
        val index = queued ?: controller.mediaItemCount.also { controller.addMediaItem(item) }

        controller.seekToDefaultPosition(index)
        controller.prepare()
        controller.play()
    }

    /**
     * Runs [action] with the connected controller. It waits for the connection, the actions of
     * calls made meanwhile run in the order of the calls.
     */
    private fun run(what: String, action: (MediaController) -> Unit) {
        scope.launch {
            try {
                action(connect().await())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't $what", e)
            }
        }
    }

    /**
     * The connection that is running or being made, a new one when there is none or the service
     * went away since, for example after the user swiped the app away.
     */
    private fun connect(): ListenableFuture<MediaController> {
        connection?.takeIf { !it.isDone || it.isUsable() }?.let { return it }

        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        return MediaController.Builder(context, token).buildAsync().also { connection = it }
    }

    private fun ListenableFuture<MediaController>.isUsable(): Boolean =
        !isCancelled && runCatching { get().isConnected }.getOrDefault(false)

    private companion object {

        const val TAG = "PlaybackController"
    }
}
