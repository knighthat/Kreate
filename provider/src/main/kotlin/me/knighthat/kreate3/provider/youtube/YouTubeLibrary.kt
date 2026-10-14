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
package me.knighthat.kreate3.provider.youtube

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.repository.RemoteCatalogRepository
import me.knighthat.kreate3.database.repository.RemoteItem
import me.knighthat.kreate3.database.repository.RemoteSong
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.LibrarySection
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Brings the library of the user signed in to YouTube into the database, one [LibrarySection] at
 * a time, so their liked songs, saved albums, subscribed artists and playlists show among the
 * others. Nothing happens while nobody is signed in. Other songs aren't brought, they are stored
 * when played.
 *
 * What YouTube has wins: a YouTube song liked there is liked here, and one liked here but not
 * there loses its like. YouTube doesn't tell when an item was saved or liked, so an item new to
 * the library is saved now, in the order of the library, and keeps that time afterwards.
 *
 * A section is fetched again at most every few minutes, and never while the user turned its
 * syncing off. The work runs on [scope], the app's long lived scope, so leaving the screen doesn't
 * throw away a library already downloaded.
 *
 * @param syncs whether a section is brought in, read before each fetch and again before storing
 * it
 */
class YouTubeLibrary(
    private val provider: YouTubeProvider,
    private val repository: RemoteCatalogRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val syncs: (LibrarySection) -> Boolean = { it.youTubeSyncSetting.value },
) {

    /** When each section of each session was last brought up to date, or is being now. */
    private val refreshed = ConcurrentHashMap<Pair<String, LibrarySection>, Instant>()

    /**
     * Held while storing, so a section taken out is not put back by a fetch that was running
     * already.
     */
    private val storing = Mutex()

    /**
     * Brings [section] up to date unless it was done recently or its syncing is off. A failure,
     * like a session that ended, leaves what is stored as it was and lets the next call try again.
     */
    fun refresh(section: LibrarySection) {
        scope.launch {
            if (!syncs(section)) return@launch
            val session = provider.sessionId() ?: return@launch
            val key = session to section
            val now = clock.now()

            // Claims the section, so a second call while this one runs does nothing
            val last = refreshed[key]
            if (last != null && now - last < MIN_INTERVAL) return@launch
            if (last == null && refreshed.putIfAbsent(key, now) != null) return@launch
            if (last != null && !refreshed.replace(key, last, now)) return@launch

            try {
                if (!sync(session, section)) refreshed.remove(key)
            } catch (e: CancellationException) {
                refreshed.remove(key)
                throw e
            } catch (e: Exception) {
                refreshed.remove(key)
                Log.w(TAG, "Couldn't bring $section of YouTube up to date", e)
            }
        }
    }

    /** Brings every section up to date now, like right after signing in. */
    fun refreshAll() {
        refreshed.clear()
        LibrarySection.entries.forEach(::refresh)
    }

    /**
     * Takes the YouTube library out of the user's library, like after signing out. The songs lose
     * their likes, the albums and artists stay in the catalog, the playlists are deleted.
     */
    suspend fun clear() = LibrarySection.entries.forEach { clear(it) }

    /**
     * Takes [section] of the YouTube library out of the user's library, like after its syncing is
     * turned off. The songs lose their likes and stay in the user's library, the albums and
     * artists stay in the catalog, the playlists are deleted.
     */
    suspend fun clear(section: LibrarySection) {
        refreshed.keys.removeAll { it.second == section }
        storing.withLock {
            when (section) {
                LibrarySection.SONGS -> repository.syncLikedSongs(Origin.YOUTUBE, emptyList())
                LibrarySection.ALBUMS -> repository.syncAlbums(Origin.YOUTUBE, emptyList())
                LibrarySection.ARTISTS -> repository.syncArtists(Origin.YOUTUBE, emptyList())
                LibrarySection.PLAYLISTS -> repository.syncPlaylists(Origin.YOUTUBE, emptyList())
            }
        }
    }

    /**
     * @return whether the section was stored, `false` when nobody is signed in, or the session
     * changed or the section's syncing was turned off meanwhile
     */
    private suspend fun sync(session: String, section: LibrarySection): Boolean =
        when (section) {
            LibrarySection.SONGS -> store(session, section, provider.likedSongs()) { liked ->
                val songs = liked.items.likedInOrder()
                // More are liked than were fetched, only the likes fetched are given then
                if (liked.continuation == null) repository.syncLikedSongs(Origin.YOUTUBE, songs)
                else repository.syncSongs(Origin.YOUTUBE, songs)
            }

            LibrarySection.ALBUMS -> store(session, section, provider.libraryAlbums()) {
                repository.syncAlbums(Origin.YOUTUBE, it.savedInOrder())
            }

            LibrarySection.ARTISTS -> store(session, section, provider.libraryArtists()) {
                repository.syncArtists(Origin.YOUTUBE, it.savedInOrder())
            }

            LibrarySection.PLAYLISTS -> store(session, section, provider.libraryPlaylists()) {
                repository.syncPlaylists(Origin.YOUTUBE, it)
            }
        }

    /**
     * Stores [items] fetched in [session] with [save], unless signing out, switching channel or
     * turning the syncing of [section] off happened meanwhile.
     *
     * @return whether they were stored
     */
    private suspend fun <T> store(
        session: String,
        section: LibrarySection,
        items: T?,
        save: suspend (T) -> Unit,
    ): Boolean {
        items ?: return false
        return storing.withLock {
            if (provider.sessionId() != session || !syncs(section)) return@withLock false
            save(items)
            true
        }
    }

    /**
     * Each item saved now, a millisecond apart so the first one, the most recent of the library,
     * is the newest.
     */
    private fun <T> List<T>.savedInOrder(): List<RemoteItem<T>> {
        val now = clock.now()
        return mapIndexed { index, item -> RemoteItem(item, now - index.milliseconds) }
    }

    /**
     * Each song liked at the time YouTube gives, or now a millisecond apart so the first one, the
     * most recently liked, is the newest.
     */
    private fun List<RemoteSong>.likedInOrder(): List<RemoteSong> {
        val now = clock.now()
        return mapIndexed { index, song ->
            song.copy(
                starredAt = song.starredAt ?: (now - index.milliseconds)
            )
        }
    }

    private companion object {

        const val TAG = "YouTubeLibrary"

        val MIN_INTERVAL = 5.minutes
    }
}

/** The setting that turns syncing this section of the YouTube library on and off. */
val LibrarySection.youTubeSyncSetting
    get() = when (this) {
        LibrarySection.SONGS -> Preferences.YOUTUBE_SYNC_SONGS
        LibrarySection.ALBUMS -> Preferences.YOUTUBE_SYNC_ALBUMS
        LibrarySection.ARTISTS -> Preferences.YOUTUBE_SYNC_ARTISTS
        LibrarySection.PLAYLISTS -> Preferences.YOUTUBE_SYNC_PLAYLISTS
    }
