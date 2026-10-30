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

import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.database.repository.StatisticsRepository
import me.knighthat.kreate3.database.repository.UserAlbumRepository
import me.knighthat.kreate3.database.repository.UserArtistRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.playback.RADIO_SIZE
import me.knighthat.kreate3.playback.Radio
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.provider.youtube.YouTubeSearchFilter
import me.knighthat.kreate3.source.withoutDisliked
import me.knighthat.kreate3.source.youtube.CARD_ARTWORK
import me.knighthat.kreate3.source.youtube.artwork
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.navigation.Tab
import me.knighthat.kreate3.source.youtube.toMediaItem as toYouTubeMediaItem

private const val TAG = "VoiceSearch"

/** Matches of a kind a server is asked for, the best ones come first. */
private const val SERVER_RESULTS = 10

// MediaStore's names for these are deprecated with its playlists, the Assistant still sends them
private const val PLAYLIST_FOCUS = "vnd.android.cursor.item/playlist"
private const val EXTRA_PLAYLIST = "android.intent.extra.playlist"

/** Matches tried in turn when the best one has nothing to play, like an empty album. */
private const val TRIED_MATCHES = 3

/**
 * Finds what to play for a voice request, "play … on Kreate", in Android Auto or from the
 * Assistant. Nothing found is stored.
 */
internal class VoiceSearch(
    private val songs: UserSongRepository,
    private val albums: UserAlbumRepository,
    private val artists: UserArtistRepository,
    private val playlists: PlaylistRepository,
    private val statistics: StatisticsRepository,
    private val openSubsonic: OpenSubsonicProvider,
    private val youTube: YouTubeProvider,
    private val radio: Radio,
    private val library: AutoLibrary,
) {

    /**
     * The songs to play for [query], the first one first. The origins of
     * [Preferences.AUTO_VOICE_SEARCH_ORDER] that aren't skipped are searched one after the other
     * until one finds something, its best match plays: a song with songs like it after it, or
     * every song of an album, artist or playlist.
     *
     * @param extras what the Assistant understood, see [VoiceRequest.of]
     * @return `null` when nothing was found
     */
    suspend fun queue(query: String, extras: Bundle?): List<MediaItem>? =
        withContext(Dispatchers.Default) {
            Preferences.awaitLoaded()
            val request = VoiceRequest.of(query, extras)
            val origins = voiceSearchOrigins(
                Preferences.AUTO_VOICE_SEARCH_ORDER.value,
                Preferences.AUTO_VOICE_SEARCH_SKIPPED.value,
            )
            for ((origin, searched) in origins) {
                if (!searched) continue
                val found = try {
                    queueFrom(origin, request)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't search $origin for ${request.query}", e)
                    null
                }
                if (!found.isNullOrEmpty()) return@withContext found
            }
            null
        }

    /**
     * Songs to play when nothing in particular is asked and nothing is left to resume: the liked
     * song listened to the longest, else the song listened to the longest, else a song of the
     * library at random, with songs like it after it.
     *
     * @return `null` while the library is empty
     */
    suspend fun anything(): List<MediaItem>? = withContext(Dispatchers.Default) {
        val seed = (statistics.mostListened(likedOnly = true) ?: statistics.mostListened())
            ?.toMediaItem()
            ?: songs.list().shuffled().firstNotNullOfOrNull { it.toMediaItem() }
            ?: return@withContext null
        withSimilar(seed)
    }

    /** The songs of the best match [origin] has for [request], `null` when it has none. */
    private suspend fun queueFrom(origin: Origin, request: VoiceRequest): List<MediaItem>? {
        val disliked = songs.listDislikedIds()
        val matches = coroutineScope {
            request.tabs.map { tab ->
                async {
                    val term = request.termFor(tab)
                    val found = find(origin, tab, term)
                        .let { if (tab == Tab.SONGS) it.withoutDisliked(disliked) else it }
                    found.mapIndexed { rank, item ->
                        Match(tab, item, request.scoreOf(tab, item), rank)
                    }
                }
            }.awaitAll().flatten()
        }
        return matches.sortedWith(Match.BEST_FIRST)
            .take(TRIED_MATCHES)
            .firstNotNullOfOrNull { match -> queueOf(match).takeIf { it.isNotEmpty() } }
    }

    /** The items of [tab] that [origin] finds for [term], the best ones first. */
    private suspend fun find(origin: Origin, tab: Tab, term: String): List<MediaItem> =
        when (origin) {
            // Like the app's search, "on device" searches the whole library
            Origin.LOCAL -> when (tab) {
                Tab.SONGS -> songs.listSearch(term).mapNotNull { it.toMediaItem() }
                Tab.ALBUMS -> albums.listSearch(term).map { it.toMediaItem() }
                Tab.ARTISTS -> artists.listSearch(term).map { it.toMediaItem() }
                Tab.PLAYLISTS -> playlists.listSearch(term).map { it.toMediaItem() }
            }

            Origin.YOUTUBE -> when (tab) {
                Tab.SONGS -> searchYouTube(term, YouTubeSearchFilter.SONGS)
                Tab.ALBUMS -> searchYouTube(term, YouTubeSearchFilter.ALBUMS)
                Tab.ARTISTS -> searchYouTube(term, YouTubeSearchFilter.ARTISTS)
                // The user's own first, signed in
                Tab.PLAYLISTS -> youTube.libraryPlaylists().orEmpty()
                    .filter { it.name.contains(term, ignoreCase = true) }
                    .map { it.toLiveMediaItem(youTube.artwork(it.thumbnail, CARD_ARTWORK)) } +
                        searchYouTube(term, YouTubeSearchFilter.FEATURED_PLAYLISTS)
            }

            // Nothing while the server is off or not set up, see OPEN_SUBSONIC_ENABLED
            Origin.OPEN_SUBSONIC -> if (tab == Tab.PLAYLISTS)
                openSubsonic.libraryPlaylists().orEmpty()
                    .filter { it.name.contains(term, ignoreCase = true) }
                    .map { it.toLiveMediaItem() }
            else {
                val found = openSubsonic.searchLibrary(
                    term,
                    songs = if (tab == Tab.SONGS) SERVER_RESULTS else 0,
                    albums = if (tab == Tab.ALBUMS) SERVER_RESULTS else 0,
                    artists = if (tab == Tab.ARTISTS) SERVER_RESULTS else 0,
                )
                when (tab) {
                    Tab.SONGS -> found?.songs?.map { it.toLiveMediaItem() }
                    Tab.ALBUMS -> found?.albums?.map { it.item.toLiveMediaItem() }
                    else -> found?.artists?.map { it.item.toLiveMediaItem() }
                }.orEmpty()
            }
        }

    /** The first page YouTube gives for [term] with [filter]. */
    private suspend fun searchYouTube(term: String, filter: YouTubeSearchFilter): List<MediaItem> =
        youTube.search(term, filter).items.map { it.toYouTubeMediaItem(youTube) }

    /** What [match] plays: a song with songs like it, or the songs of a collection. */
    private suspend fun queueOf(match: Match): List<MediaItem> =
        if (match.tab == Tab.SONGS) {
            if (match.item.mediaMetadata.isPlayable == true) withSimilar(match.item)
            else emptyList()
        } else
            library.songsOf(match.tab, match.item)

    /** [seed] followed by up to [RADIO_SIZE] songs like it, as the app's radio. */
    private suspend fun withSimilar(seed: MediaItem): List<MediaItem> {
        val similar = try {
            radio.after(seed, RADIO_SIZE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't find songs like ${seed.mediaId}", e)
            emptyList()
        }
        return listOf(seed) + similar
    }

    /**
     * An [item] of [tab] found for a request.
     *
     * @param score how well it matches, see [VoiceRequest.scoreOf]
     * @param rank where the search put it among its kind, `0` for the first
     */
    private class Match(val tab: Tab, val item: MediaItem, val score: Int, val rank: Int) {

        companion object {
            /** Highest score first, then the kinds in [VoiceRequest.tabs]' order, then rank. */
            val BEST_FIRST = compareByDescending<Match> { it.score }
                .thenBy { VoiceRequest.ALL_TABS.indexOf(it.tab) }
                .thenBy { it.rank }
        }
    }
}

/**
 * A voice request: what was said, and what the Assistant understood of it when it could tell.
 *
 * @property focus the kind asked for, `null` when it could be anything
 */
internal class VoiceRequest(
    val query: String,
    val focus: Tab? = null,
    private val title: String? = null,
    private val album: String? = null,
    private val artist: String? = null,
    private val playlist: String? = null,
) {

    /** The kinds searched, the one asked for or all of them. */
    val tabs: List<Tab> get() = focus?.let(::listOf) ?: ALL_TABS

    /** What to search the items of [tab] for: their name if the Assistant gave it, else all said. */
    fun termFor(tab: Tab): String = when (tab) {
        Tab.SONGS -> title
        Tab.ALBUMS -> album
        Tab.ARTISTS -> artist
        Tab.PLAYLISTS -> playlist
    }?.trim()?.takeIf { it.isNotEmpty() } ?: query

    /**
     * How well [item] of [tab] matches, the higher the better: its name [nameScore] against
     * [termFor] it, and a song gets one more when it is by the artist the Assistant heard.
     */
    fun scoreOf(tab: Tab, item: MediaItem): Int {
        val metadata = item.mediaMetadata
        val byArtist = tab == Tab.SONGS && !artist.isNullOrBlank() &&
                (metadata.artist ?: metadata.subtitle)?.contains(
                    artist.trim(),
                    ignoreCase = true
                ) == true
        return nameScore(metadata.title, termFor(tab)) + if (byArtist) 1 else 0
    }

    companion object {

        /** Every kind, in the order a match of each wins over the others when equal. */
        val ALL_TABS = listOf(Tab.SONGS, Tab.ARTISTS, Tab.ALBUMS, Tab.PLAYLISTS)

        /**
         * The request for [query], with what [extras] (the `MediaStore.EXTRA_MEDIA_*` the
         * Assistant sends) says about it. A genre or anything else is searched as a whole.
         */
        fun of(query: String, extras: Bundle?): VoiceRequest {
            fun text(key: String) = extras?.getString(key)
            return VoiceRequest(
                query = query.trim(),
                focus = when (text(MediaStore.EXTRA_MEDIA_FOCUS)) {
                    MediaStore.Audio.Media.ENTRY_CONTENT_TYPE -> Tab.SONGS
                    MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> Tab.ALBUMS
                    MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> Tab.ARTISTS
                    PLAYLIST_FOCUS -> Tab.PLAYLISTS
                    else -> null
                },
                title = text(MediaStore.EXTRA_MEDIA_TITLE),
                album = text(MediaStore.EXTRA_MEDIA_ALBUM),
                artist = text(MediaStore.EXTRA_MEDIA_ARTIST),
                playlist = text(EXTRA_PLAYLIST),
            )
        }
    }
}

/**
 * How well [name] matches [term]: `3` the same, `2` starting with it, `1` holding it, `0` none
 * of these. Case is ignored, speech has none; the results stay as the search gave them.
 */
internal fun nameScore(name: CharSequence?, term: String): Int {
    val text = name?.toString()?.trim() ?: return 0
    return when {
        text.equals(term, ignoreCase = true) -> 3
        text.startsWith(term, ignoreCase = true) -> 2
        text.contains(term, ignoreCase = true) -> 1
        else -> 0
    }
}

/**
 * The origins voice search goes through: the ones of [order] by name, then any missing there,
 * each with whether it is searched (not in [skipped]). Unknown names are left out.
 */
internal fun voiceSearchOrigins(
    order: List<String>,
    skipped: Set<String>
): List<Pair<Origin, Boolean>> {
    val listed = order.mapNotNull { name -> Origin.entries.firstOrNull { it.name == name } }
        .distinct()
    return (listed + Origin.entries.filterNot { it in listed }).map { it to (it.name !in skipped) }
}
