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

package me.knighthat.kreate.ui.navigation

import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import me.knighthat.kreate.R
import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.ui.component.origin

/**
 * Every screen the app can show. Entries on the back stack are instances of this.
 */
@Serializable
sealed interface Route : NavKey {

    /**
     * What the screen's ViewModel reads from its `SavedStateHandle`, see [routeViewModel].
     */
    val arguments: Bundle get() = Bundle.EMPTY
}

@Serializable
data object Songs : Route

@Serializable
data object Albums : Route

@Serializable
data object Artists : Route

@Serializable
data object Playlists : Route

@Serializable
data object Search : Route

/** The list of settings pages. */
@Serializable
data object Settings : Route

@Serializable
data object GeneralSettings : Route

@Serializable
data object UiSettings : Route

@Serializable
data object NetworkSettings : Route

/**
 * What [origin] found for [query], by kind. The screen opens on the [tab] of one of them.
 */
@Serializable
data class SearchResults(val query: String, val origin: Origin, val tab: Tab) : Route {

    override val arguments: Bundle get() = bundleOf(QUERY to query, ORIGIN to origin.name)

    companion object {
        const val QUERY = "query"
        const val ORIGIN = "origin"
    }
}

@Serializable
data class LocalAlbum(val id: String) : Route {

    override val arguments: Bundle get() = bundleOf(ID to id)

    companion object {
        const val ID = "albumId"
    }
}

@Serializable
data class LocalArtist(val id: String) : Route {

    override val arguments: Bundle get() = bundleOf(ID to id)

    companion object {
        const val ID = "artistId"
    }
}

@Serializable
data class LocalPlaylist(val id: Long) : Route {

    override val arguments: Bundle get() = bundleOf(ID to id)

    companion object {
        const val ID = "playlistId"
    }
}

/**
 * An album of the OpenSubsonic server, its songs are fetched before it shows. Its screen reads
 * the album like [LocalAlbum] does, from the same argument.
 */
@Serializable
data class OpenSubsonicAlbum(val id: String) : Route {

    override val arguments: Bundle get() = bundleOf(LocalAlbum.ID to id)
}

/**
 * An artist of the OpenSubsonic server, the songs of their albums are fetched before it shows.
 */
@Serializable
data class OpenSubsonicArtist(val id: String) : Route {

    override val arguments: Bundle get() = bundleOf(LocalArtist.ID to id)
}

/**
 * A playlist of the OpenSubsonic server, by its id in the database. Its songs are fetched
 * before it shows.
 */
@Serializable
data class OpenSubsonicPlaylist(val id: Long) : Route {

    override val arguments: Bundle get() = bundleOf(LocalPlaylist.ID to id)
}

/**
 * The screen of an album, by where it comes from.
 */
fun albumRoute(item: MediaItem): Route =
    if (item.origin == Origin.OPEN_SUBSONIC) OpenSubsonicAlbum(item.mediaId) else LocalAlbum(item.mediaId)

/**
 * The screen of an artist, by where they come from.
 */
fun artistRoute(item: MediaItem): Route =
    if (item.origin == Origin.OPEN_SUBSONIC) OpenSubsonicArtist(item.mediaId) else LocalArtist(item.mediaId)

/**
 * The screen of a playlist, by where it comes from.
 */
fun playlistRoute(item: MediaItem): Route =
    if (item.origin == Origin.OPEN_SUBSONIC) OpenSubsonicPlaylist(item.mediaId.toLong())
    else LocalPlaylist(item.mediaId.toLong())

/**
 * The destinations that share the library bottom bar, in the order they appear on it.
 */
enum class Tab(
    val route: Route,
    val label: String,
    @param:DrawableRes val icon: Int,
) {
    SONGS(Songs, "Songs", R.drawable.music_note),
    ALBUMS(Albums, "Albums", R.drawable.album),
    ARTISTS(Artists, "Artists", R.drawable.artist),
    PLAYLISTS(Playlists, "Playlists", R.drawable.queue_music);

    companion object {

        /** The tab that owns [route], or `null` for screens outside the library. */
        fun of(route: Route?): Tab? = entries.firstOrNull { it.route == route }
    }
}
