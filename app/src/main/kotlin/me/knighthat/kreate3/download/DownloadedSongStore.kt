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
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.auto.cover
import me.knighthat.kreate3.auto.coverSource
import me.knighthat.kreate3.database.model.Lyrics
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.lyrics.LyricsFinder
import me.knighthat.kreate3.playback.SongFormatRecorder
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.source.CoverFiles
import me.knighthat.kreate3.source.CoverKind
import me.knighthat.kreate3.source.LARGE_COVER
import me.knighthat.kreate3.source.SMALL_COVER
import me.knighthat.kreate3.source.SongStore
import me.knighthat.kreate3.ui.component.origin
import me.knighthat.kreate3.ui.screen.player.LyricsSettings
import me.knighthat.kreate3.ui.screen.player.loadLyrics
import me.knighthat.kreate3.ui.screen.player.lyricsQuery
import java.io.File
import java.security.MessageDigest

/**
 * Keeps in the database what a downloaded song needs offline. When it starts downloading: the
 * song, its album and artists, added to the library like a song that plays ([SongStore]), and
 * its format with its gain. Once it is whole: its cover, saved in [CoverFiles.downloads] in both
 * sizes of the image cache and set as the cover of the song (`user_song.thumbnail`, the large one)
 * of every user who downloaded it, unless they picked one of their own that is still there, and with
 * [Preferences.DOWNLOAD_LYRICS] on its lyrics, like the lyrics page would store them.
 *
 * Covers are named after their address, songs of one album share the files. When a download is
 * removed, its song's rows lose that cover, see [UserSongRepository.forgetThumbnail]; the files
 * go once no download uses them anymore, and every song of every user with them too.
 *
 * Nothing here stops a download: what fails is logged, the song plays without it.
 */
class DownloadedSongStore(
    private val context: Context,
    private val store: SongStore,
    private val formats: SongFormatRecorder,
    private val songs: SongRepository,
    private val userSongs: UserSongRepository,
    private val lyrics: LyricsFinder,
    private val covers: CoverFiles,
) {

    /**
     * Stores the song of [item] with its album and artists, and the format found for it so far.
     * Called when it starts downloading: the format found while it downloads is stored at once.
     */
    suspend fun store(item: MediaItem): Boolean = attempt("store") {
        store.store(item.mediaId, item.origin)
        formats.flush(item.mediaId)
    }

    /**
     * The song of [item] was downloaded whole: it is stored again in case that failed before,
     * like offline, then its cover is saved and, when the user wants, its lyrics.
     */
    suspend fun completed(item: MediaItem) {
        store(item)
        attempt("keep the cover of") { saveCover(item) }
        if (Preferences.DOWNLOAD_LYRICS.value) attempt("keep the lyrics of") { saveLyrics(item) }
    }

    /**
     * The download of [item] was removed. Its song loses the downloaded cover for every user, and
     * the cover's files go when none of [remaining], the songs still downloaded, uses them.
     */
    suspend fun removed(item: MediaItem, remaining: List<MediaItem>) =
        attempt("drop the cover of") {
            val large = coverFile(item)
            val thumbnail = large?.let(Uri::fromFile)
            // Nobody has it downloaded anymore
            userSongs.forgetDownload(item.mediaId, thumbnail, everyUser = true)
            if (large == null || thumbnail == null) return@attempt
            if (remaining.any { coverFile(it) == large }) {
                userSongs.forgetThumbnail(thumbnail, item.mediaId)
                return@attempt
            }
            userSongs.forgetThumbnail(thumbnail)
            withContext(Dispatchers.IO) {
                large.delete()
                covers.sizedLike(large, SMALL_COVER)?.delete()
            }
        }

    private suspend fun saveCover(item: MediaItem) {
        val large = coverFile(item) ?: return
        val small = covers.sizedLike(large, SMALL_COVER) ?: return
        val source = item.mediaMetadata.artworkUri?.coverSource() ?: return
        for ((file, size) in listOf(small to SMALL_COVER, large to LARGE_COVER)) {
            if (withContext(Dispatchers.IO) { file.exists() }) continue
            val bitmap = context.cover(source, size) ?: return
            withContext(Dispatchers.IO) { write(bitmap, file) }
        }

        val thumbnail = Uri.fromFile(large)
        // Each user who downloaded it
        userSongs.updateDownloaders(item.mediaId) { song ->
            when (covers.kindOf(song.thumbnail)) {
                CoverKind.NONE, CoverKind.DOWNLOADED -> song.copy(thumbnail = thumbnail)
                // The user's own stays while its file is there
                CoverKind.CUSTOM ->
                    if (song.thumbnail?.path?.let(::File)?.exists() == true) song
                    else song.copy(thumbnail = thumbnail)

                CoverKind.OTHER -> song
            }
        }
    }

    /** Writes [bitmap] to [file], whole or not at all: a cut file would show as a broken cover. */
    private fun write(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        val partial = File(file.parentFile, "${file.name}.tmp")
        partial.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        if (!partial.renameTo(file)) partial.delete()
    }

    private suspend fun saveLyrics(item: MediaItem) {
        val query = item.lyricsQuery() ?: return
        var found: Lyrics? = null
        // The settings' source, kept like the lyrics page keeps them: nothing when it has some
        // stored already, or when the user wants them fetched each time
        loadLyrics(
            query = query,
            request = null,
            settings = LyricsSettings.current(),
            remote = false,
            finder = lyrics,
            stored = { songs.lyricsOf(item.mediaId, it).first() },
            save = { found = it },
        )
        found?.let { songs.saveLyrics(it) }
    }

    /**
     * The large file of the cover of [item], named after the cover's address; `null` without a
     * cover. A cover that already is one of these files is that file.
     */
    private fun coverFile(item: MediaItem): File? {
        val source = item.mediaMetadata.artworkUri?.coverSource() ?: return null
        if (covers.kindOf(source) == CoverKind.DOWNLOADED) return source.path?.let(::File)
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toString().toByteArray())
        val name = digest.take(NAME_BYTES).joinToString("") { "%02x".format(it) }
        return covers.downloaded(name, LARGE_COVER)
    }

    /** The cover of [item] once downloaded, as users have it, `null` without a cover. */
    fun coverOf(item: MediaItem): Uri? = coverFile(item)?.let(Uri::fromFile)

    /** Runs [block], `false` when it failed, what failed is logged. */
    private suspend fun attempt(what: String, block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't $what a downloaded song", e)
        false
    }

    private companion object {
        const val TAG = "DownloadedSongStore"

        /** Like the covers of the image cache, see `encodeCover`. */
        const val QUALITY = 80

        /** Of the address's hash, plenty to tell covers apart. */
        const val NAME_BYTES = 16
    }
}
