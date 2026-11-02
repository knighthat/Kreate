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

import android.net.Uri
import androidx.annotation.PluralsRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.BackButton
import me.knighthat.kreate3.ui.component.Blur
import me.knighthat.kreate3.ui.component.BlurredCover
import me.knighthat.kreate3.ui.component.ConfigEntry
import me.knighthat.kreate3.ui.component.ConfigSheet
import me.knighthat.kreate3.ui.component.LabeledIconButton
import me.knighthat.kreate3.ui.component.LabeledIconToggle
import me.knighthat.kreate3.ui.component.LocalTooltipSide
import me.knighthat.kreate3.ui.component.MoreSheetButton
import me.knighthat.kreate3.ui.component.OriginBadge
import me.knighthat.kreate3.ui.component.QueueAction
import me.knighthat.kreate3.ui.component.Thumbnail
import me.knighthat.kreate3.ui.component.TooltipSide
import me.knighthat.kreate3.ui.component.artistPictureKey
import me.knighthat.kreate3.ui.component.rememberContentsAfterPicture
import me.knighthat.kreate3.ui.component.rememberSharedPicture
import me.knighthat.kreate3.ui.component.thumbnailModel
import me.knighthat.kreate3.ui.navigation.LocalArtist

/** Share of the screen's height the artist's picture takes at the top, before it scrolls. */
private const val PICTURE_HEIGHT = 0.4f

/** Share of the picture's height, from the top, before it starts to fade into the page. */
private const val PICTURE_FADE_START = 0.6f

/** The picture once the list reached the bar: a few pixels stretched, smoothed where Android can. */
private val PICTURE_BLUR = Blur(sampleSize = 32, radius = 24.dp)

/** How dark the top of the bar is, so its title and buttons read on any picture. */
private const val BAR_SHADE_ALPHA = 0.7f

/** How far down from the bar the list fades in, its items dissolve there on their way up. */
private val LIST_FADE = 32.dp

/** Edge of the badges under the name in the bar. */
private val BADGE_SIZE = 16.dp

/** Edge of the artist icon shown while there is no picture. */
private val PLACEHOLDER_SIZE = 96.dp

/** Rows standing in for an artist's top songs while they load. */
internal const val SONG_PLACEHOLDERS = 5

/**
 * What the card or row an artist was opened from showed of them, see
 * [me.knighthat.kreate3.ui.navigation.artistRoute]. Their page shows it until it has its own.
 *
 * @property picture the picture as the card loaded it, the top of the page grows from it
 */
data class OpenedArtist(val name: String?, val picture: Uri?)

/** The [OpenedArtist] in the arguments of an artist's screen, nothing when opened by id alone. */
internal fun SavedStateHandle.openedArtist(): OpenedArtist =
    OpenedArtist(get<String>(LocalArtist.NAME), get<String>(LocalArtist.PICTURE)?.toUri())

/**
 * The frame of an artist's page: their picture across the top, [PICTURE_HEIGHT] of the screen,
 * under a see-through top bar shaded at its top, with the [name], their badges under it, and a ⋮
 * button opening [menu]. The list ([content]) starts under the picture and scrolls over it, see
 * through, fading out just under the bar; the picture blurs more the farther it went up, fully blurred once
 * the list's top reaches the bar.
 *
 * @param artistId whose page it is, the picture of their card grows into the top as it opens
 * @param standIn the picture the artist was opened with, see [OpenedArtist]: in memory already,
 * it shows while [thumbnail] loads, which then fades in over it, and stands in for it when it fails
 * @param origin where the artist comes from, a badge under the name, none when `null`
 * @param subtitle shown under the name in place of the badges, like YouTube's monthly audience
 * @param menu the sheet the ⋮ button opens, given what closes it; `null` hides the button
 * @param contentPadding room the list adds around its items, e.g. so the last one can scroll
 * clear of what floats over the screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScaffold(
    name: String,
    artistId: String,
    thumbnail: Uri?,
    origin: Origin?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    standIn: Uri? = null,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
    menu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val background = MaterialTheme.colorScheme.surfaceDim
    val barHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            TopAppBarDefaults.TopAppBarExpandedHeight
    val direction = LocalLayoutDirection.current

    // No bar of its own, the insets it leaves are the room under the list
    Scaffold(
        containerColor = background,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        modifier = modifier,
    ) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val pictureHeight = maxHeight * PICTURE_HEIGHT
            val pictureSide = max(maxWidth, pictureHeight)
            // The list starts under the bar, its first item fills the rest of the picture
            val pictureBelowBar = (pictureHeight - barHeight).coerceAtLeast(0.dp)
            val picture = rememberSharedPicture(
                artistPictureKey(artistId),
                corners = 0,
                // Round on their card
                otherCorners = 50,
            )
            // Everything but the picture, shown once it landed, see pictureFirst
            val contents = rememberContentsAfterPicture()
            val models = remember(thumbnail, standIn) {
                listOfNotNull(thumbnailModel(thumbnail, null), thumbnailModel(standIn, null))
                    .distinct()
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(pictureHeight)
            ) {
                Thumbnail(
                    models = models,
                    shape = RectangleShape,
                    modifier = Modifier
                        .matchParentSize()
                        .then(picture.modifier),
                    // The card's picture is smaller, it only stands in
                    minCachedSize = pictureSide,
                    placeholder = {
                        Icon(
                            painterResource(R.drawable.artist),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(PLACEHOLDER_SIZE),
                        )
                    },
                )
                BlurredCover(
                    models = models,
                    blur = PICTURE_BLUR,
                    modifier = Modifier
                        .matchParentSize()
                        .then(contents)
                        // More of it the farther the list went up, all of it once at the bar
                        .graphicsLayer {
                            val scrolled =
                                if (listState.firstVisibleItemIndex > 0) {
                                    1f
                                } else {
                                    listState.firstVisibleItemScrollOffset /
                                            pictureBelowBar.toPx().coerceAtLeast(1f)
                                }
                            alpha = scrolled.coerceIn(0f, 1f)
                        },
                )
                // Its bottom fades into the page the list starts on
                Box(
                    Modifier
                        .matchParentSize()
                        .then(picture.overlay)
                        .then(contents)
                        .background(
                            Brush.verticalGradient(
                                PICTURE_FADE_START to Color.Transparent,
                                1f to background,
                            )
                        )
                )
            }

            LazyColumn(
                state = listState,
                contentPadding = contentPadding + PaddingValues(
                    start = insets.calculateStartPadding(direction),
                    end = insets.calculateEndPadding(direction),
                    bottom = insets.calculateBottomPadding(),
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = barHeight)
                    .then(contents)
                    .fadeTop(LIST_FADE),
            ) {
                item(key = "picture") {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(pictureBelowBar)
                    )
                }
                content()
            }

            ArtistTopBar(
                name = name,
                origin = origin,
                subtitle = subtitle,
                onBack = onBack,
                onMore = if (menu != null) ({ menuOpen = true }) else null,
                modifier = picture.overlay.then(contents),
            )
        }
    }

    if (menuOpen && menu != null) menu { menuOpen = false }
}

/** Fades what this draws in over [height] from its top: see-through at the edge, whole below. */
private fun Modifier.fadeTop(height: Dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black,
                    endY = height.toPx(),
                ),
                blendMode = BlendMode.DstIn,
            )
        }

/** The see-through bar over the picture, shaded from its top so it reads on any picture. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistTopBar(
    name: String,
    origin: Origin?,
    subtitle: String?,
    onBack: () -> Unit,
    onMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
) = CompositionLocalProvider(LocalTooltipSide provides TooltipSide.BELOW) {
    TopAppBar(
        title = {
            Column {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    origin?.let {
                        Row { OriginBadge(it, BADGE_SIZE, tint = Color.White) }
                    }
                }
            }
        },
        navigationIcon = { BackButton(onBack) },
        actions = { onMore?.let { MoreSheetButton(it) } },
        // Light on the shade in either theme
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            navigationIconContentColor = Color.White,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = BAR_SHADE_ALPHA),
                1f to Color.Transparent,
            )
        ),
    )
}

/**
 * The sheet of an artist's ⋮ button: Play, Follow and Share as buttons, then Shuffle, Play next,
 * Add to queue, Details and Statistics as rows.
 *
 * @param onAction runs a [QueueAction] with the artist's songs, `null` while there are none: the
 * buttons and rows of those are off
 * @param isFollowed whether the artist is followed, `null` turns Follow off, while it isn't known
 * @param share gives a link to the artist, `null` turns Share off, like for an artist of the
 * device
 * @param kind what is shared, the plural `artist`, for the failure message
 * @param onOpenDetails opens what MusicBrainz knows about them, `null` while their name isn't
 * known
 */
@Composable
fun ArtistMenu(
    name: String?,
    onDismiss: () -> Unit,
    onAction: ((QueueAction) -> Unit)?,
    isFollowed: Boolean?,
    onToggleFollow: () -> Unit,
    share: (suspend () -> Result<String>)?,
    onOpenDetails: (() -> Unit)?,
    onOpenStatistics: () -> Unit,
    @PluralsRes kind: Int = R.plurals.unit_artist,
) {
    val linkShare = rememberLinkShare(name, kind) {
        share?.invoke() ?: Result.failure(IllegalStateException("Nothing to share"))
    }

    ConfigSheet(
        onDismiss = onDismiss,
        actions = { close ->
            LabeledIconButton(
                icon = R.drawable.play_arrow,
                label = stringResource(R.string.menu_play),
                enabled = onAction != null,
            ) { close { onAction?.invoke(QueueAction.PLAY) } }
            LabeledIconToggle(
                isSet = isFollowed == true,
                enabled = isFollowed != null,
                setIcon = R.drawable.how_to_reg,
                unsetIcon = R.drawable.person_add,
                label = stringResource(R.string.menu_follow),
                onClick = onToggleFollow,
            )
            LabeledIconButton(
                icon = R.drawable.share,
                label = stringResource(R.string.menu_share),
                enabled = share != null && linkShare.enabled,
                // A link of the server can take a moment, the sheet stays until it is there
            ) { linkShare.share { close {} } }
        },
        entries = { close ->
            ConfigEntry(
                R.drawable.shuffle,
                stringResource(R.string.menu_shuffle),
                enabled = onAction != null,
            ) { close { onAction?.invoke(QueueAction.SHUFFLE) } }
            ConfigEntry(
                R.drawable.queue_play_next,
                stringResource(R.string.menu_play_next),
                enabled = onAction != null,
            ) { close { onAction?.invoke(QueueAction.PLAY_NEXT) } }
            ConfigEntry(
                R.drawable.playlist_add,
                stringResource(R.string.menu_add_to_queue),
                enabled = onAction != null,
            ) { close { onAction?.invoke(QueueAction.ENQUEUE) } }
            ConfigEntry(
                R.drawable.info,
                stringResource(R.string.menu_details),
                enabled = onOpenDetails != null,
            ) { close { onOpenDetails?.invoke() } }
            ConfigEntry(R.drawable.insert_chart, stringResource(R.string.menu_statistics)) {
                close(onOpenStatistics)
            }
        },
    )
}
