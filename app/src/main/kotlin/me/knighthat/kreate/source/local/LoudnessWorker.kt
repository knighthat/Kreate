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

package me.knighthat.kreate.source.local

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.database.model.SongFormat
import me.knighthat.kreate.database.repository.SongRepository
import me.knighthat.kreate.utils.hasAudioPermission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Tag of the format of a song on this device, it has one format: the file.
 */
const val LOCAL_FORMAT_TAG = "local"

/**
 * Measures the loudness of the songs on the device whose format has no gain yet, and saves it as
 * the gain of their [LOCAL_FORMAT_TAG] format. A song whose file has another size than when it was
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

        // Songs a scan adds while this runs are picked up by asking again
        while (true) {
            val pending = (songs.idsWithoutGain(Origin.LOCAL, LOCAL_FORMAT_TAG) +
                    replaced(scanner)).distinct() - failed - done
            if (pending.isEmpty()) break

            // Several songs are measured at once, the results are handled one by one
            pending.asFlow()
                .flatMapMerge(PARALLELISM) { songId ->
                    flow {
                        emit(
                            songId to measure(
                                analyzer,
                                songId
                            )
                        )
                    }
                }
                .collect { (songId, format) ->
                    if (format == null) {
                        failed += songId
                        return@collect
                    }
                    songs.saveFormat(format)
                    // Once each run, a file the index and the file system disagree on would repeat forever
                    done += songId
                }
        }

        Log.i(TAG, "Measured ${done.size} songs, ${failed.size} couldn't be measured")
        return Result.success()
    }

    /**
     * Songs with a gain whose file isn't the size it was when measured.
     */
    private suspend fun replaced(scanner: MediaStoreScanner): List<String> {
        val sizes = withContext(Dispatchers.IO) { scanner.sizes() }
        return songs.formatLengths(LOCAL_FORMAT_TAG)
            .filter { (songId, length) -> sizes[songId]?.let { it != length } == true }
            .keys.toList()
    }

    private suspend fun measure(analyzer: LoudnessAnalyzer, songId: String): SongFormat? {
        val id = songId.toLongOrNull() ?: return null
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        return try {
            withContext(Dispatchers.IO) { analyzer.analyze(songId, uri) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't measure song $songId", e)
            null
        }
    }

    private companion object {

        const val TAG = "LoudnessWorker"

        /** Songs measured at once. Decoding is heavy, so half the cores are left for the rest of the app. */
        val PARALLELISM = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
    }
}
