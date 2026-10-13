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

package me.knighthat.kreate.ui.component

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import coil3.compose.AsyncImage
import me.knighthat.kreate.R
import me.knighthat.kreate.source.local.LocalCover
import me.knighthat.kreate.source.local.isAlbumArt

private val BADGE_SIZE = 12.dp
private val ROW_THUMBNAIL_SIZE = 48.dp

/**
 * What Coil loads for the picture of an item, `null` when it has none. See [Thumbnail] for the
 * parameters.
 */
fun thumbnailModel(uri: Uri?, localSongId: Long?): Any? =
    if (localSongId != null && (uri == null || uri.isAlbumArt())) LocalCover(localSongId, uri)
    else uri

/**
 * Id in the media index when this is a song on this device, `null` otherwise.
 */
val MediaItem.localSongId: Long?
    get() = mediaId.toLongOrNull().takeIf {
        isLocal && mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_MUSIC
    }

/**
 * Cover or picture of an item. The size comes from [modifier]. While there is no picture, or
 * it cannot be loaded, the first letter of [title] shows on a plain background.
 *
 * @param localSongId id in the media index when this is the cover of a song on this device. The
 * picture inside the song's file is shown unless [uri] is something other than an album cover,
 * like a picture the user picked, which always wins.
 */
@Composable
fun Thumbnail(
    uri: Uri?,
    title: CharSequence?,
    shape: Shape,
    modifier: Modifier = Modifier,
    localSongId: Long? = null,
) {
    val model = thumbnailModel(uri, localSongId)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title?.firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (model != null)
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
    }
}

/**
 * Small icon that tells something about an item, shown above its title.
 */
@Composable
fun ItemBadge(
    @DrawableRes icon: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(BADGE_SIZE),
    )
}

/**
 * The badges that apply to [item]: a file on this device, explicit content, a liked song, a bookmarked album, a followed artist.
 *
 * @param reserveHeight keep the height of the badges when there are none, so items next to each
 * other in a grid keep their titles on the same line
 * @param horizontalArrangement where the badges sit in the width they are given
 */
@Composable
fun ItemBadges(
    item: MediaItem,
    modifier: Modifier = Modifier,
    reserveHeight: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp),
) {
    if (!item.hasBadges && !reserveHeight) return

    Row(
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.heightIn(min = BADGE_SIZE),
    ) {
        if (item.isLocal) ItemBadge(R.drawable.mobile, "On this device")
        if (item.isExplicit) ItemBadge(
            R.drawable.explicit,
            "Explicit",
            tint = MaterialTheme.colorScheme.error
        )
        if (item.isLiked) ItemBadge(R.drawable.favorite, "Favorite")
        if (item.isBookmarked) ItemBadge(R.drawable.bookmark, "Bookmarked")
        if (item.isFollowed) ItemBadge(R.drawable.notifications, "Following")
    }
}

/**
 * An item as a row: cover, then the title with its badges above and the subtitle below.
 * [overlineEnd] sits on the line of the badges, at its end. [trailing] sits at the end, level
 * with the bottom of the text. The cover is left out when [showThumbnail] is `false`, the text
 * then starts at the edge.
 */
@Composable
fun ItemRow(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showThumbnail: Boolean = true,
    overlineEnd: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val metadata = item.mediaMetadata
    val subtitle = metadata.subtitle ?: metadata.artist

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        if (showThumbnail)
            Thumbnail(
                uri = metadata.artworkUri,
                title = metadata.title,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(ROW_THUMBNAIL_SIZE),
                localSongId = item.localSongId,
            )
        // The line above the title spans the width of the trailing content too, so what sits
        // at its end lines up with it
        Column(modifier = Modifier.weight(1f)) {
            if (item.hasBadges || overlineEnd != null)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Keeps the line as tall as with badges when there is only the text
                    ItemBadges(item, reserveHeight = true)
                    if (overlineEnd != null)
                        Text(
                            text = overlineEnd,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metadata.title?.toString().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null)
                        Text(
                            text = subtitle.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                }
                trailing?.invoke()
            }
        }
    }
}

/**
 * Length as `m:ss`, `null` when it isn't known.
 */
fun formatDuration(millis: Long?): String? {
    if (millis == null || millis < 0) return null
    val seconds = millis / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
