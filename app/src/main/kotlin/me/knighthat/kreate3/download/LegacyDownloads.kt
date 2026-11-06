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

package me.knighthat.kreate3.download

import android.content.Context
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.provider.youtube.YouTubeItem
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.youtube.toMediaItem
import me.knighthat.kreate3.ui.component.toMediaItem
import java.io.File

/**
 * The songs the old Kreate app had downloaded, which the user is asked to download again after
 * importing it. The old app's list of YouTube Music ids is kept in [FILE_NAME] under
 * `noBackupFilesDir` until the user decides.
 *
 * Only songs some profile has in its library are offered, and they are downloaded from what the
 * library knows of them: a downloaded file that no user marks is
 * deleted at the next start (`SongDownloads` `sweep`). The files are made once and shared; then
 * every profile that has a song in its library gets it marked as downloaded
 * ([UserSongRepository.markDownloadedForEveryUser]), and the others get nothing.
 */
class LegacyDownloads(
    private val context: Context,
    private val userSongs: UserSongRepository,
    private val downloads: SongDownloads,
    private val youTube: YouTubeProvider,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
) {

    private val file: File get() = File(context.noBackupFilesDir, FILE_NAME)

    private val _pending = MutableStateFlow<List<String>>(emptyList())

    /** The offered songs as the library has them, so that nothing is looked up to download them. */
    private var known: Map<String, MediaItem> = emptyMap()

    /** The ids to offer, none while there is nothing to decide. */
    val pending: StateFlow<List<String>> = _pending.asStateFlow()

    /**
     * Reads the kept list and offers the songs a profile has in its library. A list with none of
     * them is deleted. Call it once the database is there.
     */
    suspend fun load() = withContext(Dispatchers.IO) {
        val text = file.takeIf(File::isFile)?.readText() ?: return@withContext
        val ids = parseLegacyDownloadIds(text)
        val inLibrary = userSongs.inAnyLibrary(ids).mapNotNull { it.toMediaItem() }
            .associateBy { it.mediaId }
        val offered = ids.filter { it in inLibrary }
        if (offered.isEmpty()) file.delete()
        known = inLibrary
        _pending.value = offered
    }

    /**
     * The details of [ids] as songs to show, a hundred to a request, in the order asked. Songs
     * YouTube doesn't know are left out.
     *
     * @throws YouTubeException when YouTube can't be reached
     */
    suspend fun details(ids: List<String>): List<MediaItem> =
        youTube.songDetailsOf(ids).map { YouTubeItem.SongItem(it).toMediaItem(youTube) }

    /**
     * Downloads every offered song on the app's own scope. The songs are in the library, so
     * nothing is looked up: a download that fails shows in its notification.
     */
    fun downloadAll() {
        val items = _pending.value.mapNotNull(known::get)
        _pending.value = emptyList()
        known = emptyMap()
        scope.launch {
            notifier.post(started(items.size))
            file.delete()
            queue(items)
        }
    }

    /**
     * Downloads [items], songs the user picked, and forgets the rest of the list. With none
     * picked nothing is downloaded and the list is forgotten all the same.
     */
    fun download(items: List<MediaItem>) {
        _pending.value = emptyList()
        known = emptyMap()
        scope.launch {
            file.delete()
            if (items.isEmpty()) return@launch
            notifier.post(started(items.size))
            queue(items)
        }
    }

    /** Forgets the list, nothing is downloaded. */
    fun dismiss() {
        _pending.value = emptyList()
        scope.launch(Dispatchers.IO) { file.delete() }
    }

    private suspend fun started(count: Int): String =
        context.getString(
            R.string.snackbar_old_app_downloads_started,
            context.resources.getQuantityString(R.plurals.song, count, count)
        )

    /** Makes the files, then marks the ones that are there for the users who have them. */
    private suspend fun queue(items: List<MediaItem>) {
        val accepted = items.filter { downloads.downloadUnmarked(it) }.map { it.mediaId }
        userSongs.markDownloadedForEveryUser(accepted)
    }

    companion object {

        const val FILE_NAME = "legacy_downloads.json"

        /** Songs asked for in one request. */
        const val BATCH = 100

    }
}

private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

/**
 * The ids in the old app's `downloaded.json`, a JSON array of YouTube Music ids. Anything that is
 * not an id is skipped, text that is not such an array gives none.
 */
internal fun parseLegacyDownloadIds(text: String): List<String> = try {
    Json.parseToJsonElement(text).jsonArray
        .mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        .filter(VIDEO_ID::matches)
        .distinct()
} catch (_: Exception) {
    emptyList()
}
