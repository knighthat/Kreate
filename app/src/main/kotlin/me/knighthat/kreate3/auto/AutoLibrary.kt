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

package me.knighthat.kreate3.auto

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.AutoTapAction
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.database.repository.RemoteSong
import me.knighthat.kreate3.database.repository.UserAlbumRepository
import me.knighthat.kreate3.database.repository.UserArtistRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.playback.RADIO_SIZE
import me.knighthat.kreate3.playback.Radio
import me.knighthat.kreate3.preferences.Preference
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.liveAlbums
import me.knighthat.kreate3.source.liveArtists
import me.knighthat.kreate3.source.livePlaylists
import me.knighthat.kreate3.source.youtube.SONG_ARTWORK
import me.knighthat.kreate3.source.youtube.artwork
import me.knighthat.kreate3.ui.component.origin
import me.knighthat.kreate3.ui.component.remoteId
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.navigation.Tab

private const val TAG = "AutoLibrary"

/**
 * What Android Auto shows of the library: a tab for each tab of the home screens, with the same
 * items in the same order, the origins hidden there hidden here too. Albums, artists and playlists
 * open or play as their `AUTO_*_TAP` setting says. Like the app, it reads the database and asks
 * the external sources live, nothing is stored.
 *
 * Every function is main-safe, the lists are built on [Dispatchers.Default].
 */
internal class AutoLibrary(
    private val context: Context,
    private val songs: UserSongRepository,
    private val albums: UserAlbumRepository,
    private val artists: UserArtistRepository,
    private val playlists: PlaylistRepository,
    private val openSubsonic: OpenSubsonicProvider,
    private val youTube: YouTubeProvider,
    private val radio: Radio,
) {

    /** The top of the library, its children are the tabs. */
    fun root(): MediaItem = folder(
        id = AutoNode.Root.id,
        title = context.getString(R.string.app_name),
        type = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
    )

    /**
     * The item of [mediaId] when it is the root or a tab, `null` for the others: they are known
     * only from the list they are in.
     */
    fun item(mediaId: String): MediaItem? = when (val node = AutoNode.parse(mediaId)) {
        AutoNode.Root -> root()
        is AutoNode.Section -> sectionItem(node.tab)
        else -> null
    }

    /**
     * What [parentId] holds, `null` when it isn't something that holds items.
     *
     * @throws me.knighthat.kreate3.provider.opensubsonic.ServerException when the server can't
     * give an item of its own
     * @throws me.knighthat.kreate3.provider.youtube.YouTubeException when YouTube can't
     */
    suspend fun children(parentId: String): List<MediaItem>? = withContext(Dispatchers.Default) {
        Preferences.awaitLoaded()
        when (val node = AutoNode.parse(parentId)) {
            AutoNode.Root -> Tab.entries.map(::sectionItem)
            is AutoNode.Section -> sectionChildren(node)
            is AutoNode.Collection -> songsOf(node).map { songItem(node, it) }
            else -> null
        }?.distinctBy { it.mediaId }
    }

    /**
     * The songs to play when [mediaId] is tapped, the first one first: an album, artist or
     * playlist that plays, or a song of a list, which plays with the songs below it. A song of
     * the Songs tab plays with songs like it instead while `HOME_SONGS_TAP_RADIO` is on, as in
     * the app.
     *
     * @return only the songs that can be played, `null` when [mediaId] isn't something to play
     * @throws me.knighthat.kreate3.provider.opensubsonic.ServerException like [children]
     * @throws me.knighthat.kreate3.provider.youtube.YouTubeException like [children]
     */
    suspend fun queue(mediaId: String): List<MediaItem>? = withContext(Dispatchers.Default) {
        Preferences.awaitLoaded()
        val queue = when (val node = AutoNode.parse(mediaId)) {
            is AutoNode.Collection -> {
                val found = songsOf(node)
                if (tapAction(node.tab) == AutoTapAction.PLAY_RANDOMLY) found.shuffled() else found
            }

            is AutoNode.Song -> {
                val list = node.list
                when {
                    list is AutoNode.Collection -> songsOf(list).from(node.songId)
                    Preferences.HOME_SONGS_TAP_RADIO.value -> radioOf(node.songId)
                    else -> librarySongs().from(node.songId)
                }
            }

            else -> return@withContext null
        }
        queue.filter { it.mediaMetadata.isPlayable == true }
    }

    private suspend fun sectionChildren(section: AutoNode.Section): List<MediaItem> =
        when (val tab = section.tab) {
            Tab.SONGS -> librarySongs().map { songItem(section, it) }

            Tab.ALBUMS -> collections(tab, Preferences.HOME_ALBUMS_ORIGINS, { origins ->
                albums.list(origins = origins).map { it.toMediaItem() }
            }) { liveAlbums(openSubsonic, youTube) }

            Tab.ARTISTS -> collections(tab, Preferences.HOME_ARTISTS_ORIGINS, { origins ->
                artists.list(origins = origins).map { it.toMediaItem() }
            }) { liveArtists(openSubsonic, youTube) }

            Tab.PLAYLISTS -> collections(tab, Preferences.HOME_PLAYLISTS_ORIGINS, { origins ->
                playlists.list(origins = origins).map { it.toMediaItem() }
            }) { livePlaylists(openSubsonic, youTube) }
        }

    /**
     * The items of the database and then the ones of the sources, of the origins [filter] shows,
     * like a home tab.
     */
    private suspend fun collections(
        tab: Tab,
        filter: Preference<Set<String>>,
        stored: suspend (Set<Origin>) -> List<MediaItem>,
        live: suspend () -> List<MediaItem>,
    ): List<MediaItem> = coroutineScope {
        val origins = filter.origins()
        // The sources take the longest, asked while the database is read
        val fetched = async { live() }
        val shown = stored(origins) + fetched.await()
            .filter { origins.isEmpty() || it.origin in origins }
        shown.map { collectionItem(tab, it) }
    }

    /** The songs of the Songs tab. */
    private suspend fun librarySongs(): List<MediaItem> =
        songs.list(origins = Preferences.HOME_SONGS_ORIGINS.origins())
            .mapNotNull { it.toMediaItem() }

    /** The songs of [collection], as its screen in the app shows them. */
    private suspend fun songsOf(collection: AutoNode.Collection): List<MediaItem> {
        val key = collection.key
        val found = when (collection.tab) {
            Tab.ALBUMS -> when (collection.origin) {
                Origin.LOCAL -> songs.listOfAlbum(key).mapNotNull { it.toMediaItem() }
                Origin.OPEN_SUBSONIC -> openSubsonic.albumWithSongs(key)?.second
                    ?.map { it.toLiveMediaItem() }.orEmpty()

                Origin.YOUTUBE -> youTube.albumPage(key)?.let { page ->
                    page.songs.map { it.toYouTubeItem(page.album.thumbnail) }
                }.orEmpty()
            }

            Tab.ARTISTS -> when (collection.origin) {
                Origin.LOCAL -> songs.listOfArtist(key).mapNotNull { it.toMediaItem() }
                Origin.OPEN_SUBSONIC -> openSubsonic.artistWithSongs(key)?.songs
                    ?.map { it.toLiveMediaItem() }.orEmpty()

                // Like the artist's screen, the top songs
                Origin.YOUTUBE -> youTube.artistPage(key)?.topSongs
                    ?.map { it.toYouTubeItem() }.orEmpty()
            }

            Tab.PLAYLISTS -> when (collection.origin) {
                Origin.LOCAL -> key.toLongOrNull()
                    ?.let { songs.listOfPlaylist(it) }
                    ?.mapNotNull { it.toMediaItem() }.orEmpty()

                Origin.OPEN_SUBSONIC -> openSubsonic.playlistWithSongs(key)?.second
                    ?.map { it.toLiveMediaItem() }.orEmpty()

                Origin.YOUTUBE -> youTubePlaylistSongs(key).map { it.toYouTubeItem() }
            }

            Tab.SONGS -> emptyList()
        }
        return found.distinctBy { it.mediaId }
    }

    /**
     * Every song of the YouTube playlist, all of its parts: Auto has no "load more", and a
     * queue started from it should hold the whole playlist.
     */
    private suspend fun youTubePlaylistSongs(playlistId: String): List<RemoteSong> {
        val page = youTube.playlistPage(playlistId) ?: return emptyList()
        val found = page.songs.toMutableList()
        val asked = HashSet<String>()
        var next = page.continuation
        // A part that points back to one already asked for would never end
        while (next != null && asked.add(next)) {
            val part = youTube.playlistSongs(next)
            found += part.items
            next = part.continuation
        }
        return found
    }

    /** [songId] followed by up to [RADIO_SIZE] songs like it, as the app's radio. */
    private suspend fun radioOf(songId: String): List<MediaItem> {
        // Not `find`, it gives nothing until the profile's user is known
        val seed = librarySongs().firstOrNull { it.mediaId == songId } ?: return emptyList()
        val similar = try {
            radio.after(seed, RADIO_SIZE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't find songs like $songId", e)
            emptyList()
        }
        return listOf(seed) + similar
    }

    private fun RemoteSong.toYouTubeItem(albumThumbnail: Uri? = album?.thumbnail): MediaItem =
        toLiveMediaItem(youTube.artwork(song.thumbnail ?: albumThumbnail, SONG_ARTWORK))

    private fun tapAction(tab: Tab): AutoTapAction = when (tab) {
        Tab.ALBUMS -> Preferences.AUTO_ALBUM_TAP.value
        Tab.ARTISTS -> Preferences.AUTO_ARTIST_TAP.value
        Tab.PLAYLISTS -> Preferences.AUTO_PLAYLIST_TAP.value
        Tab.SONGS -> AutoTapAction.PLAY_FROM_START
    }

    @OptIn(UnstableApi::class)
    private fun sectionItem(tab: Tab): MediaItem = folder(
        id = AutoNode.Section(tab).id,
        title = tab.label,
        type = when (tab) {
            Tab.SONGS -> MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
            Tab.ALBUMS -> MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
            Tab.ARTISTS -> MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
            Tab.PLAYLISTS -> MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
        },
        icon = resourceUri(tab.icon),
        // Grids of covers like the home screens, lists of rows for the others
        style = when (tab) {
            Tab.ALBUMS, Tab.ARTISTS -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
            Tab.SONGS, Tab.PLAYLISTS -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        },
    )

    /**
     * An album, artist or playlist of [tab] that opens or plays, as the setting of the tab says.
     * Its id says where its songs are found, like the screen it opens in the app.
     */
    private fun collectionItem(tab: Tab, item: MediaItem): MediaItem {
        val origin = item.origin ?: Origin.LOCAL
        // A playlist of a source is found by its id there, one of the app by its database id
        val key = if (tab == Tab.PLAYLISTS && origin != Origin.LOCAL)
            item.remoteId ?: item.mediaId
        else item.mediaId
        val opens = tapAction(tab) == AutoTapAction.OPEN

        return item.buildUpon()
            .setMediaId(AutoNode.Collection(tab, origin, key).id)
            .setMediaMetadata(
                item.mediaMetadata.buildUpon()
                    .setArtworkUri(context.autoArtworkUri(item.mediaMetadata.artworkUri))
                    .setIsBrowsable(opens)
                    .setIsPlayable(!opens)
                    .build()
            )
            .build()
    }

    /**
     * [song] as a row of [list]. Auto's rows have no line of their own for the album, the
     * subtitle holds it after the artists, except what [list] already says: the album of an
     * album's songs, the artists of an artist's. The queue gets the song itself, see [queue].
     */
    private fun songItem(list: AutoNode, song: MediaItem): MediaItem {
        val metadata = song.mediaMetadata
        val tab = (list as? AutoNode.Collection)?.tab
        val artists = (metadata.artist ?: metadata.subtitle).takeIf { tab != Tab.ARTISTS }
        val album = metadata.albumTitle.takeIf { tab != Tab.ALBUMS }
        return song.buildUpon()
            .setMediaId(AutoNode.Song(list, song.mediaId).id)
            .setMediaMetadata(
                metadata.buildUpon()
                    .setSubtitle(songSubtitle(artists, album))
                    .setArtworkUri(context.autoArtworkUri(metadata.artworkUri))
                    .build()
            )
            .build()
    }

    /**
     * An item that holds others.
     *
     * @param style how Auto lays out its children, one of the `EXTRAS_VALUE_CONTENT_STYLE_*`,
     * Auto's default when `null`
     */
    @OptIn(UnstableApi::class)
    private fun folder(
        id: String,
        title: String,
        type: Int,
        icon: Uri? = null,
        style: Int? = null,
    ): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtworkUri(icon)
                .setMediaType(type)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setExtras(style?.let {
                    bundleOf(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE to it,
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE to it,
                    )
                })
                .build()
        )
        .build()

    /** Address of a drawable of the app, the only kind of icon Auto shows for a tab. */
    private fun resourceUri(@DrawableRes id: Int): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.packageName)
        .appendPath(context.resources.getResourceTypeName(id))
        .appendPath(context.resources.getResourceEntryName(id))
        .build()
}

/** The origins the home tab's filter shows, every origin when empty. */
private fun Preference<Set<String>>.origins(): Set<Origin> =
    value.let { names -> Origin.entries.filterTo(mutableSetOf()) { it.name in names } }

/**
 * Subtitle of a song in Auto, `{artists} • {album}` like YouTube Music, only the part that is
 * known (or shown) without the dot when the other isn't. `null` when neither is.
 */
internal fun songSubtitle(artists: CharSequence?, album: CharSequence?): String? =
    listOfNotNull(artists, album)
        .map { it.toString().trim() }
        .filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" • ")

/** The songs from [songId] on, empty when it isn't in the list. */
private fun List<MediaItem>.from(songId: String): List<MediaItem> =
    dropWhile { it.mediaId != songId }
