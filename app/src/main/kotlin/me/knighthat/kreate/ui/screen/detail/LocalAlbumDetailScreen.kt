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

package me.knighthat.kreate.ui.screen.detail

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.launch
import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.provider.opensubsonic.ServerCheck
import me.knighthat.kreate.provider.opensubsonic.ServerException
import me.knighthat.kreate.source.local.albumArtUri
import me.knighthat.kreate.ui.component.ShareButton
import me.knighthat.kreate.ui.component.SongItem
import org.koin.androidx.compose.koinViewModel

/** Opens the system's share sheet with [link], titled [title]. */
private fun Context.shareLink(title: String?, link: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, link)
        .apply { title?.let { putExtra(Intent.EXTRA_TITLE, it) } }
    startActivity(Intent.createChooser(send, null))
}

/** What to tell the user when no link could be made. */
private fun Throwable.shareFailure(): String =
    when (val check = (this as? ServerException)?.check) {
        is ServerCheck.Rejected -> "The server refused to share: ${check.message.ifEmpty { "error ${check.code}" }}"
        is ServerCheck.Unreachable, is ServerCheck.UntrustedCertificate,
        is ServerCheck.CertificateChanged -> "Can't reach the server to share"

        null -> message ?: "Can't share this album"
        else -> "Can't share this album"
    }

/**
 * A album of this device's library.
 */
@Composable
fun LocalAlbumDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocalAlbumDetailViewModel = koinViewModel(),
) {
    val album by viewModel.album.collectAsState()
    val songs = viewModel.songs.collectAsLazyPagingItems()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }

    DetailScaffold(
        title = album?.name.orEmpty(),
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (album?.let(viewModel::canShare) == true)
                ShareButton(
                    enabled = !sharing,
                    onClick = {
                        sharing = true
                        scope.launch {
                            viewModel.shareLink()
                                .onSuccess { context.shareLink(album?.name, it) }
                                .onFailure {
                                    Toast.makeText(context, it.shareFailure(), Toast.LENGTH_LONG)
                                        .show()
                                }
                            sharing = false
                        }
                    },
                )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                DetailHeader(
                    uri = album?.let {
                        it.thumbnail ?: it.id.toLongOrNull()
                            ?.takeIf { _ -> it.origin == Origin.LOCAL }?.let(::albumArtUri)
                    },
                    title = album?.name,
                    subtitle = album?.let {
                        listOfNotNull(
                            it.artists?.joinToString(", "),
                            it.year?.toString()
                        ).takeIf { p -> p.isNotEmpty() }?.joinToString(" · ")
                    },
                    shape = MaterialTheme.shapes.medium,
                )
            }
            items(count = songs.itemCount, key = songs.itemKey { it.mediaId }) { index ->
                songs[index]?.let {
                    SongItem(it, onClick = {}, showThumbnail = false)
                }
            }
        }
    }
}
