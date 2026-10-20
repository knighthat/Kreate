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

package me.knighthat.kreate3.ui.screen.detail

import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.notification.MessageDuration
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.ServerCheck
import me.knighthat.kreate3.provider.opensubsonic.ServerException
import me.knighthat.kreate3.provider.opensubsonic.Shareable
import me.knighthat.kreate3.provider.youtube.YouTubeLinks
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.ShareButton
import me.knighthat.kreate3.ui.component.localSongId
import me.knighthat.kreate3.ui.component.origin
import org.koin.compose.koinInject

/** Whether the user may make shares on the server, `false` until that is known. */
internal fun OpenSubsonicProvider.canShare(scope: CoroutineScope): StateFlow<Boolean> =
    flow { emit(shareRole() != false) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

/**
 * A link to [item] that anyone can open, asked of the server at every tap. It gives a share it
 * has already or makes one. Links aren't stored, a share can expire or be deleted on the server.
 *
 * @param name shown on the shared page of a new share
 * @return the link, or why there is none
 */
internal suspend fun OpenSubsonicProvider.shareLinkOf(
    item: Shareable,
    name: String?,
): Result<String> = runCatching {
    checkNotNull(shareLink(item, name)) { "The server is turned off" }
}

/** Opens the system's share sheet with [link], titled [title]. */
internal fun Context.shareLink(title: String?, link: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, link)
        .apply { title?.let { putExtra(Intent.EXTRA_TITLE, it) } }
    startActivity(Intent.createChooser(send, null))
}

/** What to tell the user when no link could be made for a [kind], like "album". */
internal fun Throwable.shareFailure(kind: String): String =
    when (val check = (this as? ServerException)?.check) {
        // The button only shows to an account that may share, so the server doesn't share this
        is ServerCheck.Rejected -> "This server doesn't support sharing this $kind"
        is ServerCheck.Unreachable,
        is ServerCheck.UntrustedCertificate,
        is ServerCheck.CertificateChanged -> "Can't reach the server to share"

        null -> message ?: "Can't share this $kind"
        else -> "Can't share this $kind"
    }

/**
 * The share button of a detail screen's top bar. A tap gets the link from [link], which may ask
 * the server, then opens the share sheet titled [title]. It is off while the link is on its way,
 * and a failure is posted to [LocalNotifier].
 *
 * @param kind what is shared, like "album", for the failure message
 */
@Composable
fun ShareAction(title: String?, kind: String, link: suspend () -> Result<String>) {
    val context = LocalContext.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }

    ShareButton(
        enabled = !sharing,
        onClick = {
            sharing = true
            scope.launch {
                link()
                    .onSuccess { context.shareLink(title, it) }
                    .onFailure {
                        notifier.post(Message(it.shareFailure(kind), MessageDuration.LONG))
                    }
                sharing = false
            }
        },
    )
}

/** Sharing a song, see [rememberSongShare]. */
@Stable
class SongShare(
    /** `false` for an account of the server that may not share, and while a link is on its way. */
    val enabled: Boolean,
    private val start: (then: () -> Unit) -> Unit,
) {

    /** Opens the share sheet, [then] runs once it opened or the link couldn't be made. */
    fun share(then: () -> Unit = {}) = start(then)
}

/**
 * Shares the song [item]: its link on YouTube Music, a share of the server for OpenSubsonic, which
 * may be asked for first, or the file itself for a song of the device. A failure is posted to
 * [LocalNotifier].
 */
@Composable
fun rememberSongShare(
    item: MediaItem,
    openSubsonic: OpenSubsonicProvider = koinInject(),
): SongShare {
    val context = LocalContext.current
    val notifier = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val origin = item.origin
    val serverShares by remember(openSubsonic) { openSubsonic.canShare(scope) }
        .collectAsStateWithLifecycle()
    var sharing by remember { mutableStateOf(false) }
    val title = item.mediaMetadata.title?.toString()

    val enabled = !sharing && when (origin) {
        Origin.OPEN_SUBSONIC -> serverShares
        null -> false
        else -> true
    }
    return SongShare(enabled) { then ->
        when (origin) {
            Origin.LOCAL -> {
                item.localSongId?.let { context.shareSongFile(title, it) }
                then()
            }

            Origin.YOUTUBE -> {
                context.shareLink(title, YouTubeLinks.song(item.mediaId))
                then()
            }

            Origin.OPEN_SUBSONIC -> {
                sharing = true
                scope.launch {
                    openSubsonic.shareLinkOf(Shareable.Song(item.mediaId), title)
                        .onSuccess { context.shareLink(title, it) }
                        .onFailure {
                            notifier.post(Message(it.shareFailure("song"), MessageDuration.LONG))
                        }
                    sharing = false
                    then()
                }
            }

            null -> then()
        }
    }
}

/**
 * Opens the system's share sheet with the file of the device's song [songId], titled [title].
 * The app it goes to may read it, only that file.
 */
private fun Context.shareSongFile(title: String?, songId: Long) {
    val file = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
    val send = Intent(Intent.ACTION_SEND)
        .setType("audio/*")
        .putExtra(Intent.EXTRA_STREAM, file)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply {
            // The share sheet passes the permission on with it
            clipData = ClipData.newRawUri(title, file)
            title?.let { putExtra(Intent.EXTRA_TITLE, it) }
        }
    startActivity(Intent.createChooser(send, null))
}
