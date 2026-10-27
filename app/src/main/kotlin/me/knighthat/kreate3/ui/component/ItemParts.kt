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

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import me.knighthat.kreate3.R
import me.knighthat.kreate3.auto.coverSource
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.Providers
import me.knighthat.kreate3.source.local.LocalCover
import me.knighthat.kreate3.source.local.isAlbumArt
import me.knighthat.kreate3.source.youtube.SONG_ARTWORK
import org.koin.compose.koinInject

internal val BADGE_SIZE = 12.dp
private val ROW_THUMBNAIL_SIZE = 48.dp

/** The name of the source an item comes from, as its own strings say it. */
@get:StringRes
internal val Origin.sourceRes: Int
    get() = when (this) {
        Origin.LOCAL -> R.string.source_local
        Origin.YOUTUBE -> R.string.source_ytm
        Origin.OPEN_SUBSONIC -> R.string.source_opensubsonicapi
    }

/** The icon of the origin, on item badges and origin chips. */
@get:DrawableRes
internal val Origin.icon: Int
    get() = when (this) {
        Origin.LOCAL -> R.drawable.mobile
        Origin.YOUTUBE -> R.drawable.youtube_music
        Origin.OPEN_SUBSONIC -> R.drawable.open_subsonic
    }

/**
 * What Coil loads for the picture of an item, `null` when it has none. See [Thumbnail] for the
 * parameters. A queued song's cover read through [me.knighthat.kreate3.auto.ArtworkProvider] is
 * loaded from where it really is, see [coverSource].
 */
fun thumbnailModel(uri: Uri?, localSongId: Long?): Any? {
    val cover = uri?.coverSource()
    return if (localSongId != null && (cover == null || cover.isAlbumArt()))
        LocalCover(localSongId, cover)
    else cover
}

/** Key a picture of [model] has in Coil's memory cache, `null` when it can't be told. */
internal fun memoryCacheKey(model: Any): String? = when (model) {
    is LocalCover -> model.cacheKey
    is Uri -> model.toString()
    else -> null
}

/**
 * What Coil can load for the cover of the song [item], in the order to try. A song on this
 * device has its [LocalCover]. Any other song has what its ids and picture lead to at its source
 * first ([me.knighthat.kreate3.provider.Provider.songArtworkUris]), the item's `artworkUri` last: a
 * list may give none, like the songs of an album, or one that doesn't load anymore. A cover that
 * is a file on the device, like a downloaded song's, comes first instead.
 */
fun songThumbnailModels(
    item: MediaItem,
    providers: Providers,
    size: Int = SONG_ARTWORK,
): List<Any> {
    val metadata = item.mediaMetadata
    // Queued songs carry the provider's address of their cover, the app reads the original
    val cover = metadata.artworkUri?.coverSource()
    item.localSongId?.let { return listOfNotNull(thumbnailModel(cover, it)) }
    val origin = item.origin ?: return listOfNotNull(cover)

    val albumId = metadata.extras?.getString(EXTRA_ALBUM_ID)
    val fromIds = providers[origin].songArtworkUris(item.mediaId, albumId, size, cover)
    // A cover kept on the device, like a downloaded song's, loads offline without waiting for
    // the source's to fail
    if (cover?.scheme == "file") return (listOf(cover) + fromIds).distinct()
    return (fromIds + listOfNotNull(cover)).distinct()
}

/**
 * [songThumbnailModels] of [item], none for no item.
 *
 * @param size edge in pixels the picture shows at, for sources that have several sizes
 */
@Composable
fun rememberSongThumbnails(item: MediaItem?, size: Int = SONG_ARTWORK): List<Any> {
    val providers = koinInject<Providers>()
    return remember(item, providers, size) {
        item?.let { songThumbnailModels(it, providers, size) }.orEmpty()
    }
}

/**
 * Id in the media index when this is a song on this device, `null` otherwise.
 */
val MediaItem.localSongId: Long?
    get() = mediaId.toLongOrNull().takeIf {
        isLocal && mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_MUSIC
    }

/**
 * Cover or picture of an item. The size comes from [modifier]. While it loads, a light band sweeps
 * across a plain background; when there is no picture or it can't be loaded, a picture of a broken frame shows.
 *
 * @param localSongId id in the media index when this is the cover of a song on this device. The
 * picture inside the song's file is shown unless [uri] is something other than an album cover,
 * like a picture the user picked, which always wins.
 */
@Composable
fun Thumbnail(
    uri: Uri?,
    shape: Shape,
    modifier: Modifier = Modifier,
    localSongId: Long? = null,
) = Thumbnail(listOfNotNull(thumbnailModel(uri, localSongId)), shape, modifier)

/**
 * Like the other [Thumbnail], with several pictures to try. One that Coil has in memory shows
 * first, wherever it is in [models], so nothing is loaded for it. The others are loaded in the
 * order of [models], the next one when one fails.
 *
 * @param placeholder drawn instead of the broken frame when [models] is empty; a picture that
 * can't be loaded still shows the broken frame
 */
@Composable
fun Thumbnail(
    models: List<Any>,
    shape: Shape,
    modifier: Modifier = Modifier,
    placeholder: (@Composable BoxScope.() -> Unit)? = null,
) {
    val context = LocalContext.current
    val order = remember(models) {
        val memory = SingletonImageLoader.get(context).memoryCache
        val cached = models.firstOrNull { model ->
            memoryCacheKey(model)?.let { memory?.get(MemoryCache.Key(it)) } != null
        }
        if (cached == null) models else listOf(cached) + (models - cached)
    }
    var failed by remember(order) { mutableIntStateOf(0) }
    val model = order.getOrNull(failed)
    var loaded by remember(model) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null && placeholder != null && order.isEmpty())
            placeholder()
        else if (model == null)
            Image(
                painter = painterResource(R.drawable.image_not_available),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize(),
            )
        else {
            if (!loaded) LoadingShimmer(Modifier.matchParentSize())
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { failed++ },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * Light band that sweeps diagonally across a [Thumbnail] while its picture loads.
 */
@Composable
private fun LoadingShimmer(modifier: Modifier = Modifier) {
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val progress by rememberInfiniteTransition(label = "thumbnail loading").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "thumbnail shimmer progress",
    )
    Box(
        modifier.drawBehind {
            // How far the band moves sideways to go from fully before the box to fully past it
            val travel = (size.width * size.width + size.height * size.height) / size.width
            val x = -travel + progress * 2 * travel
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, highlight, Color.Transparent),
                    start = Offset(x, 0f),
                    end = Offset(x + size.width, size.height),
                ),
            )
        },
    )
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
    size: Dp = BADGE_SIZE,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/**
 * The badges that apply to [item]: where it comes from, explicit content, a downloaded or cached
 * song, a liked song, a bookmarked album, a followed artist.
 *
 * @param reserveHeight keep the height of the badges when there are none, so items next to each
 * other in a grid keep their titles on the same line
 * @param horizontalArrangement where the badges sit in the width they are given
 * @param showReactions whether the user's like, bookmark or follow shows, `false` where buttons
 * show it already
 * @param size the edge of each badge, larger where the item is shown large like in the player
 */
@Composable
fun ItemBadges(
    item: MediaItem,
    modifier: Modifier = Modifier,
    reserveHeight: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp),
    showReactions: Boolean = true,
    size: Dp = BADGE_SIZE,
) {
    val hasBadges = if (showReactions) item.hasBadges else item.origin != null || item.isExplicit
    if (!hasBadges && !reserveHeight) return

    Row(
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.heightIn(min = size),
    ) {
        item.origin?.let { OriginBadge(it, size) }
        if (item.isExplicit) ExplicitBadge(size)
        StorageBadge(item.storage, size)
        if (!showReactions) return@Row
        if (item.isLiked) ItemBadge(R.drawable.favorite, "Favorite", size = size)
        if (item.isBookmarked) ItemBadge(R.drawable.bookmark, "Bookmarked", size = size)
        if (item.isFollowed) ItemBadge(R.drawable.notifications, "Following", size = size)
    }
}

/**
 * The [ItemBadge] of where an item comes from.
 */
@Composable
fun OriginBadge(origin: Origin, size: Dp = BADGE_SIZE) = ItemBadge(
    icon = origin.icon,
    contentDescription = when (origin) {
        Origin.LOCAL -> stringResource(R.string.semantic_on_this_device)
        Origin.YOUTUBE ->
            stringResource(R.string.semantic_from_youtube, stringResource(R.string.source_ytm))

        Origin.OPEN_SUBSONIC ->
            stringResource(
                R.string.semantic_from_opensubsonic,
                stringResource(R.string.source_opensubsonicapi),
            )
    },
    size = size,
)

/**
 * The [ItemBadge] of explicit content.
 */
@Composable
fun ExplicitBadge(size: Dp = BADGE_SIZE) = ItemBadge(
    icon = R.drawable.explicit,
    contentDescription = stringResource(R.string.semantic_explicit),
    tint = MaterialTheme.colorScheme.error,
    size = size,
)

/**
 * An item as a row: cover, then the title with its badges above and the subtitle below.
 * [overlineEnd] sits on the line of the badges, at its end. [trailing] sits at the end, level
 * with the bottom of the text. The cover is left out when [showThumbnail] is `false`, the text
 * then starts at the edge.
 *
 * @param thumbnails pictures to try for the cover, see [Thumbnail], the item's own by default
 * @param thumbnailOverlay drawn over the cover, in a box of its size; alone in that box when the
 * cover is left out
 * @param onLongClick what a long press does, nothing when `null`
 */
@Composable
fun ItemRow(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showThumbnail: Boolean = true,
    thumbnails: List<Any>? = null,
    overlineEnd: String? = null,
    thumbnailOverlay: (@Composable BoxScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val metadata = item.mediaMetadata
    val subtitle = metadata.subtitle ?: metadata.artist

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        if (showThumbnail)
            Box(modifier = Modifier.size(ROW_THUMBNAIL_SIZE)) {
                Thumbnail(
                    models = thumbnails
                        ?: listOfNotNull(thumbnailModel(metadata.artworkUri, item.localSongId)),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.matchParentSize(),
                )
                thumbnailOverlay?.invoke(this)
            }
        else if (thumbnailOverlay != null)
            Box(modifier = Modifier.size(ROW_THUMBNAIL_SIZE), content = thumbnailOverlay)
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

//<editor-fold defaultstate="collapsed" desc="Previews">
@ComponentPreviews
@Composable
private fun ThumbnailPreview() = PreviewTheme {
    val shape = MaterialTheme.shapes.small

    Surface {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            // No picture
            Thumbnail(uri = null, shape = shape, modifier = Modifier.size(64.dp))
            Thumbnail(
                models = emptyList(),
                shape = shape,
                modifier = Modifier.size(64.dp),
                placeholder = {
                    Icon(painterResource(R.drawable.queue_music), contentDescription = null)
                },
            )
            // While it loads
            Box(
                Modifier
                    .size(64.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                LoadingShimmer(Modifier.matchParentSize())
            }
        }
    }
}

@ComponentPreviews
@Composable
private fun ItemBadgesPreview() = PreviewTheme {
    val items = Origin.entries.map { previewItem(it.name, "", origin = it) } + listOf(
        previewItem("Explicit", "", explicit = true),
        previewItem("Liked", "", mediaType = MediaMetadata.MEDIA_TYPE_MUSIC, rated = true),
        previewItem("Bookmarked", "", mediaType = MediaMetadata.MEDIA_TYPE_ALBUM, rated = true),
        previewItem("Followed", "", mediaType = MediaMetadata.MEDIA_TYPE_ARTIST, rated = true),
        previewItem(
            "Everything",
            "",
            origin = Origin.YOUTUBE,
            explicit = true,
            mediaType = MediaMetadata.MEDIA_TYPE_MUSIC,
            rated = true,
        ),
    )

    Surface {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            items.forEach { ItemBadges(it) }
            // As large as in the player
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Origin.entries.forEach { OriginBadge(it, size = 24.dp) }
                ExplicitBadge(size = 24.dp)
            }
        }
    }
}

@ComponentPreviews
@Composable
private fun ItemRowPreview() = PreviewTheme {
    val item = previewItem("Title", "Subtitle")

    Surface {
        Column(modifier = Modifier.width(360.dp)) {
            ItemRow(item, onClick = {})
            ItemRow(
                item,
                onClick = {},
                overlineEnd = "At the end of the badges' line",
                trailing = { Text("Trailing", style = MaterialTheme.typography.labelSmall) },
            )
            ItemRow(item, onClick = {}, showThumbnail = false)
        }
    }
}
//</editor-fold>
