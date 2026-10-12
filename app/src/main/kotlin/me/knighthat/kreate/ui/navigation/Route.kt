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
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import me.knighthat.kreate.R

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
