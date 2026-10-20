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

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.lyrics.LyricsLine
import me.knighthat.kreate3.lyrics.SyncedLyrics
import me.knighthat.kreate3.playback.PlaybackController

/** Opacity of the lines, and the words of a line, that aren't being sung. */
private const val IDLE_ALPHA = .45f

/** Part of the page's height above the line being sung once it is scrolled to. */
private const val FOCUS_FRACTION = 1f / 3

/**
 * [lyrics] following the song: the lines being sung are lit, their words fill from left to right
 * as they are sung when the lines are timed per word. The list keeps the line being sung a third
 * down the page, unless the user is dragging it. Tapping a line plays the song from it.
 *
 * @param footer shown under the last line, like who provides the lyrics
 */
@Composable
internal fun SyncedLyricsList(
    lyrics: SyncedLyrics,
    footer: String,
    isPlaying: Boolean,
    controller: PlaybackController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val lines = lyrics.lines
    // Where a line stops being sung, a line without an end lasts until the next main line
    val ends = remember(lyrics) {
        lines.mapIndexed { i, line ->
            line.end?.inWholeMilliseconds
                ?: lines.subList(i + 1, lines.size).firstOrNull { !it.background }
                    ?.start?.inWholeMilliseconds
                ?: Long.MAX_VALUE
        }
    }

    var position by remember { mutableLongStateOf(controller.position()) }
    // Every frame while playing, once when it pauses or the lyrics change
    LaunchedEffect(lyrics, isPlaying) {
        while (true) {
            position = controller.position()
            if (!isPlaying) break
            withFrameNanos { }
        }
    }

    val sung by remember(lyrics) {
        derivedStateOf {
            lines.indices.filter { lines[it].start.inWholeMilliseconds <= position && position < ends[it] }
                .toSet()
        }
    }
    // The last main line that started, it stays in view during a pause between lines
    val focus by remember(lyrics) {
        derivedStateOf {
            lines.indexOfLast { !it.background && it.start.inWholeMilliseconds <= position }
                .coerceAtLeast(0)
        }
    }

    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(focus) {
        if (dragged) return@LaunchedEffect
        val target = (listState.layoutInfo.viewportSize.height * FOCUS_FRACTION).toInt()
        val shown = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == focus }
        if (shown != null)
            listState.animateScrollBy((shown.offset - target).toFloat())
        else {
            listState.scrollToItem(focus)
            listState.scrollBy(-target.toFloat())
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(lines) { i, line ->
            LyricsLineText(
                line = line,
                isSung = i in sung,
                lineEnd = ends[i],
                position = { position },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val start = line.start.inWholeMilliseconds
                        controller.seekTo(start)
                        position = start
                    }
                    .padding(horizontal = 24.dp, vertical = if (line.background) 2.dp else 10.dp),
            )
        }
        item {
            Text(
                text = footer,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )
        }
    }
}

/**
 * A line of lyrics, lit while [isSung]. A line timed per word is drawn dim, then lit again up to
 * where the song is, read from [position] while drawing so only this line redraws every frame.
 */
@Composable
private fun LyricsLineText(
    line: LyricsLine,
    isSung: Boolean,
    lineEnd: Long,
    position: () -> Long,
    modifier: Modifier = Modifier,
) {
    val lit = MaterialTheme.colorScheme.onSurface
    val filling = isSung && line.words.isNotEmpty()
    val color by animateColorAsState(
        if (isSung && !filling) lit else lit.copy(alpha = IDLE_ALPHA),
        label = "lyrics line",
    )
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val text =
        if (line.words.isEmpty()) line.text.ifEmpty { "♪" } else line.words.joinToString("") { it.text }

    Text(
        text = text,
        style = if (line.background)
            MaterialTheme.typography.titleMedium
        else
            MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val shown = layout
            if (!filling || shown == null) return@drawWithContent
            val (sungLine, sungX) = sungUpTo(line, lineEnd, position(), shown)
                ?: return@drawWithContent

            for (visual in 0..sungLine) {
                val right = if (visual < sungLine) size.width else sungX
                clipRect(0f, shown.getLineTop(visual), right, shown.getLineBottom(visual)) {
                    drawText(shown, color = lit)
                }
            }
        },
    )
}

/**
 * Where the song is in [line], laid out as [layout]: the visual line and the x the words are sung
 * up to, part of a word filled as far as the song is into it.
 *
 * @return `null` before the first word
 */
private fun sungUpTo(
    line: LyricsLine,
    lineEnd: Long,
    at: Long,
    layout: TextLayoutResult
): Pair<Int, Float>? {
    val words = line.words
    val index = words.indexOfLast { it.start.inWholeMilliseconds <= at }
    if (index < 0) return null

    val word = words[index]
    val start = word.start.inWholeMilliseconds
    val end = word.end?.inWholeMilliseconds
        ?: words.getOrNull(index + 1)?.start?.inWholeMilliseconds
        ?: lineEnd.takeIf { it != Long.MAX_VALUE }
        ?: (start + 1)
    val fraction = ((at - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)

    val first = words.subList(0, index).sumOf { it.text.length }
    val length = word.text.trimEnd().length
    val left = layout.getBoundingBox(first)
    val visual = layout.getLineForOffset(first)
    // Only a space, the words before it are sung
    if (length == 0) return visual to left.left

    val last = first + length - 1
    val right = layout.getBoundingBox(last)
    val x = if (layout.getLineForOffset(last) == visual)
        left.left + (right.right - left.left) * fraction
    else
    // A word too long for the width wraps, fill its first part
        left.left + (layout.getLineRight(visual) - left.left) * fraction
    return visual to x
}
