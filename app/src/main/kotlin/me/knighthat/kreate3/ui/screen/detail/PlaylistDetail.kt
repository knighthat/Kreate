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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.BackButton
import me.knighthat.kreate3.ui.component.Blur
import me.knighthat.kreate3.ui.component.BlurredCover
import me.knighthat.kreate3.ui.component.ConfigEntry
import me.knighthat.kreate3.ui.component.ConfigSheet
import me.knighthat.kreate3.ui.component.CoverEditMode
import me.knighthat.kreate3.ui.component.CoverGrid
import me.knighthat.kreate3.ui.component.GRID_COVERS
import me.knighthat.kreate3.ui.component.LabeledIconButton
import me.knighthat.kreate3.ui.component.LocalTooltipSide
import me.knighthat.kreate3.ui.component.MoreSheetButton
import me.knighthat.kreate3.ui.component.OriginBadge
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.Thumbnail
import me.knighthat.kreate3.ui.component.TooltipSide
import me.knighthat.kreate3.ui.component.localSongId
import me.knighthat.kreate3.ui.component.previewCover
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.thumbnailModel

/** Share of the screen's width the framed cover takes. */
private const val FRAMED_COVER_WIDTH = 0.62f

/** The field behind the cover: a few pixels of it stretched, heavily blurred. */
private val FIELD_BLUR = Blur(sampleSize = 16, radius = 48.dp)

/** Share of the header's height, from the top, before the field fades into the page. */
private const val FIELD_FADE_START = 0.45f

/** How much the scrim darkens the blurred cover behind the bar, so its title reads. */
private const val BAR_SCRIM_ALPHA = 0.35f

/** How dark the top of the bar is while it is over the field. */
private const val FRAMED_BAR_SHADE_ALPHA = 0.5f

private val FRAMED_BADGE_SIZE = 18.dp

/** Edge of the playlist icon on a cover with nothing to show. */
private val PLACEHOLDER_ICON_SIZE = 72.dp

/**
 * The frame of a playlist's page: its cover as an object, a large rounded square with a shadow,
 * standing on a heavily blurred copy of itself that fades into the page. Under it the name, the
 * [owner] and [summary], then Play and Shuffle. The header scrolls away with the list; the bar is
 * see-through over it and, once the name has gone under it, shows the blurred cover with the name
 * and [summary].
 *
 * @param summary how many songs and how long, `12 songs · 47 minutes`
 *
 * @param models the playlist's own picture, empty for none
 * @param songCovers covers of its songs, in its order, for a playlist without a picture: four of
 * them in a 2 × 2 grid, the first alone when there are fewer, a playlist icon with none
 * @param owner who made the playlist, like a YouTube channel, `null` when unknown
 * @param onPlay plays the playlist, `null` while it has no songs: Play and Shuffle are off
 * @param menu the sheet the ⋮ button opens, given what closes it; `null` hides the button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScaffold(
    name: String,
    models: List<Any>,
    songCovers: List<Any>,
    owner: String?,
    summary: String?,
    origin: Origin?,
    onBack: () -> Unit,
    onPlay: (() -> Unit)?,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
    menu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val background = MaterialTheme.colorScheme.surfaceDim
    val barHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            TopAppBarDefaults.TopAppBarExpandedHeight
    val barHeightPx = with(LocalDensity.current) { barHeight.roundToPx() }
    val direction = LocalLayoutDirection.current
    // Where the name ends in the header, the bar takes the cover once the list scrolled past it
    var nameBottom by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val collapsed by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > nameBottom - barHeightPx
        }
    }

    Scaffold(
        containerColor = background,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        modifier = modifier,
    ) { insets ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = contentPadding + PaddingValues(
                    start = insets.calculateStartPadding(direction),
                    end = insets.calculateEndPadding(direction),
                    bottom = insets.calculateBottomPadding(),
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    FramedHeader(
                        name = name,
                        models = models,
                        songCovers = songCovers,
                        owner = owner,
                        summary = summary,
                        origin = origin,
                        topPadding = barHeight,
                        onPlay = onPlay,
                        onShuffle = onShuffle,
                        onNameBottom = { nameBottom = it },
                    )
                }
                content()
            }

            FramedTopBar(
                name = name,
                summary = summary,
                models = models.ifEmpty { songCovers.take(1) },
                collapsed = collapsed,
                onBack = onBack,
                onMore = if (menu != null) ({ menuOpen = true }) else null,
            )
        }
    }

    if (menuOpen && menu != null) menu { menuOpen = false }
}

@Composable
private fun FramedHeader(
    name: String,
    models: List<Any>,
    songCovers: List<Any>,
    owner: String?,
    summary: String?,
    origin: Origin?,
    topPadding: Dp,
    onPlay: (() -> Unit)?,
    onShuffle: () -> Unit,
    onNameBottom: (Int) -> Unit,
) = Box(Modifier.fillMaxWidth()) {
    val background = MaterialTheme.colorScheme.surfaceDim

    BlurredCover(
        models = models.ifEmpty { songCovers.take(1) },
        blur = FIELD_BLUR,
        modifier = Modifier
            .matchParentSize()
            // Short of the bottom: a row of the blurred picture showed under the gradient there,
            // the page's own color takes it instead
            .padding(bottom = 2.dp)
            .clipToBounds()
            // The field fades into the page before the text, which reads on the page's color
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = 0.15f),
                        FIELD_FADE_START to background.copy(alpha = 0.35f),
                        0.8f to background,
                    )
                )
            },
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding + 8.dp, bottom = 12.dp, start = 24.dp, end = 24.dp),
    ) {
        val shape = MaterialTheme.shapes.large
        val cover = Modifier
            .fillMaxWidth(FRAMED_COVER_WIDTH)
            .aspectRatio(1f)
            .shadow(24.dp, shape)
        when {
            models.isNotEmpty() -> Thumbnail(models, shape, cover)
            songCovers.size >= GRID_COVERS -> CoverGrid(songCovers, cover, shape)
            else -> Thumbnail(
                models = songCovers.take(1),
                shape = shape,
                modifier = cover,
                placeholder = {
                    Icon(
                        painterResource(R.drawable.queue_music),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(PLACEHOLDER_ICON_SIZE),
                    )
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        origin?.let { OriginBadge(it, FRAMED_BADGE_SIZE) }
        Text(
            name,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.onGloballyPositioned {
                onNameBottom((it.positionInParent().y + it.size.height).toInt())
            },
        )
        owner?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        summary?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onPlay?.invoke() },
                enabled = onPlay != null,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    painterResource(R.drawable.play_arrow),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.action_play))
            }
            OutlinedButton(
                onClick = onShuffle,
                enabled = onPlay != null,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    painterResource(R.drawable.shuffle),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.action_shuffle))
            }
        }
    }
}

/**
 * The bar of the playlist page: shaded and see-through over the header, then once [collapsed] the
 * cover blurred behind it under a scrim, with the [name] and [summary]. Its buttons stay light.
 *
 * @param models the pictures the header's field is of
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FramedTopBar(
    name: String,
    summary: String?,
    models: List<Any>,
    collapsed: Boolean,
    onBack: () -> Unit,
    onMore: (() -> Unit)?,
) = CompositionLocalProvider(LocalTooltipSide provides TooltipSide.BELOW) {
    val shown by animateFloatAsState(if (collapsed) 1f else 0f, label = "playlist bar")

    Box {
        // The same blurred cover the header stands on, kept behind the bar once it scrolled away
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = shown }
        ) {
            BlurredCover(models, FIELD_BLUR, Modifier.matchParentSize())
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = BAR_SCRIM_ALPHA))
            )
        }
        TopAppBar(
            title = {
                Column(Modifier.alpha(shown)) {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    summary?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = { BackButton(onBack) },
            actions = { onMore?.let { MoreSheetButton(it) } },
            // Light on the shade over the header, then on the scrim
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = Color.White,
                titleContentColor = Color.White,
                actionIconContentColor = Color.White,
            ),
            modifier = Modifier.background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = FRAMED_BAR_SHADE_ALPHA * (1f - shown)),
                    1f to Color.Transparent,
                )
            ),
        )
    }
}

/**
 * Covers of the songs of a playlist for [PlaylistDetailScaffold]'s grid, each cover once, in the
 * playlist's order.
 */
internal fun List<MediaItem>.songCovers(): List<Any> =
    mapNotNull { thumbnailModel(it.mediaMetadata.artworkUri, it.localSongId) }
        .distinct()
        .take(GRID_COVERS)

/**
 * The sheet of a playlist's ⋮ button: Play next, Add to queue and Share as buttons, then for a
 * playlist of the app the switches that move or take out its songs as rows, at most one on.
 *
 * @param onAction runs a [QueueAction] with the playlist's songs, `null` while there are none
 * @param share gives a link to the playlist, `null` turns Share off
 * @param mode what the songs' covers do, `null` for a playlist that can't be edited: no rows
 * @param canEdit whether the switches can be used
 */
@Composable
fun PlaylistMenu(
    name: String?,
    onDismiss: () -> Unit,
    onAction: ((QueueAction) -> Unit)?,
    share: (suspend () -> Result<String>)?,
    mode: CoverEditMode? = null,
    canEdit: Boolean = true,
    onModeChange: (CoverEditMode) -> Unit = {},
) {
    val linkShare = rememberLinkShare(name, ShareKind.PLAYLIST) {
        share?.invoke() ?: Result.failure(IllegalStateException("Nothing to share"))
    }

    ConfigSheet(
        onDismiss = onDismiss,
        actions = { close ->
            LabeledIconButton(
                icon = R.drawable.queue_play_next,
                label = stringResource(R.string.menu_play_next),
                enabled = onAction != null,
            ) { close { onAction?.invoke(QueueAction.PLAY_NEXT) } }
            LabeledIconButton(
                icon = R.drawable.playlist_add,
                label = stringResource(R.string.menu_add_to_queue),
                enabled = onAction != null,
            ) { close { onAction?.invoke(QueueAction.ENQUEUE) } }
            LabeledIconButton(
                icon = R.drawable.share,
                label = stringResource(R.string.menu_share),
                enabled = share != null && linkShare.enabled,
                loading = linkShare.sharing,
                // A link of the server can take a moment, the sheet stays until it is there
            ) { linkShare.share { close {} } }
        },
        entries = if (mode == null) null else { close ->
            val reordering = mode == CoverEditMode.REORDER
            val removing = mode == CoverEditMode.REMOVE
            ConfigEntry(
                icon = if (reordering) R.drawable.lock_open else R.drawable.lock,
                label = if (reordering)
                    stringResource(R.string.semantic_lock_the_order)
                else
                    stringResource(R.string.semantic_unlock_to_move_songs),
                enabled = canEdit,
            ) {
                close { onModeChange(if (reordering) CoverEditMode.NONE else CoverEditMode.REORDER) }
            }
            ConfigEntry(
                icon = R.drawable.delete,
                label = if (removing)
                    stringResource(R.string.semantic_stop_removing_songs)
                else
                    stringResource(R.string.semantic_remove_songs),
                enabled = canEdit,
            ) {
                close { onModeChange(if (removing) CoverEditMode.NONE else CoverEditMode.REMOVE) }
            }
        },
    )
}

/** What a playlist with no songs shows in place of them. */
@Composable
internal fun EmptyPlaylist() = Text(
    text = stringResource(R.string.status_playlist_empty),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
)

@ScreenPreviews
@Composable
private fun PlaylistDetailPreview() = PreviewTheme {
    val models = remember { listOf<Any>(previewCover()) }
    val songs = previewSongs()
    val resources = LocalResources.current

    PlaylistDetailScaffold(
        name = "Road trip",
        models = models,
        songCovers = emptyList(),
        owner = "Neon Coast",
        summary = resources.albumSummary(
            songs.size,
            songs.sumOf { it.mediaMetadata.durationMs ?: 0 }),
        origin = Origin.YOUTUBE,
        onBack = {},
        onPlay = {},
        onShuffle = {},
        menu = {},
    ) {
        items(songs, key = { it.mediaId }) { SongItem(it, onClick = {}) }
    }
}
