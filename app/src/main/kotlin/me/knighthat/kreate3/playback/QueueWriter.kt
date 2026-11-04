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

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.repository.QueueRepository
import me.knighthat.kreate3.model.Origin

/**
 * A song of the queue to save.
 *
 * @property origin where the song comes from, `null` when the item doesn't say
 */
internal class QueueEntry(val id: String, val origin: Origin?)

/** One write to the saved queue, see [QueueWriter]. */
internal sealed interface QueueWrite {

    /** The whole queue, the song at [current] was playing at [position] ms. */
    class Queue(val songs: List<QueueEntry>, val current: Int, val position: Long) : QueueWrite

    class Position(val index: Int, val position: Long) : QueueWrite

    /** [songs] added from [index] on, only they are written. */
    class Insert(val index: Int, val songs: List<QueueEntry>) : QueueWrite

    class Remove(val index: Int, val count: Int) : QueueWrite

    class Move(val index: Int, val count: Int, val to: Int) : QueueWrite
}

/**
 * Writes to the saved queue in the order they were sent, a position or a change belongs to the
 * queue the writes before it left. Runs in [writeScope], which has to outlive the player.
 *
 * A remote song that isn't in the database is stored before the write that needs it
 * ([store]), the saved queue can only point at stored songs. A song that can't be stored is left
 * out of the saved queue, its place stays empty.
 *
 * @param store makes sure a remote song is in the shared catalog (not the library), throws [IllegalStateException] when
 * its source doesn't know it
 * @param onChangeFailed runs when a change to part of the queue couldn't be written: the saved
 * queue no longer matches the player's, so the whole queue has to be sent again
 */
internal class QueueWriter(
    private val queue: QueueRepository,
    private val store: suspend (songId: String, origin: Origin) -> Unit,
    writeScope: CoroutineScope,
    private val onChangeFailed: () -> Unit,
) {

    private val writes = Channel<QueueWrite>(Channel.UNLIMITED)

    init {
        writeScope.launch { write() }
    }

    fun send(write: QueueWrite) {
        writes.trySend(write)
    }

    /** Writes what was sent, then stops. */
    fun close() {
        writes.close()
    }

    private suspend fun write() {
        for (write in writes)
            try {
                when (write) {
                    is QueueWrite.Queue -> {
                        store(write.songs)
                        queue.replace(write.songs.map { it.id }, write.current, write.position)
                    }

                    is QueueWrite.Position -> queue.setPosition(write.index, write.position)

                    is QueueWrite.Insert -> {
                        store(write.songs)
                        queue.insert(write.index, write.songs.map { it.id })
                    }

                    is QueueWrite.Remove -> queue.remove(write.index, write.count)
                    is QueueWrite.Move -> queue.move(write.index, write.count, write.to)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save the queue", e)
                if (write !is QueueWrite.Queue && write !is QueueWrite.Position) onChangeFailed()
            }
    }

    /**
     * Stores the remote songs not stored yet, one request each. Stops at the first failure that
     * isn't "the source doesn't know it": the source is likely not reachable, and the other
     * songs would fail the same way.
     */
    private suspend fun store(songs: List<QueueEntry>) {
        for (song in songs.distinctBy { it.id }) {
            val origin = song.origin?.takeIf { it != Origin.LOCAL } ?: continue
            try {
                store(song.id, origin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Not stored: ${song.id}", e)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't store the songs of the queue", e)
                return
            }
        }
    }

    private companion object {

        const val TAG = "QueueWriter"
    }
}
