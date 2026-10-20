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

package me.knighthat.kreate3.source.local

import android.content.ContentUris
import android.content.Context
import android.os.Process
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.database.model.SongFormat
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.notification.LocalScanNotifications
import me.knighthat.kreate3.source.local.LoudnessWorker.Companion.PARALLELISM
import me.knighthat.kreate3.utils.hasAudioPermission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Measures the loudness of the songs on the device whose format has no gain yet, and saves it as
 * the gain of their format, tagged with the name of [Origin.LOCAL]. A song on the device has one
 * format: the file. A song whose file has another size than when it was
 * measured was replaced, it is measured again. Each song is saved as soon as it is measured, so
 * a run that is stopped keeps what it did.
 *
 * A file that can't be measured is skipped and tried again by the next run.
 */
class LoudnessWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val songs: SongRepository by inject()
    private val notifications: LocalScanNotifications by inject()

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun doWork(): Result {
        if (!applicationContext.hasAudioPermission()) {
            Log.i(TAG, "Skipped, the audio permission isn't granted")
            return Result.success()
        }

        val analyzer = LoudnessAnalyzer(applicationContext)
        val scanner = MediaStoreScanner(applicationContext.contentResolver)
        val failed = mutableSetOf<String>()
        val done = mutableSetOf<String>()

        val decoding = lowPriorityDispatcher()
        try {
            // Songs a scan adds while this runs are picked up by asking again
            while (true) {
                val pending = (songs.idsWithoutGain(Origin.LOCAL, Origin.LOCAL.name) +
                        replaced(scanner)).distinct() - failed - done
                if (pending.isEmpty()) break

                val total = done.size + failed.size + pending.size
                notifications.measuring(done.size + failed.size, total)

                // Several songs are measured at once, the results are handled one by one
                pending.asFlow()
                    .flatMapMerge(PARALLELISM) { songId ->
                        flow { emit(songId to measure(analyzer, songId, decoding)) }
                    }
                    .collect { (songId, format) ->
                        if (format == null) failed += songId
                        else {
                            songs.saveFormat(format)
                            // Once each run, a file the index and the file system disagree on would repeat forever
                            done += songId
                        }
                        notifications.measuring(done.size + failed.size, total)
                    }
            }
        } catch (e: CancellationException) {
            notifications.cancelLoudness()
            throw e
        } finally {
            decoding.close()
        }

        Log.i(TAG, "Measured ${done.size} songs, ${failed.size} couldn't be measured")
        // Nothing to tell when every song was measured already
        if (done.isNotEmpty() || failed.isNotEmpty())
            notifications.measured(done.size, done.size + failed.size, failed.map { nameOf(it) })
        return Result.success()
    }

    /** What the user knows the song as, its id when it has no name. */
    private suspend fun nameOf(songId: String): String =
        songs.find(songId).first()?.name ?: "Song $songId"

    /**
     * Songs with a gain whose file isn't the size it was when measured.
     */
    private suspend fun replaced(scanner: MediaStoreScanner): List<String> {
        val sizes = withContext(Dispatchers.IO) { scanner.sizes() }
        return songs.formatLengths(Origin.LOCAL.name)
            .filter { (songId, length) -> sizes[songId]?.let { it != length } == true }
            .keys.toList()
    }

    /**
     * @param decoding where the file is decoded, see [lowPriorityDispatcher]
     */
    private suspend fun measure(
        analyzer: LoudnessAnalyzer,
        songId: String,
        decoding: CoroutineDispatcher,
    ): SongFormat? {
        val id = songId.toLongOrNull() ?: return null
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        return try {
            withContext(decoding) { analyzer.analyze(songId, uri) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't measure song $songId", e)
            null
        }
    }

    /**
     * [PARALLELISM] threads of background priority, for the decoding. At the default priority it
     * took the cores the screen needs: right after a scan the new songs showed only after a
     * stutter, the main thread waited for a core for a third of a long frame. The media services
     * the decoding calls into, like the one reading the file, run at the caller's priority, so
     * they give way too. Close it when done.
     */
    private fun lowPriorityDispatcher(): ExecutorCoroutineDispatcher {
        val count = AtomicInteger()
        return Executors.newFixedThreadPool(PARALLELISM) { task ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                task.run()
            }, "Loudness-${count.incrementAndGet()}")
        }.asCoroutineDispatcher()
    }

    private companion object {

        const val TAG = "LoudnessWorker"

        /** Songs measured at once. Decoding is heavy, so half the cores are left for the rest of the app. */
        val PARALLELISM = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
    }
}
