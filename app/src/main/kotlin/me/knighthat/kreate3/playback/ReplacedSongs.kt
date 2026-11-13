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

import androidx.media3.common.MediaItem
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import me.knighthat.kreate3.database.repository.RemoteSong
import me.knighthat.kreate3.database.repository.SongReplacement
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.youtube.toMediaItem
import me.knighthat.kreate3.ui.component.EXTRA_ARTIST_IDS
import me.knighthat.kreate3.ui.component.origin
import kotlin.time.Duration.Companion.milliseconds

/** What [checkUnavailable] found about a song its source says is unavailable. */
sealed interface SongCheck {

    /** It plays after all, it is tried again as it is. */
    data object Playable : SongCheck

    /** It no longer plays, [item] is the song published in its place, as the player queues it. */
    data class Replaced(val item: MediaItem) : SongCheck

    /** It no longer plays and nothing fits as the song published in its place. */
    data object Gone : SongCheck
}

/**
 * Whether YouTube still plays [item], a song it said is unavailable when its stream was asked
 * ([ERROR_CODE_UNAVAILABLE]), and if not, the song YouTube published in its place: YouTube
 * re-encodes songs or publishes them again under another id, the old one stops playing and points
 * nowhere. Found with [YouTubeProvider.isPlayable], then [YouTubeProvider.replacementOf] from the
 * title, artists and length of [item]. A song without a title, a length or artist ids can't be
 * matched and is [SongCheck.Gone]. Songs of other origins are [SongCheck.Playable].
 *
 * A replacement found takes what the library keeps of [item] with [store] before it is queued
 * (see `RemoteCatalogRepository.replaceSong`, a downloaded song is left as it is). A failure to
 * store it is logged and the replacement still plays.
 *
 * @param queued [item]'s replacement as the player queues it, `null` when it can't be
 * @param store moves what is kept of the song with the first id to the replacement
 * @throws me.knighthat.kreate3.provider.youtube.YouTubeException when YouTube can't be reached
 */
suspend fun YouTubeProvider.checkUnavailable(
    item: MediaItem,
    queued: (MediaItem) -> MediaItem?,
    store: suspend (String, RemoteSong) -> SongReplacement,
): SongCheck {
    if (item.origin != Origin.YOUTUBE) return SongCheck.Playable
    if (isPlayable(item.mediaId)) return SongCheck.Playable

    val metadata = item.mediaMetadata
    val title = metadata.title?.toString()
    val duration = metadata.durationMs?.milliseconds
    if (title == null || duration == null) {
        Logger.i(tag = TAG) {
            "${item.mediaId} has no ${if (title == null) "title" else "length"} to match, it's gone"
        }
        return SongCheck.Gone
    }
    val artistIds = metadata.extras?.getStringArray(EXTRA_ARTIST_IDS)?.toList().orEmpty()

    val found = replacementOf(
        songId = item.mediaId,
        title = title,
        artists = metadata.artist?.toString(),
        artistIds = artistIds,
        duration = duration,
    ) ?: return SongCheck.Gone
    val newId = found.song.song.id
    try {
        val stored = store(item.mediaId, found.song)
        Logger.i(tag = TAG) {
            when (stored) {
                SongReplacement.MOVED -> "What was kept of ${item.mediaId} is $newId's now"
                SongReplacement.DOWNLOADED -> "${item.mediaId} is downloaded, it stays as it is"
                SongReplacement.NOT_STORED -> "${item.mediaId} isn't stored, nothing to move to $newId"
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.w(e, TAG) { "Couldn't move what was kept of ${item.mediaId} to $newId" }
    }

    val replacement = queued(found.toMediaItem(this))
    if (replacement == null) {
        Logger.w(tag = TAG) { "${found.song.song.id} can't be queued in place of ${item.mediaId}" }
        return SongCheck.Gone
    }
    return SongCheck.Replaced(replacement)
}

private const val TAG = "ReplacedSongs"
