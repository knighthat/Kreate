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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Height of a bar standing in for a line of text. */
private val TEXT_BAR_HEIGHT = 12.dp

/**
 * A plain shape with a light band sweeping across it, standing in for something still loading.
 * The size comes from [modifier].
 */
@Composable
fun LoadingBlock(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.small) =
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        LoadingShimmer(Modifier.matchParentSize())
    }

/** A bar of the height of a line of text, [fraction] of the width it is given. */
@Composable
private fun TextBar(fraction: Float, modifier: Modifier = Modifier) =
    LoadingBlock(
        modifier
            .fillMaxWidth(fraction)
            .height(TEXT_BAR_HEIGHT),
        shape = MaterialTheme.shapes.extraSmall,
    )

/** Stands in for an [ItemRow], like a song, while the list loads: a cover and two lines. */
@Composable
fun ItemRowPlaceholder(modifier: Modifier = Modifier) =
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        LoadingBlock(Modifier.size(ROW_THUMBNAIL_SIZE))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            TextBar(0.6f)
            TextBar(0.35f)
        }
    }

/** Stands in for a few lines of text, like a description, while it loads. */
@Composable
fun TextPlaceholder(modifier: Modifier = Modifier, lines: Int = 3) =
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        repeat(lines) { line ->
            // The last line ends early, like a paragraph's
            TextBar(if (line == lines - 1) 0.5f else 1f)
        }
    }

/**
 * Stands in for a row of cards, like albums, while it loads: [cards] of [cardWidth], round ones
 * for artists. Those past the edge are cut off like the row's own.
 */
@Composable
fun CardRowPlaceholder(
    cardWidth: Int,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    cards: Int = 4,
) =
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(horizontal = 16.dp),
    ) {
        repeat(cards) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.width(cardWidth.dp),
            ) {
                LoadingBlock(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = if (round) CircleShape else MaterialTheme.shapes.small,
                )
                TextBar(0.8f)
            }
        }
    }

//<editor-fold defaultstate="collapsed" desc="Previews">
@ComponentPreviews
@Composable
private fun PlaceholdersPreview() = PreviewTheme {
    Surface {
        Column(modifier = Modifier.width(360.dp)) {
            TextPlaceholder()
            ItemRowPlaceholder()
            ItemRowPlaceholder()
            CardRowPlaceholder(cardWidth = 104, modifier = Modifier.padding(vertical = 8.dp))
            CardRowPlaceholder(cardWidth = 104, round = true)
        }
    }
}
//</editor-fold>
