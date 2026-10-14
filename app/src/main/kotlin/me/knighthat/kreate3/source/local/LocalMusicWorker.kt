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

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.database.repository.LocalCatalogRepository
import me.knighthat.kreate3.utils.hasAudioPermission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Reads the music on the device and brings the local catalog up to date. After a scan that
 * worked, the loudness of new songs is measured, see [LoudnessWorker].
 *
 * Without the audio permission it does nothing, the next run tries again.
 */
class LocalMusicWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val catalog: LocalCatalogRepository by inject()

    override suspend fun doWork(): Result {
        if (!applicationContext.hasAudioPermission()) {
            Log.i(TAG, "Skipped, the audio permission isn't granted")
            return Result.success()
        }

        return try {
            val songs = withContext(Dispatchers.IO) {
                MediaStoreScanner(applicationContext.contentResolver).scan()
            }
            Log.i(TAG, "Found ${songs.size} songs on the device")
            catalog.sync(songs)
            LocalMusicScheduler.measureLoudness(applicationContext)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Scanning local music failed", e)
            Result.retry()
        }
    }

    private companion object {

        const val TAG = "LocalMusicWorker"
    }
}
