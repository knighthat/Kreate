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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.presence.BrowsedKind
import me.knighthat.kreate3.presence.ShowBrowsingOnDiscord
import me.knighthat.kreate3.provider.youtube.YouTubeLinks
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
import me.knighthat.kreate3.ui.component.QueueActionButton
import me.knighthat.kreate3.ui.component.TextPlaceholder
import me.knighthat.kreate3.ui.component.Thumbnail
import me.knighthat.kreate3.ui.component.TooltipSide
import me.knighthat.kreate3.ui.component.artistPictureKey
import me.knighthat.kreate3.ui.component.rememberContentsAfterPicture
import me.knighthat.kreate3.ui.component.rememberSharedPicture
import me.knighthat.kreate3.ui.component.thumbnailModel

/** Share of the screen's height the artist's picture takes at the top, before it scrolls. */
private const val PICTURE_HEIGHT = 0.4f

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

/** How much the description in the pane covers, a little less than the name above it. */
private const val PANE_DESCRIPTION_ALPHA = 0.85f

/** How dark the bottom of the picture beside the list gets, under the name. */
private const val PANE_NAME_SHADE_ALPHA = 0.8f

/** Rows standing in for an artist's top songs while they load. */
internal const val SONG_PLACEHOLDERS = 5

/**
 * The frame of an artist's page: their picture across the top, [PICTURE_HEIGHT] of the screen,
 * under a see-through top bar shaded at its top, with the [name], their badges under it, the
 * [action] button and a ⋮ button opening [menu]. The list ([content]), the [description] first,
 * starts under the picture and scrolls over it, see through, fading out just under the bar; the
 * picture blurs more the farther it went up, fully blurred once the list's top reaches the bar.
 * On a wide window the picture fills a pane beside the list instead, see
 * [HorizontalArtistLayout].
 *
 * @param artistId whose page it is, the picture of their card grows into the top as it opens
 * @param standIn the picture the artist was opened with, see [OpenedItem]: in memory already,
 * it shows while [thumbnail] loads, which then fades in over it, and stands in for it when it fails
 * @param origin where the artist comes from, a badge under the name, none when `null`
 * @param subtitle shown under the name in place of the badges, like YouTube's monthly audience
 * @param description what the source says about the artist, `null` for none or while it loads:
 * the list's first item when upright, at the bottom of the picture beside the list, which blurs
 * while all of it shows
 * @param descriptionLoading the description is on its way: a placeholder takes its place in the
 * upright list
 * @param action what the button beside ⋮ does with the songs, `null` hides the button
 * @param onAction runs [action], `null` turns the button off while there are no songs
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
    description: String? = null,
    descriptionLoading: Boolean = false,
    standIn: Uri? = null,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
    action: QueueAction? = null,
    onAction: ((QueueAction) -> Unit)? = null,
    menu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    // The picture blurs behind the whole description, so it reads over any picture
    var descriptionOpen by rememberSaveable { mutableStateOf(false) }
    val openBlur = animateFloatAsState(
        if (descriptionOpen && description != null) 1f else 0f,
        label = "description blur",
    )
    ShowBrowsingOnDiscord(
        kind = BrowsedKind.ARTIST,
        origin = origin,
        name = name,
        creator = null,
        models = remember(thumbnail, standIn) {
            listOfNotNull(thumbnailModel(thumbnail, null), thumbnailModel(standIn, null))
        },
        link = YouTubeLinks.artist(artistId).takeIf { origin == Origin.YOUTUBE },
    )
    val picture = rememberSharedPicture(
        artistPictureKey(artistId),
        corners = 0,
        // Round on their card
        otherCorners = 50,
    )
    // Everything but the picture, shown once it landed, see pictureFirst
    val contents = rememberContentsAfterPicture()
    val models = remember(thumbnail, standIn) {
        listOfNotNull(thumbnailModel(thumbnail, null), thumbnailModel(standIn, null)).distinct()
    }
    val artist = ArtistContent(
        name = name,
        models = models,
        picture = picture.modifier,
        overlay = picture.overlay,
        contents = contents,
        origin = origin,
        subtitle = subtitle,
        description = description,
        descriptionLoading = descriptionLoading,
        descriptionBlur = openBlur,
        onDescriptionExpanded = { descriptionOpen = it },
    )
    val actions: @Composable RowScope.() -> Unit = {
        action?.let {
            QueueActionButton(it, { onAction?.invoke(it) }, enabled = onAction != null)
        }
        if (menu != null) MoreSheetButton({ menuOpen = true })
    }
    // Beside the list the queue's actions are buttons of the bar, the ⋮ sheet leaves them out
    val wideActions: @Composable RowScope.() -> Unit = {
        ListPaneQueueButtons(onAction)
        if (menu != null) MoreSheetButton({ menuOpen = true })
    }

    // No bar of its own, the insets it leaves are the room under the list
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceDim,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        modifier = modifier,
    ) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sideBySide = isSideBySide(maxWidth, maxHeight)
            if (sideBySide) {
                HorizontalArtistLayout(
                    artist = artist,
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
                VerticalArtistLayout(
                    artist = artist,
                    width = maxWidth,
                    height = maxHeight,
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
 * What an artist's page shows above their songs, the same in both layouts, see
 * [ArtistDetailScaffold].
 *
 * @property picture the shared picture's modifier, [overlay] the one of what is drawn over it
 * @property contents fades everything but the picture in after it, see
 * [rememberContentsAfterPicture]
 * @property descriptionLoading the description is on its way
 * @property descriptionBlur how much the picture beside the list is blurred for the description,
 * `1` while all of it shows
 * @property onDescriptionExpanded gets whether all of the description shows
 */
@Immutable
private class ArtistContent(
    val name: String,
    val models: List<Any>,
    val picture: Modifier,
    val overlay: Modifier,
    val contents: Modifier,
    val origin: Origin?,
    val subtitle: String?,
    val description: String?,
    val descriptionLoading: Boolean,
    val descriptionBlur: State<Float>,
    val onDescriptionExpanded: (Boolean) -> Unit,
)

/**
 * The artist's picture filling what [modifier] gives it, the microphone while there is none.
 *
 * @param minCachedSize a picture in memory smaller than this only stands in while it loads
 */
@Composable
private fun ArtistPicture(artist: ArtistContent, minCachedSize: Dp, modifier: Modifier) =
    Thumbnail(
        models = artist.models,
        shape = RectangleShape,
        modifier = modifier.then(artist.picture),
        minCachedSize = minCachedSize,
        placeholder = {
            Icon(
                painterResource(R.drawable.mic),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(PLACEHOLDER_SIZE),
            )
        },
    )

/**
 * The artist page on an upright window: the picture across the top under a see-through bar, the
 * list, the description first, scrolling up over it, see [ArtistDetailScaffold].
 *
 * @param width the page's width, [height] its height
 * @param insets what the system bars and the cutout take, from the scaffold
 * @param actions the buttons at the end of the bar
 */
@Composable
private fun VerticalArtistLayout(
    artist: ArtistContent,
    width: Dp,
    height: Dp,
    insets: PaddingValues,
    contentPadding: PaddingValues,
    listState: LazyListState,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: LazyListScope.() -> Unit,
) = Box(Modifier.fillMaxSize()) {
    val background = MaterialTheme.colorScheme.surfaceDim
    val barHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            TopAppBarDefaults.TopAppBarExpandedHeight
    val direction = LocalLayoutDirection.current
    val pictureHeight = height * PICTURE_HEIGHT
    // The list starts under the bar, its first item fills the rest of the picture
    val pictureBelowBar = (pictureHeight - barHeight).coerceAtLeast(0.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .height(pictureHeight)
    ) {
        ArtistPicture(artist, max(width, pictureHeight), Modifier.matchParentSize())
        BlurredCover(
            models = artist.models,
            blur = PICTURE_BLUR,
            modifier = Modifier
                .matchParentSize()
                .then(artist.contents)
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
        // It fades into the page the list starts on, see drawEasedFade
        Box(
            Modifier
                .matchParentSize()
                .then(artist.overlay)
                .then(artist.contents)
                .drawBehind { drawEasedFade(background) }
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
            .then(artist.contents)
            .fadeTop(LIST_FADE),
    ) {
        item(key = "picture") {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(pictureBelowBar)
            )
        }
        if (artist.description != null) {
            item(key = "description") {
                DetailDescription(
                    artist.description,
                    onExpandedChange = artist.onDescriptionExpanded,
                )
            }
        } else if (artist.descriptionLoading) {
            item(key = "description") { TextPlaceholder() }
        }
        content()
    }

    ArtistTopBar(
        name = artist.name,
        origin = artist.origin,
        subtitle = artist.subtitle,
        onBack = onBack,
        actions = actions,
        modifier = artist.overlay.then(artist.contents),
    )
}

/**
 * The artist page on a wide window ([isSideBySide]): the picture filling a pane at the start,
 * the name, badge or subtitle and the description at its bottom, the list beside it under its own
 * bar, see [ArtistDetailScaffold].
 *
 * @param width the page's width, [height] its height
 * @param insets what the system bars and the cutout take, from the scaffold
 * @param actions the buttons at the end of the list's bar
 */
@Composable
private fun HorizontalArtistLayout(
    artist: ArtistContent,
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

    SidePanes(
        paneWidth = paneWidth,
        header = {
            ArtistPicture(artist, max(paneWidth, height), Modifier.matchParentSize())
            BlurredCover(
                models = artist.models,
                blur = PICTURE_BLUR,
                modifier = Modifier
                    .matchParentSize()
                    .then(artist.contents)
                    .graphicsLayer { alpha = artist.descriptionBlur.value },
            )
            // Shaded at the top for the back button and at the bottom for the name
            Box(
                Modifier
                    .matchParentSize()
                    .then(artist.overlay)
                    .then(artist.contents)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = BAR_SHADE_ALPHA),
                            0.25f to Color.Transparent,
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = PANE_NAME_SHADE_ALPHA),
                        )
                    )
            )
            // At the bottom, a long description scrolls under the bar
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
                modifier = Modifier
                    .matchParentSize()
                    .padding(top = barHeight)
                    .then(artist.overlay)
                    .then(artist.contents)
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = height - barHeight)
                    .padding(
                        start = start + HEADER_PANE_PADDING,
                        end = HEADER_PANE_PADDING,
                        bottom = insets.calculateBottomPadding() +
                                contentPadding.calculateBottomPadding() +
                                HEADER_PANE_PADDING,
                    ),
            ) {
                Text(
                    artist.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artist.subtitle != null) {
                    Text(
                        artist.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    artist.origin?.let { OriginBadge(it, BADGE_SIZE, tint = Color.White) }
                }
                artist.description?.let { text ->
                    DetailDescription(
                        text,
                        // Its own padding, its text lines up with the name
                        modifier = Modifier.layout { measurable, constraints ->
                            val edge = 16.dp.roundToPx()
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = constraints.minWidth + 2 * edge,
                                    maxWidth = constraints.maxWidth + 2 * edge,
                                )
                            )
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.placeRelative(-edge, 0)
                            }
                        },
                        color = Color.White.copy(alpha = PANE_DESCRIPTION_ALPHA),
                        linkColor = Color.White,
                        onExpandedChange = artist.onDescriptionExpanded,
                    )
                }
            }
            HeaderPaneBar(onBack, Color.White, artist.overlay.then(artist.contents))
        },
        list = {
            ListPaneBar(artist.overlay.then(artist.contents), actions = actions)
            LazyColumn(
                state = listState,
                contentPadding = contentPadding + PaddingValues(
                    end = insets.calculateEndPadding(direction),
                    bottom = insets.calculateBottomPadding(),
                ),
                modifier = Modifier
                    .weight(1f)
                    .then(artist.contents),
                content = content,
            )
        },
    )
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
    actions: @Composable RowScope.() -> Unit,
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
        actions = actions,
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
 * Opened from a wide page, which has the queue's actions as buttons of its bar
 * ([LocalQueueButtonsInBar]), it leaves them out.
 *
 * @param onAction runs a [QueueAction] with the artist's songs, `null` while there are none: the
 * buttons and rows of those are off
 * @param isFollowed whether the artist is followed, `null` turns Follow off, while it isn't known
 * @param share gives a link to the artist, `null` turns Share off, like for an artist of the
 * device
 * @param kind what is shared, for the failure message
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
    onEdit: (() -> Unit)? = null,
    kind: ShareKind = ShareKind.ARTIST,
) {
    val linkShare = rememberLinkShare(name, kind) {
        share?.invoke() ?: Result.failure(IllegalStateException("Nothing to share"))
    }
    // The page's bar has them already
    val inBar = LocalQueueButtonsInBar.current

    ConfigSheet(
        onDismiss = onDismiss,
        actions = { close ->
            if (!inBar)
                LabeledIconButton(
                    icon = R.drawable.play_arrow,
                    label = stringResource(R.string.menu_shortcut_play),
                    enabled = onAction != null,
                ) { close { onAction?.invoke(QueueAction.PLAY) } }
            LabeledIconToggle(
                isSet = isFollowed == true,
                enabled = isFollowed != null,
                setIcon = R.drawable.how_to_reg,
                unsetIcon = R.drawable.person_add,
                label = stringResource(R.string.menu_shortcut_follow),
                onClick = onToggleFollow,
            )
            LabeledIconButton(
                icon = R.drawable.share,
                label = stringResource(R.string.menu_shortcut_share),
                enabled = share != null && linkShare.enabled,
                loading = linkShare.sharing,
                // A link of the server can take a moment, the sheet stays until it is there
            ) { linkShare.share { close {} } }
        },
        entries = { close ->
            if (!inBar) {
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
            }
            ConfigEntry(
                R.drawable.info,
                stringResource(R.string.menu_details),
                enabled = onOpenDetails != null,
            ) { close { onOpenDetails?.invoke() } }
            ConfigEntry(R.drawable.insert_chart, stringResource(R.string.menu_statistics)) {
                close(onOpenStatistics)
            }
            if (onEdit != null)
                ConfigEntry(R.drawable.edit, stringResource(R.string.menu_edit)) { close(onEdit) }
        },
    )
}
