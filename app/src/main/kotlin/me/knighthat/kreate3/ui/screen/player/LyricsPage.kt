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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.provider.youtube.YouTubeLyrics
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.ui.component.origin
import org.koin.compose.koinInject

private const val TAG = "LyricsPage"

/** What the lyrics page shows. */
internal sealed interface LyricsState {

    data object Loading : LyricsState

    /** @property source who provides the lyrics, `null` when unknown */
    data class Found(val text: String, val source: String?) : LyricsState

    /** The song has no lyrics, or none that can be found. */
    data object Missing : LyricsState

    /** The source couldn't be reached. */
    data object Failed : LyricsState
}

/**
 * Lyrics of [item]: the ones stored, otherwise YouTube's for a song of YouTube, which [save] then
 * stores. A song of another origin has only the ones stored.
 */
internal suspend fun loadLyrics(
    item: MediaItem,
    songs: SongRepository,
    fetchYouTube: suspend (songId: String) -> YouTubeLyrics?,
    save: (Lyrics) -> Unit,
): LyricsState {
    val songId = item.mediaId
    songs.lyricsOf(songId).first()?.let { return LyricsState.Found(it.lyrics, null) }
    if (item.origin != Origin.YOUTUBE) return LyricsState.Missing

    val fetched = try {
        fetchYouTube(songId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't fetch the lyrics of $songId", e)
        return LyricsState.Failed
    } ?: return LyricsState.Missing

    save(fetched.lyrics)
    return LyricsState.Found(fetched.lyrics.lyrics, fetched.source)
}

/**
 * The lyrics of [item] in large text, with who provides them under it when that is known. They
 * load when the page shows, see [loadLyrics]; when the source can't be reached, a button tries
 * again.
 *
 * @param scope stores the lyrics found, the page may be gone by then
 */
@Composable
internal fun LyricsPage(
    item: MediaItem,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    songs: SongRepository = koinInject(),
    youTube: YouTubeProvider = koinInject(),
    scope: CoroutineScope = koinInject(),
) {
    var attempt by remember(item.mediaId) { mutableIntStateOf(0) }
    val state by produceState<LyricsState>(LyricsState.Loading, item.mediaId, attempt) {
        value = LyricsState.Loading
        value = loadLyrics(item, songs, youTube::lyrics) { lyrics ->
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
        is LyricsState.Found -> Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = shown.text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (shown.source != null) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = shown.source,
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
            when (shown) {
                LyricsState.Loading -> CircularProgressIndicator()
                LyricsState.Missing -> Message("No lyrics for this song")
                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Message("Couldn't load the lyrics")
                    TextButton(onClick = { attempt++ }) { Text("Retry") }
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
