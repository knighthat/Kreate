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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.repository.RemoteCatalogRepository
import me.knighthat.kreate3.database.repository.UserAlbumRepository
import me.knighthat.kreate3.database.repository.UserArtistRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.LibrarySection
import me.knighthat.kreate3.provider.ReactionSender
import me.knighthat.kreate3.provider.isDislike
import me.knighthat.kreate3.provider.isLike
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Keeps the songs the signed in user liked on YouTube liked in their library, and the likes and
 * dislikes given in the app on YouTube. Liked songs are the only part of the YouTube library that
 * is added to the database, its albums, artists and playlists are fetched each time they show. The
 * albums and artists already stored take their bookmark and follow from the library, see
 * [UserAlbumRepository.syncBookmarks] and [UserArtistRepository.syncFollows]. Signing out, or
 * turning the account or the syncing off, only stops the syncing, the likes, bookmarks and follows
 * stay as they are.
 *
 * @param syncsArtists whether the follows of stored artists are synced, [Preferences.YOUTUBE_SYNC_ARTISTS]
 * @param syncsAlbums whether the bookmarks of stored albums are synced, [Preferences.YOUTUBE_SYNC_ALBUMS]
 * @param syncs whether the liked songs are synced, [Preferences.YOUTUBE_SYNC_SONGS]
 */
class YouTubeLibrary(
    private val provider: YouTubeProvider,
    private val repository: RemoteCatalogRepository,
    userSongs: UserSongRepository,
    private val userAlbums: UserAlbumRepository,
    private val userArtists: UserArtistRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val syncsAlbums: () -> Boolean = { LibrarySection.ALBUMS.youTubeSyncSetting.value },
    private val syncsArtists: () -> Boolean = { LibrarySection.ARTISTS.youTubeSyncSetting.value },
    private val syncs: () -> Boolean = { LibrarySection.SONGS.youTubeSyncSetting.value },
) {

    /** When the liked songs of each session were last brought up to date, or are being now. */
    private val refreshed = ConcurrentHashMap<String, Instant>()

    private val sender = ReactionSender(userSongs)

    /**
     * Brings the liked songs up to date unless it was done recently or their syncing is off. A
     * failure, like a session that ended, leaves what is stored as it was and lets the next call
     * try again.
     *
     * @return the work, to wait for it
     */
    fun refresh(): Job =
        scope.launch {
            if (!syncs() && !syncsAlbums() && !syncsArtists()) return@launch
            val session = provider.sessionId() ?: return@launch
            val now = clock.now()

            // Claims the session, so a second call while this one runs does nothing
            val last = refreshed[session]
            if (last != null && now - last < MIN_INTERVAL) return@launch
            if (last == null && refreshed.putIfAbsent(session, now) != null) return@launch
            if (last != null && !refreshed.replace(session, last, now)) return@launch

            try {
                val stored = !syncs() || sync(session)
                syncAlbums(session)
                syncArtists(session)
                if (!stored) refreshed.remove(session)
            } catch (e: CancellationException) {
                refreshed.remove(session)
                throw e
            } catch (e: Exception) {
                refreshed.remove(session)
                Logger.w(e, TAG) { "Couldn't bring the liked songs of YouTube up to date" }
            }
        }

    /** Brings the liked songs up to date now, like right after signing in. */
    fun refreshNow(): Job {
        refreshed.clear()
        return refresh()
    }

    /**
     * Changes the user's reaction to [songId], a song of YouTube, to what [likedAt] makes of the
     * one stored, see `UserSong.likedAt`. While signed in and the syncing is on, YouTube gets the
     * song liked, disliked or neither, as it now is. Signed out it stays on the device, and the next
     * sync after signing in puts YouTube's likes back; dislikes aren't listed by YouTube, they stay.
     *
     * When YouTube can't be told, the reaction is taken back to what it was, unless another one
     * replaced it meanwhile.
     *
     * @throws YouTubeException when YouTube can't be reached or refuses, the reaction is taken
     * back then
     */
    suspend fun react(songId: String, likedAt: (Instant?) -> Instant?) =
        sender.react(songId, likedAt) { before, after ->
            val status = after.likeStatus
            if (before.likeStatus != status && syncs() && provider.isSignedIn()) {
                Logger.d(tag = TAG) { "Telling YouTube the reaction to $songId is $status" }
                provider.rate(songId, status)
            }
        }

    /**
     * @return whether the songs were stored, `false` when nobody is signed in, or the session
     * changed, the syncing was turned off or a reaction was saved or sent meanwhile
     */
    private suspend fun sync(session: String): Boolean {
        val since = sender.mark()
        Logger.i(tag = TAG) { "Syncing the liked songs" }
        val liked = provider.likedSongs()
        if (liked == null) {
            Logger.i(tag = TAG) { "No liked songs to sync, nobody is signed in" }
            return false
        }

        return sender.store(since) {
            if (provider.sessionId() != session || !syncs()) {
                Logger.i(tag = TAG) { "The sync was dropped, the session or the setting changed" }
                return@store false
            }
            Logger.i(tag = TAG) {
                "Storing ${liked.items.size} liked songs, " +
                        if (liked.continuation == null) "the whole list" else "a part of the list"
            }
            // More are liked than were fetched, the likes not fetched are left as they are then
            repository.syncLikedSongs(
                Origin.YOUTUBE,
                liked.items,
                complete = liked.continuation == null,
            )
            true
        }
    }

    /**
     * Brings the bookmarks of the albums stored up to date when their syncing is on. A failure
     * leaves them as they were.
     */
    private suspend fun syncAlbums(session: String) {
        if (!syncsAlbums()) return
        try {
            val saved = provider.libraryAlbumIds() ?: return
            if (provider.sessionId() != session || !syncsAlbums()) return
            Logger.i(tag = TAG) { "Syncing the bookmarks of ${saved.ids.size} albums" }
            userAlbums.syncBookmarks(Origin.YOUTUBE, saved.ids, saved.complete)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't bring the bookmarked albums up to date" }
        }
    }

    /**
     * Brings the follows of the artists stored up to date when their syncing is on. A failure
     * leaves them as they were.
     */
    private suspend fun syncArtists(session: String) {
        if (!syncsArtists()) return
        try {
            val subscribed = provider.libraryArtistIds() ?: return
            if (provider.sessionId() != session || !syncsArtists()) return
            Logger.i(tag = TAG) { "Syncing the follows of ${subscribed.ids.size} artists" }
            userArtists.syncFollows(Origin.YOUTUBE, subscribed.ids, subscribed.complete)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't bring the followed artists up to date" }
        }
    }

    /** What YouTube is told of a reaction, see `UserSong.likedAt`. */
    private val Instant?.likeStatus: LikeStatus
        get() = when {
            isDislike -> LikeStatus.DISLIKE
            isLike -> LikeStatus.LIKE
            else -> LikeStatus.INDIFFERENT
        }

    private companion object {

        const val TAG = "YouTubeLibrary"

        val MIN_INTERVAL = 5.minutes
    }
}

/**
 * The setting that turns [this] section of the YouTube library on: the liked songs are synced,
 * the others show on the home screen.
 */
val LibrarySection.youTubeSyncSetting
    get() = when (this) {
        LibrarySection.SONGS -> Preferences.YOUTUBE_SYNC_SONGS
        LibrarySection.ALBUMS -> Preferences.YOUTUBE_SYNC_ALBUMS
        LibrarySection.ARTISTS -> Preferences.YOUTUBE_SYNC_ARTISTS
        LibrarySection.PLAYLISTS -> Preferences.YOUTUBE_SYNC_PLAYLISTS
    }
