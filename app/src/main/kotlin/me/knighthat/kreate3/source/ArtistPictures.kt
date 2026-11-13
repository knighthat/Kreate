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

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.model.UserArtistView
import me.knighthat.kreate3.database.repository.ArtistRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.Providers
import java.util.concurrent.ConcurrentHashMap

/**
 * Fills in the picture of stored artists that have none. Artists stored from the songs crediting
 * them come without one, the credits carry only an id and a name; their source has the picture.
 * Device artists have no source to ask.
 */
class ArtistPictures(
    private val providers: Providers,
    private val artists: ArtistRepository,
    private val scope: CoroutineScope,
) {

    /** Artists asked for during this run, each is asked once even when the source had nothing. */
    private val asked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Asks the source of each of [shown] without a picture for one and stores it on the artist,
     * in the background. The rows change, so lists reading them show it when it comes. A source
     * that is off or fails leaves the artist as it is.
     */
    fun fillMissing(shown: List<UserArtistView>) {
        shown.filter { it.thumbnail == null && it.origin != Origin.LOCAL && asked.add(it.artistId) }
            .forEach { artist -> scope.launch { fetch(artist.artistId, artist.origin) } }
    }

    /**
     * The picture of the artist [artistId] of [origin]: the stored one, else asked of its source
     * once per run like [fillMissing], and stored when the artist is. `null` when there's none,
     * like for a device artist.
     */
    suspend fun pictureOf(artistId: String, origin: Origin): Uri? {
        val artist = artists.find(artistId).first()
        artist?.thumbnail?.let { return it }
        if (origin == Origin.LOCAL || !asked.add(artistId)) return null
        return fetch(artistId, origin)
    }

    private suspend fun fetch(artistId: String, origin: Origin): Uri? {
        val picture = try {
            providers[origin].artist(artistId)?.thumbnail
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return null
        // The user's own picture or one stored meanwhile stays; an artist not stored isn't added
        if (artists.find(artistId).first() != null)
            artists.update(artistId) { it.copy(thumbnail = it.thumbnail ?: picture) }
        return picture
    }
}
