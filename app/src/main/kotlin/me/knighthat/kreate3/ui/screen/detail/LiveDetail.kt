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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.provider.opensubsonic.ServerCheck
import me.knighthat.kreate3.provider.opensubsonic.ServerException
import me.knighthat.kreate3.provider.youtube.YouTubeBrowse
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.ui.component.AlbumItem
import me.knighthat.kreate3.ui.component.ArtistItem
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.navigation.Tab

/**
 * Where fetching an item of an online catalog or the OpenSubsonic server stands. What it fetched is only kept by the screen,
 * never stored.
 */
sealed interface LiveState<out T> {

    data object Loading : LiveState<Nothing>

    data class Ready<T>(val value: T) : LiveState<T>

    data class Failed(val reason: String) : LiveState<Nothing>
}

/**
 * Fetches an item of an online catalog when its screen opens, see [LiveDetailScreen]. Subclasses
 * call [load] once from their `init`, after their own properties are set.
 */
abstract class LiveDetailViewModel<T : Any> : ViewModel() {

    private val _state = MutableStateFlow<LiveState<T>>(LiveState.Loading)
    private var job: Job? = null

    val state: StateFlow<LiveState<T>> = _state.asStateFlow()

    /** What [load] says when [fetch] finds nothing, like "YouTube doesn't have this album". */
    protected abstract val notFound: String

    /** @return `null` when the catalog doesn't have the item */
    protected abstract suspend fun fetch(): T?

    /** Starts over, also after a failure. */
    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = LiveState.Loading
            _state.value = try {
                // Turning the answer into the page takes long, never on the main thread
                withContext(Dispatchers.Default) { fetch() }?.let { LiveState.Ready(it) }
                    ?: LiveState.Failed(notFound)
            } catch (e: CancellationException) {
                throw e
            } catch (e: YouTubeException) {
                LiveState.Failed(if (e.status == null) "Can't reach YouTube" else "YouTube refused: ${e.message}")
            } catch (e: ServerException) {
                LiveState.Failed(e.reason())
            }
        }
    }
}

/**
 * What [block] asks the server, `null` when the server can't or won't answer. For what a screen
 * shows when there is some and does without otherwise, like an album's notes.
 */
internal suspend fun <T> askServerOrNull(block: suspend () -> T?): T? =
    try {
        block()
    } catch (_: ServerException) {
        null
    }

/** What a screen of the OpenSubsonic server says when the provider gives nothing. */
internal const val SERVER_OFF = "The server is turned off, turn it on in Network settings"

/** What to tell the user when the OpenSubsonic server couldn't give an item. */
private fun ServerException.reason(): String = when (val check = check) {
    is ServerCheck.Rejected -> "The server refused: ${check.message.ifEmpty { "error ${check.code}" }}"
    is ServerCheck.Unreachable -> "Can't reach the server: ${check.reason}"
    is ServerCheck.UntrustedCertificate -> "The server's certificate isn't trusted"
    is ServerCheck.CertificateChanged -> "The server's certificate changed, check it in Network settings"
    ServerCheck.NotSubsonic -> "The server sent something unexpected"
    ServerCheck.NotConfigured -> "Sign in to the server in Network settings"
    else -> "Couldn't load this from the server"
}

/**
 * A detail screen of an item fetched live: [content] shows once it arrived. Meanwhile there is a
 * spinner, and when it fails the reason with a way to try again, [onRetry].
 *
 * @param state see [LiveDetailViewModel.state]
 * @param contentPadding see [DetailScaffold]
 */
@Composable
fun <T : Any> LiveDetailScreen(
    title: String,
    onBack: () -> Unit,
    state: LiveState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (T, PaddingValues) -> Unit,
) {
    DetailScaffold(title, onBack, modifier, contentPadding, actions) { innerPadding ->
        when (val current = state) {
            is LiveState.Ready -> content(current.value, innerPadding)

            else -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
            ) {
                if (current is LiveState.Failed)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = current.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        FilledTonalButton(onClick = onRetry) { Text("Try again") }
                    }
                else CircularProgressIndicator()
            }
        }
    }
}

/**
 * A row of items fetched live, like "Singles & EPs" of an artist, each with the [Tab] of its kind.
 *
 * @property more the page with every item of the row, `null` when the row shows all of them
 */
data class LiveSection(
    val title: String?,
    val items: List<Pair<Tab, MediaItem>>,
    val more: YouTubeBrowse? = null,
)

/**
 * A titled row of cards scrolling sideways. Artists show round, anything else like an album. The
 * title has an arrow to every item when the row shows only some.
 */
@Composable
fun LiveSectionRow(
    section: LiveSection,
    onOpen: (Tab, MediaItem) -> Unit,
    onSeeMore: (title: String, more: YouTubeBrowse) -> Unit,
) {
    val more = section.more
    section.title?.let { title ->
        SectionTitle(title, onSeeMore = more?.let { { onSeeMore(title, it) } })
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            section.items,
            key = { (tab, item) -> "${tab.name}:${item.mediaId}" }) { (tab, item) ->
            val modifier = Modifier.width(CARD_WIDTH.dp)
            if (tab == Tab.ARTISTS) ArtistItem(
                item,
                onClick = { onOpen(tab, item) },
                modifier = modifier
            )
            else AlbumItem(item, onClick = { onOpen(tab, item) }, modifier = modifier)
        }
    }
}

private const val CARD_WIDTH = 120
