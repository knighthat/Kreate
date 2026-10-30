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

import android.content.Context
import androidx.annotation.PluralsRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.provider.opensubsonic.ServerCheck
import me.knighthat.kreate3.provider.opensubsonic.ServerException
import me.knighthat.kreate3.provider.youtube.YouTubeBrowse
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.source.withoutDislikedSongs
import me.knighthat.kreate3.ui.component.AlbumItem
import me.knighthat.kreate3.ui.component.ArtistItem
import me.knighthat.kreate3.ui.component.SectionTitle
import me.knighthat.kreate3.ui.component.openSubsonicName
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
 *
 * @param context for the texts of what went wrong
 * @param dislikedIds see [me.knighthat.kreate3.database.repository.UserSongRepository.dislikedIds]
 * @param background where [fetch] runs, never the main thread
 */
abstract class LiveDetailViewModel<T : Any>(
    protected val context: Context,
    dislikedIds: Flow<Set<String>>,
    private val background: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow<LiveState<T>>(LiveState.Loading)
    private var job: Job? = null

    /** Ids of the songs the user disliked, they're never shown. */
    protected val disliked: StateFlow<Set<String>> =
        dislikedIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** What [fetch] gave, without the songs the user disliked, also the ones disliked meanwhile. */
    val state: StateFlow<LiveState<T>> = combine(_state, disliked) { state, ids ->
        if (state is LiveState.Ready && ids.isNotEmpty())
            LiveState.Ready(state.value.withoutSongs(ids))
        else state
    }
        .flowOn(background)
        .stateIn(viewModelScope, SharingStarted.Eagerly, LiveState.Loading)

    /** What [load] says when [fetch] finds nothing, like "YouTube doesn't have this album". */
    protected abstract val notFound: String

    /** @return `null` when the catalog doesn't have the item */
    protected abstract suspend fun fetch(): T?

    /** The item without the songs whose id is in [disliked], the rest as it is. */
    protected abstract fun T.withoutSongs(disliked: Set<String>): T

    /** Starts over, also after a failure. */
    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = LiveState.Loading
            _state.value = try {
                // Turning the answer into the page takes long, never on the main thread
                withContext(background) { fetch() }?.let { LiveState.Ready(it) }
                    ?: LiveState.Failed(notFound)
            } catch (e: CancellationException) {
                throw e
            } catch (e: YouTubeException) {
                val youTube = context.getString(R.string.source_ytm)
                LiveState.Failed(
                    if (e.status == null) context.getString(R.string.error_unreachable, youTube)
                    else context.getString(
                        R.string.error_source_refused,
                        youTube,
                        e.message.orEmpty()
                    )
                )
            } catch (e: ServerException) {
                LiveState.Failed(e.reason(context))
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
internal fun Context.serverOff(): String =
    getString(
        R.string.error_server_off,
        resources.openSubsonicName(),
        getString(R.string.screen_title_network)
    )

/** What a screen of YouTube says when it doesn't have the item, a [noun] like the plural album. */
internal fun Context.youTubeNotFound(@PluralsRes noun: Int): String =
    getString(
        R.string.error_source_not_found,
        getString(R.string.source_ytm),
        resources.getQuantityString(noun, 1),
    )

/** What to tell the user when the OpenSubsonic server couldn't give an item. */
private fun ServerException.reason(context: Context): String {
    val server = context.resources.openSubsonicName()
    return when (val check = check) {
        is ServerCheck.Rejected -> context.getString(
            R.string.error_source_refused,
            server,
            check.message.ifEmpty { context.getString(R.string.error_server_code, check.code) },
        )

        is ServerCheck.Unreachable ->
            context.getString(R.string.error_server_unreachable, server, check.reason)

        is ServerCheck.UntrustedCertificate ->
            context.getString(R.string.error_server_certificate_untrusted, server)

        is ServerCheck.CertificateChanged -> context.getString(
            R.string.error_server_certificate_changed,
            server,
            context.getString(R.string.screen_title_network),
        )

        ServerCheck.NotSubsonic -> context.getString(R.string.error_server_unexpected, server)
        ServerCheck.NotConfigured -> context.getString(
            R.string.error_server_not_configured,
            server,
            context.getString(R.string.screen_title_network),
        )

        else -> context.getString(R.string.error_server_load, server)
    }
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
                        FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
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

/** The rows without the songs whose id is in [disliked], a row left with nothing goes too. */
@JvmName("sectionsWithoutDislikedSongs")
internal fun List<LiveSection>.withoutDislikedSongs(disliked: Set<String>): List<LiveSection> =
    mapNotNull { section ->
        section.copy(items = section.items.withoutDislikedSongs(disliked))
            .takeIf { it.items.isNotEmpty() }
    }

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
