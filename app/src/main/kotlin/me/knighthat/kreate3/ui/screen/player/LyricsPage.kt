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

package me.knighthat.kreate3.ui.screen.player

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.model.Lyrics
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.lyrics.FoundLyrics
import me.knighthat.kreate3.lyrics.LyricsContent
import me.knighthat.kreate3.lyrics.LyricsFinder
import me.knighthat.kreate3.lyrics.LyricsQuery
import me.knighthat.kreate3.lyrics.SyncedLyrics
import me.knighthat.kreate3.lyrics.coarsenedTo
import me.knighthat.kreate3.lyrics.parseLyrics
import me.knighthat.kreate3.model.LyricsProvider
import me.knighthat.kreate3.model.LyricsSync
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.origin
import me.knighthat.kreate3.ui.screen.settings.prettyName
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "LyricsPage"

/** What the lyrics page shows. */
internal sealed interface LyricsState {

    data object Loading : LyricsState

    /**
     * @property text as the source gave it
     * @property content [text] read, timed no more finely than the user wants
     * @property source name of the source that found them
     * @property credit who the source names as the provider of the lyrics
     */
    data class Found(
        val text: String,
        val content: LyricsContent,
        val source: String,
        val credit: String?,
    ) : LyricsState

    /** The source asked has no lyrics for the song, the others may have. */
    data class Unavailable(val source: String) : LyricsState

    /** No source has lyrics for the song. */
    data object Missing : LyricsState

    /** A source couldn't be reached. */
    data object Failed : LyricsState
}

/**
 * The lyrics settings, read when the page loads.
 *
 * @property source the provider whose lyrics show, see [Preferences.LYRICS_SOURCE]
 * @property sync the timing the user wants, see [Preferences.LYRICS_SYNC]
 * @property alwaysLive see [Preferences.LYRICS_ALWAYS_LIVE]
 */
internal data class LyricsSettings(
    val source: LyricsProvider,
    val sync: LyricsSync,
    val alwaysLive: Boolean,
) {

    companion object {

        fun current() = LyricsSettings(
            Preferences.LYRICS_SOURCE.value,
            Preferences.LYRICS_SYNC.value,
            Preferences.LYRICS_ALWAYS_LIVE.value,
        )
    }
}

/**
 * Lyrics the user asked for with the buttons of the page, instead of the ones of the source of the
 * settings.
 *
 * @property provider whose lyrics show, the one of the settings when `null`; unused by a search
 * @property title what the song is called in the search, the song's own title when `null`
 * @property artist who sings it in the search, the song's own artists when `null`, none when blank
 */
internal data class LyricsRequest(
    val provider: LyricsProvider? = null,
    val title: String? = null,
    val artist: String? = null,
) {

    /** Whether the user typed what to search, the song's own words aren't used then. */
    val isSearch: Boolean
        get() = title != null || artist != null

    /** [query] as the user changed it. Its album goes with words the user typed, it came with
     * the song's own. */
    fun applyTo(query: LyricsQuery): LyricsQuery {
        if (title == null && artist == null) return query
        return query.copy(
            title = title ?: query.title,
            artists = artist?.let { listOf(it.trim()).filter(String::isNotEmpty) } ?: query.artists,
            album = null,
        )
    }
}

/**
 * What the buttons under the tabs change on the lyrics page, made for one song. The page tells
 * them what it [shown], they ask it for other lyrics with [request] and to show them timed less
 * finely with [sync].
 */
@Stable
internal class LyricsControls {

    /** Lyrics asked for instead of the usual ones, `null` for the usual ones. */
    var request by mutableStateOf<LyricsRequest?>(null)

    /** The lyrics on the page, `null` while there are none. */
    var shown by mutableStateOf<LyricsState.Found?>(null)

    /** How finely [shown] is drawn, `null` as it loaded. */
    var sync by mutableStateOf<LyricsSync?>(null)

    /** Back to the usual lyrics, drawn as they load. */
    fun reset() {
        request = null
        sync = null
    }
}

/** What the inline icon of the change source button reads as, and where it goes in the hint. */
private const val CHANGE_SOURCE_MARK = "⇄"

/** Sizes the lyrics' text can take, in percent, see [Preferences.LYRICS_TEXT_SIZE]. */
internal val LYRICS_TEXT_SIZES = 80..200 step 10

/** How [LyricsSync] is shown to the user. */
@get:StringRes
internal val LyricsSync.labelRes: Int
    get() = when (this) {
        LyricsSync.WORD -> R.string.menu_word_by_word
        LyricsSync.LINE -> R.string.menu_line_by_line
        LyricsSync.NONE -> R.string.menu_plain_text
    }

/** What lyrics sources are told about the item, `null` when it has no origin or title. */
internal fun MediaItem.lyricsQuery(): LyricsQuery? {
    val origin = origin ?: return null
    val title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: return null
    return LyricsQuery(
        songId = mediaId,
        origin = origin,
        title = title,
        artists = listOfNotNull(mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }),
        album = mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() },
        duration = mediaMetadata.durationMs?.milliseconds,
    )
}

/**
 * Lyrics of the song of [query] from one source: the one [request] picked, the one of the settings
 * otherwise.
 *
 * 1. The [stored] ones of that source, whatever their timing, unless [remote] asks to fetch them
 *    again or the settings say to always fetch.
 * 2. Otherwise the ones that source finds, [LyricsState.Unavailable] when it has none.
 *
 * A [request] with typed words always fetches, the first lyrics timed as the user wants of every
 * source.
 *
 * Lyrics shown are read with [parseLyrics] and timed no more finely than the user wants. Lyrics
 * fetched are given to [save], replacing the stored ones of their source, unless the settings say
 * to always fetch.
 */
internal suspend fun loadLyrics(
    query: LyricsQuery,
    request: LyricsRequest?,
    settings: LyricsSettings,
    remote: Boolean,
    finder: LyricsFinder,
    stored: suspend (LyricsProvider) -> Lyrics?,
    save: (Lyrics) -> Unit,
): LyricsState {
    val source = finder.source(request?.provider ?: settings.source) ?: finder.sources.first()
    val search = request?.takeIf { it.isSearch }
    if (search == null && !remote && !settings.alwaysLive)
        stored(source.provider)?.let { return it.toState(finder, settings) }

    val found: FoundLyrics = try {
        if (search == null)
            source.find(query) ?: return LyricsState.Unavailable(source.name)
        else
            finder.find(search.applyTo(query), settings.sync) ?: return LyricsState.Missing
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't look up the lyrics of ${query.songId}", e)
        return LyricsState.Failed
    }

    if (!settings.alwaysLive)
        save(Lyrics(query.songId, found.text, found.sync, found.source, found.credit))
    return shown(found.text, finder.nameOf(found.source), found.credit, settings)
}

private suspend fun Lyrics.toState(finder: LyricsFinder, settings: LyricsSettings) =
    shown(lyrics, finder.nameOf(source), credit, settings)

private suspend fun shown(text: String, source: String, credit: String?, settings: LyricsSettings) =
    LyricsState.Found(text, parseLyrics(text).coarsenedTo(settings.sync), source, credit)

/** Name of the source of [provider], the provider's own when it has no source anymore. */
private fun LyricsFinder.nameOf(provider: LyricsProvider): String =
    source(provider)?.name ?: provider.prettyName()

/**
 * The lyrics of [item], with who provides them under them. Timed lyrics follow the song, see
 * [SyncedLyricsList]. They load when the page shows, see [loadLyrics]. When the source has none or
 * can't be reached, a button fetches them again. The buttons under the tabs ask for other lyrics and change how they show, see
 * [controls]. The text is as large as [Preferences.LYRICS_TEXT_SIZE] says.
 *
 * @param isPlaying whether [item] is playing, timed lyrics follow it while it does
 * @param controls made for [item]
 * @param scope stores the lyrics found, the page may be gone by then
 */
@Composable
internal fun LyricsPage(
    item: MediaItem,
    isPlaying: Boolean,
    controller: PlaybackController,
    controls: LyricsControls,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    songs: SongRepository = koinInject(),
    finder: LyricsFinder = koinInject(),
    scope: CoroutineScope = koinInject(),
) {
    var attempt by remember(item.mediaId) { mutableIntStateOf(0) }
    // Set by the buttons that fetch again, for the next load alone
    var fetchAgain by remember(item.mediaId) { mutableStateOf(false) }
    val request = controls.request
    val state by produceState<LyricsState>(LyricsState.Loading, item.mediaId, attempt, request) {
        val remote = fetchAgain
        fetchAgain = false
        value = LyricsState.Loading
        val query = item.lyricsQuery()
        val save: (Lyrics) -> Unit = { lyrics ->
            scope.launch {
                try {
                    songs.saveLyrics(lyrics)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Like a song of YouTube that isn't stored yet, it is fetched again next time
                    Log.w(TAG, "Couldn't store the lyrics of ${lyrics.songId}", e)
                }
            }
        }
        value = if (query == null)
            LyricsState.Missing
        else
            loadLyrics(
                query = query,
                request = request,
                settings = LyricsSettings.current(),
                remote = remote,
                finder = finder,
                stored = { songs.lyricsOf(item.mediaId, it).first() },
                save = save,
            )
    }
    LaunchedEffect(controls, state) {
        controls.shown = state as? LyricsState.Found
        // Timed differently, other lyrics are drawn as they load
        controls.sync = null
    }

    val textSize by Preferences.LYRICS_TEXT_SIZE.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val scaled = remember(density, textSize) {
        Density(density.density, density.fontScale * textSize / 100f)
    }
    CompositionLocalProvider(LocalDensity provides scaled) {
        LyricsPageContent(
            state = state,
            sync = controls.sync,
            isPlaying = isPlaying,
            controller = controller,
            contentPadding = contentPadding,
            onFetchAgain = {
                fetchAgain = true
                attempt++
            },
            modifier = modifier,
        )
    }
}

/**
 * What [LyricsPage] shows for [state], found lyrics timed no more finely than [sync] when it
 * isn't `null`.
 */
@Composable
private fun LyricsPageContent(
    state: LyricsState,
    sync: LyricsSync?,
    isPlaying: Boolean,
    controller: PlaybackController,
    contentPadding: PaddingValues,
    onFetchAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val shown = state) {
        is LyricsState.Found -> {
            val footer = listOfNotNull(
                stringResource(R.string.screen_lyrics_status_from, shown.source),
                shown.credit
            ).joinToString("\n")
            val content by produceState(shown.content, shown, sync) {
                value = if (sync == null) shown.content
                else withContext(Dispatchers.Default) { shown.content.coarsenedTo(sync) }
            }
            val drawn = content
            if (drawn is SyncedLyrics)
                SyncedLyricsList(drawn, footer, isPlaying, controller, contentPadding, modifier)
            else
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(contentPadding)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = drawn.text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = footer,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
        }

        else -> Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (shown) {
                    LyricsState.Loading -> CircularProgressIndicator()
                    LyricsState.Missing -> Message(stringResource(R.string.screen_lyrics_status_missing))
                    is LyricsState.Unavailable -> {
                        Message(
                            stringResource(
                                R.string.screen_lyrics_status_unavailable,
                                shown.source
                            )
                        )
                        val hint = stringResource(
                            R.string.screen_lyrics_hint_change_source,
                            CHANGE_SOURCE_MARK
                        )
                        Message(
                            buildAnnotatedString {
                                // The icon takes the place of the mark, wherever the translation puts it
                                val start = hint.indexOf(CHANGE_SOURCE_MARK)
                                if (start < 0) append(hint)
                                else {
                                    append(hint.substring(0, start))
                                    appendInlineContent(CHANGE_SOURCE_ICON, CHANGE_SOURCE_MARK)
                                    append(hint.substring(start + CHANGE_SOURCE_MARK.length))
                                }
                            },
                        )
                        Button(onClick = onFetchAgain) { Text(stringResource(R.string.action_reload)) }
                    }

                    else -> {
                        Message(stringResource(R.string.error_lyrics_load))
                        TextButton(onClick = onFetchAgain) { Text(stringResource(R.string.action_retry)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) = Message(AnnotatedString(text))

/** [text] of a message of the page, with the icon of a button in it at [CHANGE_SOURCE_ICON]. */
@Composable
private fun Message(text: AnnotatedString) = Text(
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    inlineContent = mapOf(
        CHANGE_SOURCE_ICON to InlineTextContent(
            Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)
        ) {
            // Described by the text around it
            Icon(painterResource(R.drawable.swap_horiz), contentDescription = null)
        },
    ),
)

/** Where a message shows the icon of the Change source button, see [LyricsButtons]. */
private const val CHANGE_SOURCE_ICON = "changeSource"
