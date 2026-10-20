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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.model.Lyrics
import me.knighthat.kreate3.database.model.LyricsSync
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.lyrics.FoundLyrics
import me.knighthat.kreate3.lyrics.LyricsContent
import me.knighthat.kreate3.lyrics.LyricsFinder
import me.knighthat.kreate3.lyrics.LyricsQuery
import me.knighthat.kreate3.lyrics.SyncedLyrics
import me.knighthat.kreate3.lyrics.coarsenedTo
import me.knighthat.kreate3.lyrics.parseLyrics
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.ui.component.origin
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

    /** The chosen source has no lyrics for the song, the others may have. */
    data class Unavailable(val source: String) : LyricsState

    /** No source has lyrics for the song. */
    data object Missing : LyricsState

    /** A source couldn't be reached. */
    data object Failed : LyricsState
}

/**
 * The lyrics settings, read when the page loads.
 *
 * @property source id of the source asked first, see [Preferences.LYRICS_SOURCE]
 * @property sync the timing the user wants, see [Preferences.LYRICS_SYNC]
 * @property alwaysLive see [Preferences.LYRICS_ALWAYS_LIVE]
 */
internal data class LyricsSettings(
    val source: String,
    val sync: LyricsSync,
    val alwaysLive: Boolean,
) {

    companion object {

        fun current() = LyricsSettings(
            Preferences.LYRICS_SOURCE.value,
            lyricsSyncOf(Preferences.LYRICS_SYNC.value),
            Preferences.LYRICS_ALWAYS_LIVE.value,
        )
    }
}

/** The [LyricsSync] named [name], word by word when no value has that name. */
internal fun lyricsSyncOf(name: String): LyricsSync =
    LyricsSync.entries.firstOrNull { it.name == name } ?: LyricsSync.WORD

/** How [LyricsSync] is shown to the user. */
internal val LyricsSync.label: String
    get() = when (this) {
        LyricsSync.WORD -> "Word by word"
        LyricsSync.LINE -> "Line by line"
        LyricsSync.NONE -> "Plain text"
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
 * Lyrics of the song of [query]:
 *
 * 1. the [stored] ones, whatever their source, when they are timed the way the user wants, unless
 *    the settings say to always fetch;
 * 2. otherwise, the ones of the chosen source alone, [LyricsState.Unavailable] when it has none;
 * 3. with [everywhere], after the user asked to look elsewhere: the most finely timed of every
 *    other source, the stored ones when none has any.
 *
 * Lyrics shown are read with [parseLyrics] and timed no more finely than the user wants. Lyrics
 * found are given to [save], unless the settings say to always fetch, or the stored ones are
 * timed more finely and the found ones aren't timed the way the user wants either.
 */
internal suspend fun loadLyrics(
    query: LyricsQuery,
    settings: LyricsSettings,
    everywhere: Boolean,
    finder: LyricsFinder,
    stored: suspend () -> Lyrics?,
    save: (Lyrics) -> Unit,
): LyricsState {
    val saved = if (settings.alwaysLive) null else stored()
    if (!everywhere && saved != null && saved.sync == settings.sync)
        return saved.toState(finder, settings)

    val chosen = finder.source(settings.source) ?: finder.sources.first()
    val found: FoundLyrics? = try {
        if (everywhere)
            finder.find(query, exclude = setOf(chosen.id))
        else
            chosen.find(query) ?: return LyricsState.Unavailable(chosen.name)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't look up the lyrics of ${query.songId}", e)
        return LyricsState.Failed
    }
    if (found == null) return saved?.toState(finder, settings) ?: LyricsState.Missing

    val keep = saved == null || found.sync == settings.sync || found.sync >= saved.sync
    if (!settings.alwaysLive && keep)
        save(Lyrics(query.songId, found.text, found.sync, found.source, found.credit))
    return shown(found.text, finder.nameOf(found.source), found.credit, settings)
}

private suspend fun Lyrics.toState(finder: LyricsFinder, settings: LyricsSettings) =
    shown(lyrics, finder.nameOf(source), credit, settings)

private suspend fun shown(text: String, source: String, credit: String?, settings: LyricsSettings) =
    LyricsState.Found(text, parseLyrics(text).coarsenedTo(settings.sync), source, credit)

/** Name of the source with this [id], the id when the source is gone. */
private fun LyricsFinder.nameOf(id: String): String = source(id)?.name ?: id

/**
 * The lyrics of [item], with who provides them under them. Timed lyrics follow the song, see
 * [SyncedLyricsList]. They load when the page shows, see [loadLyrics]. When the chosen source has
 * none, a button looks in the other sources; when a source can't be reached, a button tries
 * again.
 *
 * @param isPlaying whether [item] is playing, timed lyrics follow it while it does
 * @param scope stores the lyrics found, the page may be gone by then
 */
@Composable
internal fun LyricsPage(
    item: MediaItem,
    isPlaying: Boolean,
    controller: PlaybackController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    songs: SongRepository = koinInject(),
    finder: LyricsFinder = koinInject(),
    scope: CoroutineScope = koinInject(),
) {
    var attempt by remember(item.mediaId) { mutableIntStateOf(0) }
    var everywhere by remember(item.mediaId) { mutableStateOf(false) }
    val state by produceState<LyricsState>(LyricsState.Loading, item.mediaId, attempt, everywhere) {
        value = LyricsState.Loading
        val query = item.lyricsQuery()
        value = if (query == null)
            LyricsState.Missing
        else
            loadLyrics(
                query = query,
                settings = LyricsSettings.current(),
                everywhere = everywhere,
                finder = finder,
                stored = { songs.lyricsOf(item.mediaId).first() },
            ) { lyrics ->
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
    }

    when (val shown = state) {
        is LyricsState.Found -> {
            val footer = listOfNotNull("From ${shown.source}", shown.credit).joinToString("\n")
            val content = shown.content
            if (content is SyncedLyrics)
                SyncedLyricsList(content, footer, isPlaying, controller, contentPadding, modifier)
            else
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(contentPadding)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = content.text,
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
                    LyricsState.Missing -> Message("No lyrics for this song")
                    is LyricsState.Unavailable -> {
                        Message("${shown.source} has no lyrics for this song. Look in the other sources?")
                        Button(onClick = { everywhere = true }) { Text("Look elsewhere") }
                    }

                    else -> {
                        Message("Couldn't load the lyrics")
                        TextButton(onClick = { attempt++ }) { Text("Retry") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)
