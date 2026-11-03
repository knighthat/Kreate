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

package me.knighthat.kreate3.ui.navigation

import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.media3.common.MediaItem
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import me.knighthat.kreate3.R
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.youtube.YouTubeBrowse
import me.knighthat.kreate3.ui.component.origin
import me.knighthat.kreate3.ui.component.remoteId

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

/** Caches and downloads, the room they take and their sizes. */
@Serializable
data object DataSettings : Route

@Serializable
data object PlayerSettings : Route

/** Where a voice request searches, opened from [PlayerSettings]. */
@Serializable
data object VoiceSearchSettings : Route

@Serializable
data object UiSettings : Route

@Serializable
data object NetworkSettings : Route

/** The YouTube account, opened from [NetworkSettings]. */
@Serializable
data object YouTubeSettings : Route

/** The OpenSubsonic server, opened from [NetworkSettings]. */
@Serializable
data object OpenSubsonicSettings : Route

/** The OpenSubsonic server's credentials, opened from [OpenSubsonicSettings]. */
@Serializable
data object OpenSubsonicSignIn : Route

@Serializable
data object BackupSettings : Route

/** The app's version and license, the last of the settings pages. */
@Serializable
data object About : Route

/** The libraries the app is built with, opened from [About]. */
@Serializable
data object OpenSourceLicenses : Route

/** What the active profile has done and kept, opened from the profile button. */
@Serializable
data object Account : Route

/** The songs the profile played, opened from [Account]. */
@Serializable
data object History : Route

/** How the profile listens, opened from [Account]. */
@Serializable
data object Statistics : Route

/** How the profile listens to the song [songId], opened from [Statistics]. */
@Serializable
data class SongStatistics(val songId: String) : Route {

    override val arguments: Bundle get() = Bundle().apply { putString(ID, songId) }

    companion object {
        const val ID = "songId"
    }
}

/**
 * What MusicBrainz knows about the song [songId], opened from the song menu. [title], [artist]
 * and [durationMs] are what it is searched by, they come with the menu's song: one fetched live
 * isn't in the database.
 */
@Serializable
data class SongDetails(
    val songId: String,
    val title: String,
    val artist: String?,
    val durationMs: Long?,
) : Route {

    override val arguments: Bundle
        get() = Bundle().apply {
            putString(ID, songId)
            putString(TITLE, title)
            putString(ARTIST, artist)
            durationMs?.let { putLong(DURATION, it) }
        }

    companion object {
        const val ID = "songId"
        const val TITLE = "title"
        const val ARTIST = "artist"
        const val DURATION = "durationMs"
    }
}

/**
 * What MusicBrainz knows about the artist named [name], opened from an artist's page. The name is
 * what they are searched by.
 */
@Serializable
data class ArtistDetails(val name: String) : Route {

    override val arguments: Bundle get() = Bundle().apply { putString(NAME, name) }

    companion object {
        const val NAME = "artistName"
    }
}

/** How the profile listens to the songs of the album [albumId], opened from [Statistics]. */
@Serializable
data class AlbumStatistics(val albumId: String) : Route {

    override val arguments: Bundle get() = Bundle().apply { putString(ID, albumId) }

    companion object {
        const val ID = "albumId"
    }
}

/** How the profile listens to the songs of the artist [artistId], opened from [Statistics]. */
@Serializable
data class ArtistStatistics(val artistId: String) : Route {

    override val arguments: Bundle get() = Bundle().apply { putString(ID, artistId) }

    companion object {
        const val ID = "artistId"
    }
}

/** The songs the profile disliked, opened from [Account]. */
@Serializable
data object Disliked : Route

/** The songs the profile downloaded to this device, opened from [Account]. */
@Serializable
data object Downloaded : Route

/** The profile's downloads that aren't whole yet, opened from [Downloaded]. */
@Serializable
data object DownloadQueue : Route

/**
 * Where a proxy is and how to sign in to it, opened for an answer by [rememberRouteResult] under
 * [key]; the values themselves never go on the back stack, the password is one of them.
 */
@Serializable
data class ProxyEditor(val key: String) : Route

/** Google's sign in page, the session it leaves is kept for YouTube. */
@Serializable
data object YouTubeSignIn : Route

/**
 * What [origin] found for [query], by kind. The screen opens on the [tab] of one of them.
 */
@Serializable
data class SearchResults(val query: String, val origin: Origin, val tab: Tab) : Route {

    override val arguments: Bundle
        get() = Bundle().apply {
            putString(QUERY, query)
            putString(ORIGIN, origin.name)
        }

    companion object {
        const val QUERY = "query"
        const val ORIGIN = "origin"
    }
}

/**
 * An album of the device's library. Opened from a card or a row, the album's screen shows the
 * [name] and [picture] it had there until it has its own, the same for every album screen.
 *
 * @param picture the address of the picture as the card loaded it
 */
@Serializable
data class LocalAlbum(
    val id: String,
    val name: String? = null,
    val picture: String? = null,
) : Route {

    override val arguments: Bundle get() = openedArguments(ID, id, name, picture)

    companion object {
        const val ID = "albumId"
    }
}

/**
 * An artist of the device's library. Opened from a card or a row, the artist's screen shows the
 * [name] and [picture] it had there until it has its own, the same for every artist screen.
 *
 * @param picture the address of the picture as the card loaded it
 */
@Serializable
data class LocalArtist(
    val id: String,
    val name: String? = null,
    val picture: String? = null,
) : Route {

    override val arguments: Bundle get() = openedArguments(ID, id, name, picture)

    companion object {
        const val ID = "artistId"
    }
}

/** The argument with the name an artist or album was shown with where it was opened. */
const val OPENED_NAME = "openedName"

/** The argument with the address of the picture an artist or album was shown with there. */
const val OPENED_PICTURE = "openedPicture"

/** What the screen of an artist or album reads: its id under [idKey], and what it was shown with. */
private fun openedArguments(idKey: String, id: String, name: String?, picture: String?): Bundle =
    Bundle().apply {
        putString(idKey, id)
        putString(OPENED_NAME, name)
        putString(OPENED_PICTURE, picture)
    }

@Serializable
data class LocalPlaylist(val id: Long) : Route {

    override val arguments: Bundle get() = Bundle().apply { putLong(ID, id) }

    companion object {
        const val ID = "playlistId"
    }
}

/**
 * An album of the OpenSubsonic server, fetched when it opens and never stored. Its screen reads
 * the album id from the same argument as [LocalAlbum].
 */
@Serializable
data class OpenSubsonicAlbum(
    val id: String,
    val name: String? = null,
    val picture: String? = null,
) : Route {

    override val arguments: Bundle get() = openedArguments(LocalAlbum.ID, id, name, picture)
}

/**
 * An artist of the OpenSubsonic server, fetched when they open and never stored.
 */
@Serializable
data class OpenSubsonicArtist(
    val id: String,
    val name: String? = null,
    val picture: String? = null,
) : Route {

    override val arguments: Bundle get() = openedArguments(LocalArtist.ID, id, name, picture)
}

/**
 * Every song of the OpenSubsonic artist [id] named [name], fetched when they open and never stored.
 */
@Serializable
data class OpenSubsonicArtistSongs(val id: String, val name: String?) : Route {

    override val arguments: Bundle
        get() = Bundle().apply {
            putString(LocalArtist.ID, id)
            putString(NAME, name)
        }

    companion object {
        const val NAME = "artistName"
    }
}

/**
 * A playlist of the OpenSubsonic server by its id there, fetched when it opens and never stored.
 */
@Serializable
data class OpenSubsonicPlaylist(val id: String) : Route {

    override val arguments: Bundle get() = Bundle().apply { putString(ID, id) }

    companion object {
        const val ID = "remotePlaylistId"
    }
}

/**
 * An album of YouTube, fetched when it opens and never stored. Its screen reads the album id from
 * the same argument as [LocalAlbum].
 */
@Serializable
data class YouTubeAlbum(
    val id: String,
    val name: String? = null,
    val picture: String? = null,
) : Route {

    override val arguments: Bundle get() = openedArguments(LocalAlbum.ID, id, name, picture)
}

/**
 * An artist of YouTube, fetched when they open and never stored.
 */
@Serializable
data class YouTubeArtist(
    val id: String,
    val name: String? = null,
    val picture: String? = null,
) : Route {

    override val arguments: Bundle get() = openedArguments(LocalArtist.ID, id, name, picture)
}

/**
 * A playlist of YouTube by its id there, fetched when it opens and never stored.
 */
@Serializable
data class YouTubePlaylist(val id: String) : Route {

    override val arguments: Bundle get() = Bundle().apply { putString(ID, id) }

    companion object {
        const val ID = "remotePlaylistId"
    }
}

/**
 * Every item of a row of YouTube titled [title], like all the singles of an artist, from the page
 * [browseId] with [params]. Fetched as it scrolls and never stored.
 */
@Serializable
data class YouTubeMore(val title: String, val browseId: String, val params: String?) : Route {

    override val arguments: Bundle
        get() = Bundle().apply {
            putString(TITLE, title)
            putString(BROWSE_ID, browseId)
            putString(PARAMS, params)
        }

    companion object {
        const val TITLE = "title"
        const val BROWSE_ID = "browseId"
        const val PARAMS = "params"
    }
}

/**
 * The screen with every item of a YouTube row titled [title]. A row whose items are a playlist,
 * like an artist's videos, opens that playlist.
 */
fun youTubeMoreRoute(title: String, more: YouTubeBrowse): Route =
    if (more.browseId.startsWith(PLAYLIST_PREFIX)) {
        YouTubePlaylist(
            more.browseId.removePrefix(
                PLAYLIST_PREFIX
            )
        )
    } else {
        YouTubeMore(title, more.browseId, more.params)
    }

/** Browse id of a YouTube playlist is this followed by the playlist's id. */
private const val PLAYLIST_PREFIX = "VL"

/**
 * The screen of an album, by where it comes from.
 */
fun albumRoute(item: MediaItem): Route {
    val name = item.mediaMetadata.title?.toString()
    val picture = item.mediaMetadata.artworkUri?.toString()
    return when (item.origin) {
        Origin.OPEN_SUBSONIC -> OpenSubsonicAlbum(item.mediaId, name, picture)
        Origin.YOUTUBE -> YouTubeAlbum(item.mediaId, name, picture)
        else -> LocalAlbum(item.mediaId, name, picture)
    }
}

/** The screen of the album [id] of [origin], like the album of a song. */
fun albumRoute(origin: Origin?, id: String): Route = when (origin) {
    Origin.OPEN_SUBSONIC -> OpenSubsonicAlbum(id)
    Origin.YOUTUBE -> YouTubeAlbum(id)
    else -> LocalAlbum(id)
}

/**
 * The screen of an artist, by where they come from. It shows the name and picture of [item] until
 * it has its own.
 */
fun artistRoute(item: MediaItem): Route {
    val name = item.mediaMetadata.title?.toString()
    val picture = item.mediaMetadata.artworkUri?.toString()
    return when (item.origin) {
        Origin.OPEN_SUBSONIC -> OpenSubsonicArtist(item.mediaId, name, picture)
        Origin.YOUTUBE -> YouTubeArtist(item.mediaId, name, picture)
        else -> LocalArtist(item.mediaId, name, picture)
    }
}

/** The screen of the artist [id] of [origin], like an artist of a song. */
fun artistRoute(origin: Origin?, id: String): Route = when (origin) {
    Origin.OPEN_SUBSONIC -> OpenSubsonicArtist(id)
    Origin.YOUTUBE -> YouTubeArtist(id)
    else -> LocalArtist(id)
}

/**
 * The screen of a playlist, by where it comes from. A playlist of an external source opens by its
 * id there.
 */
fun playlistRoute(item: MediaItem): Route = when (item.origin) {
    Origin.OPEN_SUBSONIC -> OpenSubsonicPlaylist(item.remoteId ?: item.mediaId)
    Origin.YOUTUBE -> YouTubePlaylist(item.remoteId ?: item.mediaId)
    else -> LocalPlaylist(item.mediaId.toLong())
}

/**
 * The screen of an item of a [tab], `null` for a song which has none.
 */
fun itemRoute(tab: Tab, item: MediaItem): Route? = when (tab) {
    Tab.SONGS -> null
    Tab.ALBUMS -> albumRoute(item)
    Tab.ARTISTS -> artistRoute(item)
    Tab.PLAYLISTS -> playlistRoute(item)
}

/**
 * The destinations that share the library bottom bar, in the order they appear on it.
 */
enum class Tab(
    val route: Route,
    @param:StringRes val title: Int,
    @param:PluralsRes val noun: Int,
    @param:StringRes val noResults: Int,
    @param:DrawableRes val icon: Int,
) {
    SONGS(
        Songs, R.string.tab_songs, R.plurals.unit_song,
        R.string.screen_search_results_status_none_songs, R.drawable.music_note
    ),
    ALBUMS(
        Albums, R.string.tab_albums, R.plurals.unit_album,
        R.string.screen_search_results_status_none_albums, R.drawable.album
    ),
    ARTISTS(
        Artists, R.string.tab_artists, R.plurals.unit_artist,
        R.string.screen_search_results_status_none_artists, R.drawable.artist
    ),
    PLAYLISTS(
        Playlists, R.string.tab_playlists, R.plurals.unit_playlist,
        R.string.screen_search_results_status_none_playlists, R.drawable.queue_music
    );

    companion object {

        /** The tab that owns [route], or `null` for screens outside the library. */
        fun of(route: Route?): Tab? = entries.firstOrNull { it.route == route }
    }
}
