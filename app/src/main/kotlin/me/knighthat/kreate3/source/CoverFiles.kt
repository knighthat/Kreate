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

package me.knighthat.kreate3.source

import android.content.Context
import android.net.Uri
import android.util.Log
import coil3.request.Options
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.preferences.Profile
import java.io.File

/**
 * Covers kept as files on the device, which a user's song can have as its own
 * (`user_song.thumbnail`): the covers of downloaded songs, in [downloads], shared like the
 * downloads, and the ones a user picked, in their profile's folder ([custom]), which goes with the
 * profile. Both are left out of backups.
 *
 * A downloaded cover is kept in the two sizes of the image cache, [SMALL_COVER] and [LARGE_COVER];
 * songs point at the large one, [Mapper] draws the small one where it is enough.
 */
class CoverFiles(context: Context) {

    /** The covers of downloaded songs. */
    val downloads = File(context.noBackupFilesDir, "download_covers")

    /** Where the files of this device only are. */
    private val root = context.noBackupFilesDir

    /** Where every profile's folder is, see [Profile.directory]. */
    private val profiles = Profile.directory(root, Profile.DEFAULT_ID).parentFile

    /** The covers the user with [profileId] picked for their songs (a profile's id is its user's). */
    fun custom(profileId: String): File =
        File(Profile.directory(root, profileId), CUSTOM_DIRECTORY)

    /** What a song's own [cover] is, [CoverKind.NONE] for none. */
    fun kindOf(cover: Uri?): CoverKind {
        val file = cover?.file() ?: return if (cover == null) CoverKind.NONE else CoverKind.OTHER
        val folder = file.parentFile
        return if (folder == downloads) CoverKind.DOWNLOADED
        else if (folder?.name == CUSTOM_DIRECTORY && folder.parentFile?.parentFile == profiles)
            CoverKind.CUSTOM
        else CoverKind.OTHER
    }

    /** The file of the cover downloaded from [address] in [size], see [SMALL_COVER]. */
    fun downloaded(address: String, size: Int): File = File(downloads, "$address-$size.jpg")

    /** The large cover's file the same cover in [size] is next to, `null` for any other file. */
    fun sizedLike(large: File, size: Int): File? {
        if (large.parentFile != downloads) return null
        val address = large.name.removeSuffix("-$LARGE_COVER.jpg")
        if (address == large.name) return null
        return downloaded(address, size)
    }

    /**
     * Takes off every user's songs the covers of [downloads] and [custom] whose file is gone, like
     * after a backup was restored on another device: see [UserSongRepository.forgetThumbnail].
     */
    suspend fun forgetMissing(userSongs: UserSongRepository) {
        try {
            val missing = withContext(Dispatchers.IO) {
                userSongs.fileThumbnails().filter {
                    kindOf(it) in KEPT && it.file()?.exists() != true
                }
            }
            missing.forEach { userSongs.forgetThumbnail(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't forget the covers that are gone", e)
        }
    }

    /**
     * Draws the [SMALL_COVER] copy of a downloaded cover where [coverSize] says that is enough,
     * like the image cache does with the covers of sources.
     */
    class Mapper(private val files: CoverFiles) : coil3.map.Mapper<Uri, Uri> {

        override fun map(data: Uri, options: Options): Uri? {
            if (coverSize(options.size) != SMALL_COVER) return null
            val large = data.file() ?: return null
            return files.sizedLike(large, SMALL_COVER)?.let(Uri::fromFile)
        }
    }

    private companion object {
        const val TAG = "CoverFiles"

        /** In each profile's folder. */
        const val CUSTOM_DIRECTORY = "custom_thumbnail"

        /** The kinds of covers this keeps the files of. */
        val KEPT = setOf(CoverKind.DOWNLOADED, CoverKind.CUSTOM)
    }
}

/** What a song's own cover is, see [CoverFiles.kindOf]. */
enum class CoverKind {
    /** None, the shared cover shows. */
    NONE,

    /** The cover of a downloaded song, in [CoverFiles.downloads]. */
    DOWNLOADED,

    /** One a user picked, in their [CoverFiles.custom]. */
    CUSTOM,

    /** An address or a file from anywhere else. */
    OTHER,
}

/** The file [this] is the address of, `null` when it isn't a file's. */
private fun Uri.file(): File? = if (scheme == "file") path?.let(::File) else null
