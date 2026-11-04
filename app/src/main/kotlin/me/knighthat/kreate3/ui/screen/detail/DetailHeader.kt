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

import android.content.res.Resources
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.R
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.ExplicitBadge
import me.knighthat.kreate3.ui.component.OriginBadge
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.Thumbnail

/** Lines of [DetailDescription] shown until it is opened. */
private const val COLLAPSED_LINES = 3

/** Edge of each badge above the [DetailHeader]'s picture. */
private val BADGE_SIZE = 20.dp

/**
 * The top of a detail screen: the item's badges, a large picture under them and a line under
 * that, centered. The top bar already shows the name, its buttons the bookmark or follow.
 *
 * @param origin where the item comes from, shown as a badge, none when `null`
 * @param isExplicit whether the explicit badge shows
 */
@Composable
fun DetailHeader(
    uri: Uri?,
    subtitle: String?,
    shape: Shape,
    origin: Origin?,
    modifier: Modifier = Modifier,
    isExplicit: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        if (origin != null || isExplicit) Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            origin?.let { OriginBadge(it, BADGE_SIZE) }
            if (isExplicit) ExplicitBadge(BADGE_SIZE)
        }
        Thumbnail(
            uri = uri,
            shape = shape,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .size(200.dp),
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * What the source says about the item, under the [DetailHeader]. It shows the first few lines,
 * a tap shows all of it or the first lines again. "More" only shows when the text is longer.
 *
 * @param color of the text, [linkColor] of "More", for a description on a colored header
 */
@Composable
fun DetailDescription(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    linkColor: Color = MaterialTheme.colorScheme.primary,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    // Heights in px of the text laid out whole and cut to COLLAPSED_LINES. Collapsing keeps the
    // whole text and animates a height limit down to the cut one, so the text and the list below
    // move together; cutting the lines at once would hide them before the box could shrink.
    var fullHeight by remember { mutableIntStateOf(0) }
    var collapsedHeight by remember { mutableIntStateOf(0) }
    var collapsing by remember { mutableStateOf(false) }
    var limit by remember { mutableFloatStateOf(0f) }

    // Collapsing starts in the tap itself (collapsing, limit), not in an effect: one frame with
    // the text already cut would show before the effect ran.
    LaunchedEffect(collapsing) {
        if (collapsing) {
            animate(
                initialValue = fullHeight.toFloat(),
                targetValue = collapsedHeight.toFloat(),
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            ) { value, _ -> limit = value }
            collapsing = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (collapsing) Modifier
                else Modifier.animateContentSize(
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = overflows || expanded,
            ) {
                if (collapsing) {
                    collapsing = false
                    expanded = true
                } else if (expanded && collapsedHeight in 1 until fullHeight) {
                    limit = fullHeight.toFloat()
                    collapsing = true
                    expanded = false
                } else {
                    expanded = !expanded
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = if (expanded || collapsing) Int.MAX_VALUE else COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = if (collapsing) {
                // clipToBounds first: it clips to the size the layout below it reports
                Modifier
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val height = minOf(placeable.height, limit.toInt())
                        layout(placeable.width, height) { placeable.place(0, 0) }
                    }
            } else {
                Modifier
            },
            onTextLayout = {
                if (expanded || collapsing) {
                    fullHeight = it.size.height
                    collapsedHeight =
                        it.getLineBottom(minOf(COLLAPSED_LINES, it.lineCount) - 1).toInt()
                } else {
                    overflows = it.hasVisualOverflow
                }
            },
        )
        if (overflows || expanded)
            Crossfade(
                targetState = expanded,
                animationSpec = tween(200),
                label = "descriptionToggle"
            ) { isExpanded ->
                Text(
                    text = if (isExpanded) stringResource(R.string.action_less) else stringResource(
                        R.string.action_more
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = linkColor,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
    }
}

/**
 * A song of an album's tracklist: its [number] where a list's cover would be, no album, which the
 * page is.
 */
@Composable
internal fun AlbumTrack(item: MediaItem, number: Int?, onClick: () -> Unit) = SongItem(
    item = item,
    onClick = onClick,
    showThumbnail = false,
    showAlbum = false,
    // The page says where they come from already
    showBadges = false,
    thumbnailOverlay = {
        number?.let {
            Text(
                it.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    },
)

/** The heading of a disc of an album with several. */
@Composable
internal fun DiscHeader(number: Int, modifier: Modifier = Modifier) = Text(
    stringResource(R.string.section_header_disc, number),
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
)

/** `12 songs · 47 minutes`, the length left out when it isn't known. */
internal fun Resources.albumSummary(songs: Int, totalMs: Long?): String = listOfNotNull(
    getQuantityString(R.plurals.song, songs, songs),
    totalMs?.takeIf { it > 0 }?.let {
        val minutes = ((it + 30_000) / 60_000).toInt()
        getQuantityString(R.plurals.minute, minutes, minutes)
    },
).joinToString(" · ")

/**
 * `Artist · 2004`, the artist in [linkColor] and tappable when [onOpenArtist] is given. Shared by
 * the album page designs.
 */
@Composable
internal fun ArtistLine(
    artists: String?,
    year: Int?,
    onOpenArtist: (() -> Unit)?,
    linkColor: Color,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) = Row(verticalAlignment = Alignment.CenterVertically) {
    artists?.let {
        Text(
            it,
            style = MaterialTheme.typography.titleSmall,
            color = if (onOpenArtist != null) linkColor else color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .then(
                    if (onOpenArtist != null)
                        Modifier.clickable(onClick = onOpenArtist)
                    else
                        Modifier
                ),
        )
    }
    year?.let {
        Text(
            if (artists != null) " · $it" else it.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = color,
        )
    }
}
