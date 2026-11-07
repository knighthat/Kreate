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
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.ProfileManager
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Keeps the pictures the user picks from the device for their playlists, songs, albums and
 * artists: a picked picture (a `content://` address that only lives while the app runs) is copied
 * into the profile's [CoverFiles.custom] folder and the item keeps the copy. A file is named after
 * its owner, see [ownerOf], so it is deleted with the picture it replaced.
 */
class PickedCovers(
    private val context: Context,
    private val covers: CoverFiles,
    private val profiles: ProfileManager,
) {

    /**
     * Copies [picked] to a file of [owner]'s, when it is an address of the device's; any other
     * (a file already kept, an address, `null`) comes back as it is.
     *
     * @return where the picture is now
     */
    suspend fun keep(owner: String, picked: Uri?): Uri? {
        if (picked?.scheme != "content") return picked

        val profile = profileId()
        return withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val extension = resolver.getType(picked)
                ?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType)
                ?: "jpg"
            val target = covers.newPicked(profile, owner, extension)
            target.parentFile?.mkdirs()
            try {
                val input = resolver.openInputStream(picked)
                    ?: throw IOException("Can't read $picked")
                input.use { from -> target.outputStream().use { from.copyTo(it) } }
            } catch (e: Exception) {
                target.delete()
                throw e
            }
            Uri.fromFile(target)
        }
    }

    /**
     * Deletes [picture] when it is [owner]'s own file and the item doesn't keep it anymore,
     * [kept] being what it keeps instead.
     */
    suspend fun discard(owner: String, picture: Uri?, kept: Uri?) {
        if (picture == null || picture == kept) return

        val profile = profileId()
        if (covers.isPickedBy(profile, owner, picture))
            withContext(Dispatchers.IO) { picture.path?.let(::File)?.delete() }
    }

    private suspend fun profileId(): String = checkNotNull(profiles.activeId.first { it != null })

    companion object {

        /** The owner of a playlist's pictures. */
        fun ownerOf(playlistId: Long): String = "playlist-$playlistId"

        /**
         * The owner of the pictures of the song, album or artist [id] of [origin], [kind] being
         * `song`, `album` or `artist`. Ids can hold anything, so the name is a hash of them.
         */
        fun ownerOf(kind: String, origin: Origin, id: String): String {
            val hash = MessageDigest.getInstance("SHA-1").digest("$origin:$id".toByteArray())
            return "$kind-" + hash.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
