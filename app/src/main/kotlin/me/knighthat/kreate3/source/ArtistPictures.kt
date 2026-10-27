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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
            .forEach { artist ->
                scope.launch {
                    val picture = try {
                        providers[artist.origin].artist(artist.artistId)?.thumbnail
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    } ?: return@launch
                    // The user's own picture or one stored meanwhile stays
                    artists.update(artist.artistId) { it.copy(thumbnail = it.thumbnail ?: picture) }
                }
            }
    }
}
