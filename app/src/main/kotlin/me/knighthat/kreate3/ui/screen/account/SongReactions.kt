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

package me.knighthat.kreate3.ui.screen.account

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import kotlinx.coroutines.CancellationException
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicLibrary
import me.knighthat.kreate3.provider.opensubsonic.ServerException
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.provider.youtube.YouTubeLibrary
import me.knighthat.kreate3.ui.screen.detail.itemName
import me.knighthat.kreate3.ui.screen.detail.serverFailure
import me.knighthat.kreate3.ui.screen.detail.youTubeFailure
import kotlin.time.Instant

private const val TAG = "SongReactions"

/**
 * Changes the reaction to [songId] to what [change] makes of its `likedAt`, at its source too,
 * like the song menu does. A source that doesn't take it gets the reaction put back. [asked] is
 * the text for when the song can't be saved, "Couldn't remove the dislike from …".
 *
 * @return what to say when it failed, `null` once it's done
 */
internal suspend fun reactToSong(
    context: Context,
    songRepository: UserSongRepository,
    openSubsonicLibrary: OpenSubsonicLibrary,
    youTubeLibrary: YouTubeLibrary,
    songId: String,
    origin: Origin,
    name: String?,
    @StringRes asked: Int,
    change: (Instant?) -> Instant?,
): String? {
    val song = context.resources.itemName(name, R.string.snackbar_this_song)
    return try {
        when (origin) {
            Origin.OPEN_SUBSONIC -> openSubsonicLibrary.react(songId, change)
            Origin.YOUTUBE -> youTubeLibrary.react(songId, change)
            else -> songRepository.update(songId) { it.copy(likedAt = change(it.likedAt)) }
        }
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: ServerException) {
        e.serverFailure(context.resources, song)
    } catch (e: YouTubeException) {
        e.youTubeFailure(context.resources, song)
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't change the reaction to $songId", e)
        context.resources.getString(asked, song)
    }
}
