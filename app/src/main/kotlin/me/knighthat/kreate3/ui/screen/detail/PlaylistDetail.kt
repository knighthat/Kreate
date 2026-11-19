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

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.layout.onSizeChanged
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
import me.knighthat.kreate3.constant.PlaylistButton
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.presence.BrowsedKind
import me.knighthat.kreate3.presence.ShowBrowsingOnDiscord
import me.knighthat.kreate3.provider.Providers
import me.knighthat.kreate3.source.youtube.HEADER_ARTWORK
import me.knighthat.kreate3.ui.component.BackButton
import me.knighthat.kreate3.ui.component.Blur
import me.knighthat.kreate3.ui.component.BlurredCover
import me.knighthat.kreate3.ui.component.ConfigEntry
import me.knighthat.kreate3.ui.component.ConfigSheet
import me.knighthat.kreate3.ui.component.CoverEditMode
import me.knighthat.kreate3.ui.component.CoverGrid
import me.knighthat.kreate3.ui.component.GRID_COVERS
import me.knighthat.kreate3.ui.component.LabeledIconButton
import me.knighthat.kreate3.ui.component.LabeledIconToggle
import me.knighthat.kreate3.ui.component.LocalTooltipSide
import me.knighthat.kreate3.ui.component.MoreSheetButton
import me.knighthat.kreate3.ui.component.OriginBadge
import me.knighthat.kreate3.ui.component.PlaylistOrderButton
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.Thumbnail
import me.knighthat.kreate3.ui.component.TooltipSide
import me.knighthat.kreate3.ui.component.labelRes
import me.knighthat.kreate3.ui.component.playlistPictureKey
import me.knighthat.kreate3.ui.component.previewCover
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberContentsAfterPicture
import me.knighthat.kreate3.ui.component.rememberContentsAfterPictureBelow
import me.knighthat.kreate3.ui.component.rememberPlaylistExport
import me.knighthat.kreate3.ui.component.rememberSharedPicture
import me.knighthat.kreate3.ui.component.songThumbnailModels

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

/** How much of the page's color tones the blurred picture filling the header pane. */
private const val PANE_FIELD_ALPHA = 0.55f

/**
 * The frame of a playlist's page: its cover as an object, a large rounded square with a shadow,
 * standing on a heavily blurred copy of itself that fades into the page. Under it the name, the
 * [owner] and [summary], then the two buttons of [LocalPlaylistButtons]. The header scrolls away with the list; the bar is
 * see-through over it and, once the name has gone under it, shows the blurred cover with the name
 * and [summary].
 *
 * @param summary the lines under the name: `12 songs` and `47 minutes` as two, or YouTube's own
 * text as one; empty while there is nothing to say
 *
 * @param models the playlist's own picture, empty for none
 * @param songCovers covers of its songs, in its order, for a playlist without a picture: four of
 * them in a 2 × 2 grid, the first alone when there are fewer, a playlist icon with none
 * @param owner who made the playlist, like a YouTube channel, `null` when unknown
 * @param onAction runs a [QueueAction] with the playlist's songs, `null` while it has none: the
 * buttons that need songs are off
 * @param share gives a link to the playlist, `null` turns the Share button off
 * @param canShare `false` hides Share, for a playlist that has nothing to share
 * @param menu the sheet the ⋮ button opens, given what closes it; `null` hides the button
 * @param reversed whether the songs run from the playlist's end, for the sort order button
 * @param onToggleOrder turns the songs around, `null` turns the button off
 * @param link where anyone can open the playlist, for Discord's button; `null` for none
 * @param isPrivate whether only its owner can see the playlist, for Discord
 * @param pictureKey the cover's [playlistPictureKey], it moves from the row the page was opened
 * from; `null` for a cover that doesn't move
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScaffold(
    name: String,
    models: List<Any>,
    songCovers: List<Any>,
    owner: String?,
    summary: List<String>,
    origin: Origin?,
    onBack: () -> Unit,
    onAction: ((QueueAction) -> Unit)?,
    share: (suspend () -> Result<String>)?,
    modifier: Modifier = Modifier,
    canShare: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
    menu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    reversed: Boolean = false,
    onToggleOrder: (() -> Unit)? = null,
    link: String? = null,
    isPrivate: Boolean = false,
    pictureKey: String? = null,
    content: LazyListScope.() -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val coverShape = MaterialTheme.shapes.large
    val picture = pictureKey?.let { rememberSharedPicture(it, coverShape) }
    ShowBrowsingOnDiscord(
        kind = BrowsedKind.PLAYLIST,
        origin = origin,
        name = name,
        creator = owner,
        models = remember(models, songCovers) { models.ifEmpty { songCovers.take(1) } },
        link = link,
        isPrivate = isPrivate,
    )
    val linkShare = rememberLinkShare(name, ShareKind.PLAYLIST) {
        share?.invoke() ?: Result.failure(IllegalStateException("Nothing to share"))
    }
    val (first, second) = LocalPlaylistButtons.current
    val buttons =
        listOf(first, second).filter { canShare || it != PlaylistButton.SHARE }.map { button ->
            PlaylistHeaderButton(
                button = button,
                enabled = if (button == PlaylistButton.SHARE)
                    share != null && linkShare.enabled
                else
                    onAction != null,
                loading = button == PlaylistButton.SHARE && linkShare.sharing,
            ) {
                when (button) {
                    PlaylistButton.PLAY -> onAction?.invoke(QueueAction.PLAY)
                    PlaylistButton.SHUFFLE -> onAction?.invoke(QueueAction.SHUFFLE)
                    PlaylistButton.PLAY_NEXT -> onAction?.invoke(QueueAction.PLAY_NEXT)
                    PlaylistButton.ENQUEUE -> onAction?.invoke(QueueAction.ENQUEUE)
                    PlaylistButton.SHARE -> linkShare.share()
                }
            }
        }
    val playlist = PlaylistContent(
        name = name,
        models = models,
        songCovers = songCovers,
        owner = owner,
        summary = summary,
        origin = origin,
        buttons = buttons,
        cover = picture?.modifier ?: Modifier,
        // Everything but the cover, shown once it landed, see pictureFirst
        contents = rememberContentsAfterPicture(),
    )
    val actions: @Composable RowScope.() -> Unit = {
        PlaylistOrderButton(
            reversed = reversed,
            onClick = { onToggleOrder?.invoke() },
            enabled = onToggleOrder != null,
        )
        if (menu != null) MoreSheetButton({ menuOpen = true })
    }
    // Beside the list the queue's actions are buttons of the bar, the ⋮ sheet leaves them out
    val wideActions: @Composable RowScope.() -> Unit = {
        ListPaneQueueButtons(onAction)
        PlaylistOrderButton(
            reversed = reversed,
            onClick = { onToggleOrder?.invoke() },
            enabled = onToggleOrder != null,
        )
        if (menu != null) MoreSheetButton({ menuOpen = true })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceDim,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        modifier = modifier,
    ) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sideBySide = isSideBySide(maxWidth, maxHeight)
            if (sideBySide) {
                HorizontalPlaylistLayout(
                    playlist = playlist,
                    width = maxWidth,
                    height = maxHeight,
                    insets = insets,
                    contentPadding = contentPadding,
                    listState = listState,
                    onBack = onBack,
                    actions = wideActions,
                    content = content,
                )
            } else {
                VerticalPlaylistLayout(
                    playlist = playlist,
                    insets = insets,
                    contentPadding = contentPadding,
                    listState = listState,
                    onBack = onBack,
                    actions = actions,
                    content = content,
                )
            }

            if (menuOpen && menu != null)
                CompositionLocalProvider(LocalQueueButtonsInBar provides sideBySide) {
                    menu { menuOpen = false }
                }
        }
    }
}

/**
 * What a playlist's page shows above its songs, the same in both layouts, see
 * [PlaylistDetailScaffold].
 *
 * @property cover moves the cover in from the row it was opened from, see [rememberSharedPicture]
 * @property contents fades everything but the cover in after it, see
 * [rememberContentsAfterPicture]
 */
@Immutable
private class PlaylistContent(
    val name: String,
    val models: List<Any>,
    val songCovers: List<Any>,
    val owner: String?,
    val summary: List<String>,
    val origin: Origin?,
    val buttons: List<PlaylistHeaderButton>,
    val cover: Modifier,
    val contents: Modifier,
) {
    /** The pictures the blurred field behind the header is made of. */
    val field: List<Any>
        get() = models.ifEmpty { songCovers.take(1) }
}

/**
 * The playlist page on an upright window: the header is the list's first item, the bar takes the
 * cover blurred and the name once it scrolled under, see [PlaylistDetailScaffold].
 *
 * @param insets what the system bars and the cutout take, from the scaffold
 * @param actions the buttons at the end of the bar
 */
@Composable
private fun VerticalPlaylistLayout(
    playlist: PlaylistContent,
    insets: PaddingValues,
    contentPadding: PaddingValues,
    listState: LazyListState,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: LazyListScope.() -> Unit,
) = Box(Modifier.fillMaxSize()) {
    val barHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            TopAppBarDefaults.TopAppBarExpandedHeight
    val barHeightPx = with(LocalDensity.current) { barHeight.roundToPx() }
    val direction = LocalLayoutDirection.current
    // Where the name ends in the header, the bar takes the cover once the list scrolled past it
    var nameBottom by remember { mutableIntStateOf(Int.MAX_VALUE) }
    // Where the header ends in the list, the songs under it fade in after the cover
    var headerHeight by remember { mutableIntStateOf(0) }
    val headerBottom = remember(listState) {
        {
            if (listState.firstVisibleItemIndex == 0)
                (headerHeight - listState.firstVisibleItemScrollOffset).toFloat()
            else
                0f
        }
    }
    val collapsed by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > nameBottom - barHeightPx
        }
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
            .then(rememberContentsAfterPictureBelow(headerBottom)),
    ) {
        item(key = "header") {
            FramedHeader(
                name = playlist.name,
                models = playlist.models,
                songCovers = playlist.songCovers,
                owner = playlist.owner,
                summary = playlist.summary,
                origin = playlist.origin,
                topPadding = barHeight,
                buttons = playlist.buttons,
                cover = playlist.cover,
                contents = playlist.contents,
                onNameBottom = { nameBottom = it },
                modifier = Modifier.onSizeChanged { headerHeight = it.height },
            )
        }
        content()
    }

    FramedTopBar(
        name = playlist.name,
        summary = playlist.summary,
        models = playlist.field,
        collapsed = collapsed,
        onBack = onBack,
        actions = actions,
        modifier = playlist.contents,
    )
}

/**
 * The playlist page on a wide window ([isSideBySide]), laid out like an album's: the cover and the
 * owner in a pane at the start on the blurred picture, its bar naming the playlist with its
 * source, song count and length, the list beside it under its own bar, see
 * [PlaylistDetailScaffold]. The header's two buttons are left out, the list's bar has the queue's.
 *
 * @param width the page's width, [height] its height
 * @param insets what the system bars and the cutout take, from the scaffold
 * @param actions the buttons at the end of the list's bar
 */
@Composable
private fun HorizontalPlaylistLayout(
    playlist: PlaylistContent,
    width: Dp,
    height: Dp,
    insets: PaddingValues,
    contentPadding: PaddingValues,
    listState: LazyListState,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val barHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            TopAppBarDefaults.TopAppBarExpandedHeight
    val direction = LocalLayoutDirection.current
    val paneWidth = headerPaneWidth(width)
    val start = insets.calculateStartPadding(direction)
    val background = MaterialTheme.colorScheme.surfaceDim

    SidePanes(
        paneWidth = paneWidth,
        header = {
            // The blurred picture fills the pane, toned toward the page for the texts
            BlurredCover(
                models = playlist.field,
                blur = FIELD_BLUR,
                modifier = Modifier
                    .matchParentSize()
                    .then(playlist.contents)
                    .clipToBounds()
                    .drawWithContent {
                        drawContent()
                        drawRect(background.copy(alpha = PANE_FIELD_ALPHA))
                    },
            )
            HeaderPaneColumn(
                paneHeight = height,
                padding = PaddingValues(
                    start = start + HEADER_PANE_PADDING,
                    top = barHeight,
                    end = HEADER_PANE_PADDING,
                    bottom = insets.calculateBottomPadding() +
                            contentPadding.calculateBottomPadding() + 16.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlaylistCover(
                    models = playlist.models,
                    songCovers = playlist.songCovers,
                    coverSize = Modifier
                        .size(headerPaneCover(paneWidth - start, height))
                        .then(playlist.cover),
                )
                playlist.owner?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = playlist.contents.padding(top = 12.dp),
                    )
                }
            }
            HeaderPaneBar(
                onBack = onBack,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = playlist.contents,
                title = {
                    HeaderPaneTitle(
                        name = playlist.name,
                        origin = playlist.origin,
                        details = playlist.summary.joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        },
        list = {
            ListPaneBar(playlist.contents, actions)
            LazyColumn(
                state = listState,
                contentPadding = contentPadding + PaddingValues(
                    end = insets.calculateEndPadding(direction),
                    bottom = insets.calculateBottomPadding(),
                ),
                modifier = Modifier
                    .weight(1f)
                    .then(playlist.contents),
                content = content,
            )
        },
    )
}

@Composable
private fun FramedHeader(
    name: String,
    models: List<Any>,
    songCovers: List<Any>,
    owner: String?,
    summary: List<String>,
    origin: Origin?,
    topPadding: Dp,
    buttons: List<PlaylistHeaderButton>,
    cover: Modifier,
    contents: Modifier,
    onNameBottom: (Int) -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier.fillMaxWidth()) {
    val background = MaterialTheme.colorScheme.surfaceDim

    BlurredCover(
        models = models.ifEmpty { songCovers.take(1) },
        blur = FIELD_BLUR,
        modifier = Modifier
            .matchParentSize()
            .then(contents)
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
        FramedHeaderContent(
            name = name,
            models = models,
            songCovers = songCovers,
            owner = owner,
            summary = summary,
            origin = origin,
            buttons = buttons,
            coverSize = Modifier
                .fillMaxWidth(FRAMED_COVER_WIDTH)
                .aspectRatio(1f)
                .then(cover),
            contents = contents,
            onNameBottom = onNameBottom,
        )
    }
}

/**
 * The cover, name, owner, [summary] and buttons of a playlist's header, over the page or in the
 * pane beside its list.
 *
 * @param coverSize the cover's size
 * @param contents for everything but the cover, see [PlaylistContent.contents]
 * @param onNameBottom gets where the name ends in the column, `null` when nothing needs it
 */
@Composable
private fun FramedHeaderContent(
    name: String,
    models: List<Any>,
    songCovers: List<Any>,
    owner: String?,
    summary: List<String>,
    origin: Origin?,
    buttons: List<PlaylistHeaderButton>,
    coverSize: Modifier,
    contents: Modifier,
    onNameBottom: ((Int) -> Unit)? = null,
) {
    PlaylistCover(models, songCovers, coverSize)
    Spacer(Modifier.height(16.dp))
    origin?.let { Box(contents) { OriginBadge(it, FRAMED_BADGE_SIZE) } }
    Text(
        name,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = if (onNameBottom != null) {
            contents.onGloballyPositioned {
                onNameBottom((it.positionInParent().y + it.size.height).toInt())
            }
        } else {
            contents
        },
    )
    owner?.let {
        Text(
            it,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = contents,
        )
    }
    summary.forEach {
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = contents,
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = contents) {
        buttons.forEachIndexed { index, button -> HeaderButton(button, filled = index == 0) }
    }
}

/**
 * The playlist's picture, else a 2 × 2 grid of its [songCovers], the first alone with fewer, a
 * playlist icon with none, in a shadow.
 *
 * @param coverSize the cover's size
 */
@Composable
private fun PlaylistCover(models: List<Any>, songCovers: List<Any>, coverSize: Modifier) {
    val shape = MaterialTheme.shapes.large
    val cover = coverSize.shadow(24.dp, shape)

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
}

/**
 * One of the two buttons under a playlist's header.
 *
 * @param loading shows a spinner in place of the icon while a link is on its way
 */
private class PlaylistHeaderButton(
    val button: PlaylistButton,
    val enabled: Boolean,
    val loading: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun HeaderButton(button: PlaylistHeaderButton, filled: Boolean) {
    val label = stringResource(button.button.labelRes)
    val content: @Composable RowScope.() -> Unit = {
        if (button.loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        } else {
            Icon(
                painterResource(button.button.icon),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        }
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label)
    }
    if (filled) {
        Button(
            onClick = button.onClick,
            enabled = button.enabled,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            content = content,
        )
    } else {
        OutlinedButton(
            onClick = button.onClick,
            enabled = button.enabled,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            content = content,
        )
    }
}

/** The icon of a playlist page button. */
@get:DrawableRes
private val PlaylistButton.icon: Int
    get() = when (this) {
        PlaylistButton.PLAY -> R.drawable.play_arrow
        PlaylistButton.SHUFFLE -> R.drawable.shuffle
        PlaylistButton.PLAY_NEXT -> R.drawable.queue_play_next
        PlaylistButton.ENQUEUE -> R.drawable.playlist_add
        PlaylistButton.SHARE -> R.drawable.share
    }

/**
 * The two buttons under the header of a playlist's page, see `Preferences.PLAYLIST_FIRST_BUTTON`.
 * Play and Shuffle unless provided, like in previews.
 */
val LocalPlaylistButtons =
    compositionLocalOf { PlaylistButton.PLAY to PlaylistButton.SHUFFLE }

/**
 * The bar of the playlist page: shaded and see-through over the header, then once [collapsed] the
 * cover blurred behind it under a scrim, with the [name] and [summary]. Its buttons stay light.
 *
 * @param models the pictures the header's field is of
 * @param actions the buttons at the end of the bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FramedTopBar(
    name: String,
    summary: List<String>,
    models: List<Any>,
    collapsed: Boolean,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) = CompositionLocalProvider(LocalTooltipSide provides TooltipSide.BELOW) {
    val shown by animateFloatAsState(if (collapsed) 1f else 0f, label = "playlist bar")

    Box(modifier) {
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
                    if (summary.isNotEmpty()) {
                        // One line under the name, however many lines the header gives it
                        Text(
                            summary.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = { BackButton(onBack) },
            actions = actions,
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
 * playlist's order, stopping at the fourth so a long list isn't walked on the main thread. A
 * song's cover is the first of [songThumbnailModels] at [HEADER_ARTWORK], the sharp one the
 * player shows, not the small one of its row.
 */
internal fun List<MediaItem>.songCovers(providers: Providers): List<Any> =
    asSequence()
        .mapNotNull { songThumbnailModels(it, providers, HEADER_ARTWORK).firstOrNull() }
        .distinct()
        .take(GRID_COVERS)
        .toList()

/**
 * The sheet of a playlist's ⋮ button: Play next, Add to queue and Share as buttons, then for a
 * playlist of the app the switches that move or take out its songs as rows, at most one on, a
 * row that exports the playlist to a file and one that deletes it.
 *
 * Opened from a wide page, which has the queue's actions as buttons of its bar
 * ([LocalQueueButtonsInBar]), it leaves them out.
 *
 * @param onAction runs a [QueueAction] with the playlist's songs, `null` while there are none
 * @param share gives a link to the playlist, `null` turns Share off
 * @param canShare `false` hides Share
 * @param mode what the songs' covers do, `null` for a playlist that can't be edited: no rows
 * @param canEdit whether the switches can be used
 * @param canRemove whether songs can be taken out, `false` for a monthly playlist: no such row
 * @param onDelete asks to delete the playlist, `null` for a playlist that can't be: no row
 * @param exportId the database id of the app's playlist to export to a CSV file, `null` for no
 * row (playlists of a source aren't exported); the sheet stays with a spinner on the row while
 * its songs are fetched and closes once the file is written or given up
 * @param isSaved whether the playlist is in the account's library, `null` hides the button
 * @param canDelete whether the user may delete it, the row is grayed out otherwise
 * @param busy for a playlist of a server, whose edits are sent when the user turns the mode off:
 * whether the page waits for the server. The row that was tapped is a spinner then and the other
 * rows are off. The sheet stays until the mode changed, tapping a row can take a while there;
 * `null` for a playlist that applies the modes at once
 */
@Composable
fun PlaylistMenu(
    name: String?,
    onDismiss: () -> Unit,
    onAction: ((QueueAction) -> Unit)?,
    share: (suspend () -> Result<String>)?,
    canShare: Boolean = true,
    isSaved: Boolean? = null,
    onToggleSaved: () -> Unit = {},
    mode: CoverEditMode? = null,
    canEdit: Boolean = true,
    canRemove: Boolean = true,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    canDelete: Boolean = true,
    onModeChange: (CoverEditMode) -> Unit = {},
    busy: Boolean? = null,
    exportId: Long? = null,
) {
    val linkShare = rememberLinkShare(name, ShareKind.PLAYLIST) {
        share?.invoke() ?: Result.failure(IllegalStateException("Nothing to share"))
    }
    // The page's bar has them already
    val inBar = LocalQueueButtonsInBar.current

    ConfigSheet(
        onDismiss = onDismiss,
        actions = { close ->
            if (!inBar) {
                LabeledIconButton(
                    icon = R.drawable.queue_play_next,
                    label = stringResource(R.string.menu_shortcut_play_next),
                    enabled = onAction != null,
                ) { close { onAction?.invoke(QueueAction.PLAY_NEXT) } }
                LabeledIconButton(
                    icon = R.drawable.playlist_add,
                    label = stringResource(R.string.menu_shortcut_add_to_queue),
                    enabled = onAction != null,
                ) { close { onAction?.invoke(QueueAction.ENQUEUE) } }
            }
            if (isSaved != null)
                LabeledIconToggle(
                    isSet = isSaved,
                    enabled = true,
                    setIcon = R.drawable.bookmark_filled,
                    unsetIcon = R.drawable.bookmark_outline,
                    label = stringResource(R.string.menu_shortcut_add_to_library),
                    onClick = onToggleSaved,
                )
            if (canShare)
                LabeledIconButton(
                    icon = R.drawable.share,
                    label = stringResource(R.string.menu_shortcut_share),
                    enabled = share != null && linkShare.enabled,
                    loading = linkShare.sharing,
                    // A link of the server can take a moment, the sheet stays until it is there
                ) { linkShare.share { close {} } }
        },
        entries = if (mode == null && exportId == null) null else { close ->
            if (mode != null)
                PlaylistEditEntries(
                    close, mode, canEdit, canRemove, onEdit, onModeChange, busy,
                )
            if (exportId != null) {
                val exporting = rememberPlaylistExport(
                    playlistId = exportId,
                    name = name,
                    onDone = { close {} },
                )
                ConfigEntry(
                    icon = R.drawable.upload,
                    label = stringResource(R.string.semantic_export_playlist),
                    enabled = busy != true,
                    loading = exporting.loading,
                ) { exporting.start() }
            }
            if (mode != null && onDelete != null)
                ConfigEntry(
                    icon = R.drawable.delete,
                    label = stringResource(R.string.semantic_delete_playlist),
                    enabled = canDelete && busy != true,
                ) { close { onDelete() } }
        },
    )
}

/** The rows of [PlaylistMenu] that edit the playlist, see its parameters. */
@Composable
private fun PlaylistEditEntries(
    close: (then: () -> Unit) -> Unit,
    mode: CoverEditMode,
    canEdit: Boolean,
    canRemove: Boolean,
    onEdit: (() -> Unit)?,
    onModeChange: (CoverEditMode) -> Unit,
    busy: Boolean?,
) {
    val unlocked = mode == CoverEditMode.REORDER
    val removing = mode == CoverEditMode.REMOVE
    val waiting = busy == true
    if (onEdit != null)
        ConfigEntry(
            icon = R.drawable.edit,
            label = stringResource(R.string.semantic_edit_playlist),
            enabled = !waiting,
        ) { close { onEdit() } }
    // The row tapped last, it shows the spinner while the page waits
    var tapped by remember { mutableStateOf<CoverEditMode?>(null) }
    if (busy != null) {
        // A mode on or off is the end of what the tap asked for
        var shown by remember { mutableStateOf(mode) }
        LaunchedEffect(mode) {
            if (shown != mode) close {}
            shown = mode
        }
    }
    ConfigEntry(
        icon = if (unlocked) R.drawable.lock_open else R.drawable.lock,
        label = if (unlocked)
            stringResource(R.string.semantic_lock_the_order)
        else
            stringResource(R.string.semantic_unlock_to_move_songs),
        enabled = canEdit && !waiting,
        loading = waiting && tapped == CoverEditMode.REORDER,
    ) {
        val target = if (unlocked) CoverEditMode.NONE else CoverEditMode.REORDER
        tapped = CoverEditMode.REORDER
        // The edits of a server's playlist are sent or fetched first, the sheet waits
        if (busy != null) onModeChange(target) else close { onModeChange(target) }
    }
    if (canRemove)
        ConfigEntry(
            icon = R.drawable.playlist_remove,
            label = if (removing)
                stringResource(R.string.semantic_stop_removing_songs)
            else
                stringResource(R.string.semantic_remove_songs),
            enabled = canEdit && !waiting,
            loading = waiting && tapped == CoverEditMode.REMOVE,
        ) {
            val target = if (removing) CoverEditMode.NONE else CoverEditMode.REMOVE
            tapped = CoverEditMode.REMOVE
            if (busy != null) onModeChange(target) else close { onModeChange(target) }
        }
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
        summary = listOfNotNull(
            resources.songCount(songs.size),
            resources.totalLength(songs.sumOf { it.mediaMetadata.durationMs ?: 0 }),
        ),
        origin = Origin.YOUTUBE,
        onBack = {},
        onAction = {},
        share = null,
        menu = {},
    ) {
        items(songs, key = { it.mediaId }) { SongItem(it, onClick = {}) }
    }
}
