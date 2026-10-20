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

package me.knighthat.kreate3.playback

import android.util.Log
import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.repository.RemoteCatalogRepository
import me.knighthat.kreate3.database.repository.RemoteSong
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.ui.component.origin
import java.util.concurrent.ConcurrentHashMap

/**
 * Stores a song of YouTube or the server once it starts playing, in the catalog and the user's
 * library, see [RemoteCatalogRepository.savePlayedSong]. Songs shown live are only stored then,
 * not when they are listed or queued. The song is asked to its source again, so what is stored
 * is what the source has now, not what the list showed.
 *
 * A song already in the library is left as it is. Once a song is stored, the format found while
 * it loaded is kept ([SongFormatRecorder.flush]) and [onStored] runs, on the thread of [player],
 * so what needs the song in the database can catch up, like [PersistentQueue.refresh].
 *
 * A failure is logged, the song plays anyway and is tried again the next time it starts.
 *
 * @param scope runs the writes, it has to outlive the player like for [PlaybackHistoryRecorder]
 * @param playerScope runs on the thread of [player], for [onStored]
 */
class PlayedSongRecorder(
    player: Player,
    private val library: UserSongRepository,
    private val catalog: RemoteCatalogRepository,
    private val formats: SongFormatRecorder,
    private val details: suspend (origin: Origin, songId: String) -> RemoteSong?,
    private val scope: CoroutineScope,
    private val playerScope: CoroutineScope,
    private val onStored: () -> Unit,
) : Player.Listener {

    constructor(
        player: Player,
        library: UserSongRepository,
        catalog: RemoteCatalogRepository,
        formats: SongFormatRecorder,
        youTube: YouTubeProvider,
        openSubsonic: OpenSubsonicProvider,
        scope: CoroutineScope,
        playerScope: CoroutineScope,
        onStored: () -> Unit,
    ) : this(
        player,
        library,
        catalog,
        formats,
        { origin, songId ->
            when (origin) {
                Origin.YOUTUBE -> youTube.songDetails(songId)
                Origin.OPEN_SUBSONIC -> openSubsonic.songDetails(songId)
                Origin.LOCAL -> null
            }
        },
        scope,
        playerScope,
        onStored,
    )

    /** Songs stored, or being stored, since the player was made. */
    private val handled = ConcurrentHashMap.newKeySet<String>()

    init {
        player.addListener(this)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (!player.isPlaying) return
        val item = player.currentMediaItem ?: return
        val origin = item.origin?.takeIf { it != Origin.LOCAL } ?: return
        if (!handled.add(item.mediaId)) return

        scope.launch { store(origin, item.mediaId) }
    }

    private suspend fun store(origin: Origin, songId: String) {
        try {
            if (library.find(songId).first() != null) {
                // Its format was stored when it loaded
                return
            }
            val song = details(origin, songId)
            if (song == null) {
                Log.w(TAG, "$origin doesn't know $songId, it isn't stored")
                handled.remove(songId)
                return
            }
            catalog.savePlayedSong(song)
            formats.flush(songId)
            playerScope.launch { onStored() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't store $songId", e)
            handled.remove(songId)
        }
    }

    private companion object {
        const val TAG = "PlayedSongRecorder"
    }
}
