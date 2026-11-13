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

package me.knighthat.kreate3.ui.component

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.ExportedSong
import me.knighthat.kreate3.source.PlaylistExportSongs
import me.knighthat.kreate3.source.PlaylistFiles
import me.knighthat.kreate3.ui.screen.detail.itemName
import org.koin.compose.koinInject
import java.io.IOException

/**
 * Exports one of the app's playlists to a CSV file: [start] fetches its songs, asks the origins
 * to take in, then for the place of the file, and writes it on the app's scope.
 */
@Stable
internal class PlaylistExport {

    /** Whether the songs are being fetched, for a spinner on the control that started it. */
    var loading by mutableStateOf(false)
        internal set

    /** The latest start, set at every composition. */
    internal var begin: () -> Unit = {}

    /** Fetches the songs, then asks how to export them. Nothing happens while it loads. */
    fun start() = begin()
}

/** What the dialog asked for, kept while the file's place is picked. */
private class ExportRequest(val songs: List<ExportedSong>, val origins: Set<Origin>)

/**
 * The export of the app's playlist [playlistId], see [PlaylistExport].
 *
 * @param playlistId the playlist's database id
 * @param name the playlist's name, `null` while it isn't known
 * @param onDone runs once the export is written to its file, given up or failed to start, like
 * to close the menu it was started from
 */
@Composable
internal fun rememberPlaylistExport(
    playlistId: Long,
    name: String?,
    onDone: () -> Unit = {},
): PlaylistExport {
    val context = LocalContext.current
    val resources = LocalResources.current
    val notifier = LocalNotifier.current
    val appScope = koinInject<CoroutineScope>()
    val library = koinInject<UserSongRepository>()
    val songRepository = koinInject<SongRepository>()
    val youTube = koinInject<YouTubeProvider>()
    val resolver = context.contentResolver
    val source = remember(library, songRepository, youTube) {
        PlaylistExportSongs(library, songRepository, youTube)
    }
    val scope = rememberCoroutineScope()
    val currentName by rememberUpdatedState(name)
    val currentDone by rememberUpdatedState(onDone)
    var asking by remember { mutableStateOf<List<ExportedSong>?>(null) }
    var request by remember { mutableStateOf<ExportRequest?>(null) }

    fun quotedName() = resources.itemName(
        currentName?.takeIf { it.isNotBlank() },
        R.string.snackbar_this_playlist
    )

    fun onPicked(uri: Uri?) {
        val picked = request
        request = null
        if (uri != null && picked != null) {
            val shown = quotedName()
            val playlistName = currentName.orEmpty()
            appScope.launch {
                val saved = writePlaylist(resolver, uri, picked, playlistId, playlistName)
                // The file picker just closed: the app's screen may not show messages again yet,
                // and a message posted before it does would be dropped
                notifier.awaitScreen()
                notifier.post(
                    if (saved)
                        resources.getString(R.string.snackbar_playlist_exported, shown)
                    else
                        resources.getString(R.string.error_export_playlist, shown)
                )
            }
        }
        currentDone()
    }

    val picker = rememberLauncherForActivityResult(CreateDocument(CSV_MIME_TYPE)) { onPicked(it) }

    val export = remember { PlaylistExport() }
    export.begin = begin@{
        if (export.loading) return@begin
        export.loading = true
        scope.launch {
            val songs = try {
                source.of(playlistId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't get the songs of $playlistId to export" }
                null
            }
            export.loading = false
            when {
                songs == null -> {
                    notifier.post(
                        resources.getString(
                            R.string.error_collection_songs,
                            quotedName()
                        )
                    )
                    currentDone()
                }

                songs.isEmpty() -> {
                    notifier.post(
                        resources.getString(R.string.snackbar_playlist_export_empty, quotedName())
                    )
                    currentDone()
                }

                else -> asking = songs
            }
        }
    }

    asking?.let { songs ->
        ExportPlaylistDialog(
            present = songs.mapTo(HashSet()) { it.origin },
            onDismiss = {
                asking = null
                currentDone()
            },
            onConfirm = { origins ->
                asking = null
                request = ExportRequest(songs, origins)
                picker.launch(fileName(currentName))
            },
        )
    }

    return export
}

/**
 * Writes the songs of [request] to [uri] as CSV.
 *
 * @return whether the file was written
 */
private suspend fun writePlaylist(
    resolver: ContentResolver,
    uri: Uri,
    request: ExportRequest,
    playlistId: Long,
    name: String,
): Boolean = try {
    withContext(Dispatchers.IO) {
        val output = resolver.openOutputStream(uri, "wt")
            ?: throw IOException("No stream for the file")
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            PlaylistFiles.writeCsv(writer, name, request.songs, request.origins)
        }
    }
    true
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Logger.w(e, TAG) { "Couldn't export $playlistId" }
    false
}

/**
 * The type the file is made with: the one the system gives its extension, so the place that
 * makes the file keeps the extension instead of adding its own.
 */
private val CSV_MIME_TYPE: String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(PlaylistFiles.CSV_EXTENSION)
        ?: "text/csv"

/** The name suggested for the file: the playlist's, without what file names can't hold. */
private fun fileName(name: String?): String {
    val base = name?.replace(UNSAFE_FILE_NAME, "_")?.trim()?.takeIf { it.isNotEmpty() }
        ?: "playlist"
    return "$base.${PlaylistFiles.CSV_EXTENSION}"
}

private val UNSAFE_FILE_NAME = Regex("""[\\/:*?"<>|\u0000-\u001f]""")

private const val TAG = "PlaylistExport"

/**
 * Asks the songs of which origins to take in: a box per origin, checked at first; an origin
 * none of the songs has is grayed out and unchecked.
 *
 * @param present the origins the playlist's songs have
 */
@Composable
private fun ExportPlaylistDialog(
    present: Set<Origin>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Origin>) -> Unit,
) {
    var included by remember { mutableStateOf(present) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_export_playlist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.dialog_body_export_playlist_origins),
                    style = MaterialTheme.typography.titleSmall,
                )
                Origin.entries.forEach { origin ->
                    val enabled = origin in present
                    val checked = origin in included
                    val color = if (enabled)
                        LocalContentColor.current
                    else
                        LocalContentColor.current.copy(alpha = DISABLED_ALPHA)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(checked, enabled, Role.Checkbox) {
                                included = if (it) included + origin else included - origin
                            },
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                        // The label says the same, nothing more for screen readers
                        Icon(
                            painter = painterResource(origin.icon),
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(18.dp),
                        )
                        Text(
                            stringResource(origin.sourceRes),
                            color = color,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(included) },
                enabled = included.isNotEmpty(),
            ) { Text(stringResource(R.string.action_export)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Material's opacity of disabled content. */
private const val DISABLED_ALPHA = 0.38f

//<editor-fold defaultstate="collapsed" desc="Previews">
/** A playlist without songs of the server: its box is grayed out. */
@ComponentPreviews
@Composable
private fun ExportPlaylistDialogPreview() = PreviewTheme {
    ExportPlaylistDialog(
        present = setOf(Origin.YOUTUBE, Origin.LOCAL),
        onDismiss = {},
        onConfirm = {},
    )
}
//</editor-fold>
