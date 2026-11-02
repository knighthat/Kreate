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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.materialkolor.hct.Hct
import me.knighthat.kreate3.R
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.BackButton
import me.knighthat.kreate3.ui.component.ConfigEntry
import me.knighthat.kreate3.ui.component.ConfigSheet
import me.knighthat.kreate3.ui.component.EXTRA_ARTIST_IDS
import me.knighthat.kreate3.ui.component.EXTRA_ARTIST_NAMES
import me.knighthat.kreate3.ui.component.ExplicitBadge
import me.knighthat.kreate3.ui.component.LabeledIconButton
import me.knighthat.kreate3.ui.component.LabeledIconToggle
import me.knighthat.kreate3.ui.component.LocalTooltipSide
import me.knighthat.kreate3.ui.component.MoreSheetButton
import me.knighthat.kreate3.ui.component.OriginBadge
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.QueueAction
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.Thumbnail
import me.knighthat.kreate3.ui.component.TooltipSide
import me.knighthat.kreate3.ui.component.albumPictureKey
import me.knighthat.kreate3.ui.component.origin
import me.knighthat.kreate3.ui.component.previewCover
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.component.rememberContentsAfterPicture
import me.knighthat.kreate3.ui.component.rememberSharedPicture
import me.knighthat.kreate3.ui.component.thumbnailModel
import me.knighthat.kreate3.ui.navigation.Route
import me.knighthat.kreate3.ui.navigation.artistRoute
import me.knighthat.kreate3.ui.theme.rememberCoverSeed

/** Edge of the cover beside the name. */
private val TINTED_COVER_SIZE = 136.dp

/** How dark the header's color is, HCT tone: dark enough for white text on any cover. */
private const val TINT_TONE = 30.0

/** Text under the name on the tint, a little dimmer than the name. */
private const val TINTED_SECONDARY_ALPHA = 0.75f

private val TINTED_BADGE_SIZE = 16.dp

/**
 * The frame of an album's page: no picture behind anything, the header
 * is filled with a dark shade of the cover's main color instead, fading into the page. The cover
 * sits at the start with the badges, the overline, name, [artists] (a tap opens them), year and
 * [summary] beside it, the [description] under them. Playing is in the ⋮ sheet. The bar takes the
 * same color, so it is one block with the header, and shows the name once the name has gone under
 * it.
 *
 * @param albumId whose page it is, the cover of its card grows into the header as it opens
 * @param standIn the cover the album was opened with, see [OpenedItem]: in memory already, it
 * shows until [models] load
 * @param tint the color the header is a shade of, the cover's own by default; the theme's primary
 * while there is none
 * @param description what the source says about the album, `null` when it says nothing
 * @param onOpenArtist opens the album's artist, `null` when there is none to open
 * @param menu the sheet the ⋮ button opens, given what closes it; `null` hides the button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScaffold(
    name: String,
    albumId: String,
    models: List<Any>,
    artists: String?,
    year: Int?,
    summary: String?,
    origin: Origin?,
    isExplicit: Boolean,
    description: String?,
    onBack: () -> Unit,
    onOpenArtist: (() -> Unit)?,
    modifier: Modifier = Modifier,
    standIn: Uri? = null,
    tint: Color = rememberCoverSeed(models),
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
    menu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val covers = remember(models, standIn) {
        (models + listOfNotNull(thumbnailModel(standIn, null))).distinct()
    }
    val picture = rememberSharedPicture(albumPictureKey(albumId), MaterialTheme.shapes.medium)
    // Everything but the cover, shown once it landed, see pictureFirst
    val contents = rememberContentsAfterPicture()
    val seed = tint.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.primary
    val shade by animateColorAsState(
        remember(seed) { Color(Hct.fromInt(seed.toArgb()).withTone(TINT_TONE).toInt()) },
        label = "album tint",
    )
    val barHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            TopAppBarDefaults.TopAppBarExpandedHeight
    val barHeightPx = with(LocalDensity.current) { barHeight.roundToPx() }
    val direction = LocalLayoutDirection.current
    var nameBottom by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val collapsed by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > nameBottom - barHeightPx
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceDim,
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
                modifier = Modifier
                    .fillMaxSize()
                    .then(contents.modifier),
            ) {
                item(key = "header") {
                    TintedHeader(
                        name = name,
                        models = covers,
                        cover = contents.picture.then(picture.modifier),
                        artists = artists,
                        year = year,
                        summary = summary,
                        origin = origin,
                        isExplicit = isExplicit,
                        description = description,
                        shade = shade,
                        topPadding = barHeight,
                        onOpenArtist = onOpenArtist,
                        onNameBottom = { nameBottom = it },
                    )
                }
                content()
            }

            TintedTopBar(
                name = name,
                shade = shade,
                collapsed = collapsed,
                onBack = onBack,
                onMore = if (menu != null) ({ menuOpen = true }) else null,
                modifier = contents.modifier,
            )
        }
    }

    if (menuOpen && menu != null) menu { menuOpen = false }
}

@Composable
private fun TintedHeader(
    name: String,
    models: List<Any>,
    cover: Modifier,
    artists: String?,
    year: Int?,
    summary: String?,
    origin: Origin?,
    isExplicit: Boolean,
    description: String?,
    shade: Color,
    topPadding: Dp,
    onOpenArtist: (() -> Unit)?,
    onNameBottom: (Int) -> Unit,
) = Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier
        .fillMaxWidth()
        .background(
            Brush.verticalGradient(
                0f to shade,
                0.7f to shade,
                1f to MaterialTheme.colorScheme.surfaceDim,
            )
        )
        .padding(top = topPadding + 4.dp, bottom = 28.dp),
) {
    val dim = Color.White.copy(alpha = TINTED_SECONDARY_ALPHA)

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        val shape = MaterialTheme.shapes.medium
        Thumbnail(
            models = models,
            shape = shape,
            modifier = Modifier
                .size(TINTED_COVER_SIZE)
                .then(cover)
                .shadow(12.dp, shape),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned {
                    // The column's top in the header plus the name inside it is close enough
                    onNameBottom((it.positionInParent().y + it.size.height).toInt())
                },
        ) {
            if (origin != null || isExplicit)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 2.dp),
                ) {
                    origin?.let { OriginBadge(it, TINTED_BADGE_SIZE, tint = dim) }
                    if (isExplicit) ExplicitBadge(TINTED_BADGE_SIZE)
                }
            Text(
                pluralStringResource(R.plurals.unit_album, 1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = dim,
            )
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            ArtistLine(artists, year, onOpenArtist, linkColor = Color.White, color = dim)
            summary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = dim)
            }
        }
    }

    // Its own padding, its tap area reaches the edges
    description?.let { DetailDescription(it, color = dim, linkColor = Color.White) }
}

/** The bar of the tinted design: the header's color, the [name] showing once [collapsed]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TintedTopBar(
    name: String,
    shade: Color,
    collapsed: Boolean,
    onBack: () -> Unit,
    onMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
) = CompositionLocalProvider(LocalTooltipSide provides TooltipSide.BELOW) {
    val title by animateFloatAsState(if (collapsed) 1f else 0f, label = "album bar title")

    TopAppBar(
        title = {
            Text(
                name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(title),
            )
        },
        navigationIcon = { BackButton(onBack) },
        actions = { onMore?.let { MoreSheetButton(it) } },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = shade,
            scrolledContainerColor = shade,
            navigationIconContentColor = Color.White,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
        modifier = modifier,
    )
}

/**
 * The page of the album's artist: of the artists credited on its [songs][this], the first that
 * the album names in [names], else the first credited on its first song. `null` without credits.
 */
internal fun List<MediaItem>.albumArtistRoute(names: List<String>?): Route? {
    val credits = flatMap { song ->
        val extras = song.mediaMetadata.extras
        val ids = extras?.getStringArray(EXTRA_ARTIST_IDS).orEmpty()
        val credited = extras?.getStringArray(EXTRA_ARTIST_NAMES).orEmpty()
        ids.mapIndexed { index, id -> Triple(song.origin, id, credited.getOrNull(index)) }
    }
    val artist = names.orEmpty().firstNotNullOfOrNull { name ->
        credits.firstOrNull { it.third == name }
    } ?: credits.firstOrNull()
    return artist?.let { (origin, id) -> artistRoute(origin, id) }
}

/**
 * The sheet of an album's ⋮ button: Play, Bookmark and Share as buttons, then Shuffle, Play next,
 * Add to queue, the artist, Statistics and, for an album that can be, Reorder as rows.
 *
 * @param onAction runs a [QueueAction] with the album's songs, `null` while there are none: the
 * buttons and rows of those are off
 * @param isBookmarked whether the album is bookmarked, `null` turns Bookmark off, while it isn't
 * known
 * @param share gives a link to the album, `null` turns Share off
 * @param onOpenArtist opens the album's artist, `null` hides the row
 * @param isReordering whether the songs are being moved, `null` hides Reorder
 * @param onResetOrder what a long press on Reorder does, asking to put back the album's order
 */
@Composable
fun AlbumMenu(
    name: String?,
    onDismiss: () -> Unit,
    onAction: ((QueueAction) -> Unit)?,
    isBookmarked: Boolean?,
    onToggleBookmark: () -> Unit,
    share: (suspend () -> Result<String>)?,
    onOpenArtist: (() -> Unit)?,
    onOpenStatistics: () -> Unit,
    isReordering: Boolean? = null,
    canReorder: Boolean = true,
    onReorder: () -> Unit = {},
    onResetOrder: () -> Unit = {},
) {
    val linkShare = rememberLinkShare(name, R.plurals.unit_album) {
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
                isSet = isBookmarked == true,
                enabled = isBookmarked != null,
                setIcon = R.drawable.bookmark_filled,
                unsetIcon = R.drawable.bookmark_outline,
                label = stringResource(R.string.menu_bookmark),
                onClick = onToggleBookmark,
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
            if (onOpenArtist != null)
                ConfigEntry(
                    R.drawable.artist,
                    stringResource(
                        R.string.menu_go_to,
                        pluralStringResource(R.plurals.unit_artist, 1),
                    ),
                ) { close(onOpenArtist) }
            ConfigEntry(R.drawable.insert_chart, stringResource(R.string.menu_statistics)) {
                close(onOpenStatistics)
            }
            if (isReordering != null)
                ConfigEntry(
                    icon = if (isReordering) R.drawable.lock_open else R.drawable.lock,
                    label = if (isReordering)
                        stringResource(R.string.semantic_lock_the_order)
                    else
                        stringResource(R.string.semantic_reorder),
                    // Stays on with fewer songs, so it can be turned off
                    enabled = isReordering || canReorder,
                    onLongClick = { close(onResetOrder) },
                ) { close(onReorder) }
        },
    )
}

@ScreenPreviews
@Composable
private fun AlbumDetailPreview() = PreviewTheme {
    val models = remember { listOf<Any>(previewCover()) }
    val songs = previewSongs()
    val resources = LocalResources.current

    AlbumDetailScaffold(
        name = "Afterglow",
        albumId = "",
        models = models,
        artists = "Neon Coast",
        year = 2021,
        summary = resources.albumSummary(
            songs.size,
            songs.sumOf { it.mediaMetadata.durationMs ?: 0 }),
        origin = Origin.LOCAL,
        isExplicit = true,
        onBack = {},
        description = "Recorded over one summer on the coast, Afterglow follows a single evening " +
                "from the last light to the first stars, with synths that drift like the tide.",
        onOpenArtist = {},
        // What the cover gives, a static preview runs no effect to work it out
        tint = Color(0xFF8E3B6B),
        menu = {},
    ) {
        item(key = "disc1") { DiscHeader(1) }
        itemsIndexed(songs.take(3), key = { _, it -> it.mediaId }) { index, song ->
            AlbumTrack(song, index + 1, onClick = {})
        }
        item(key = "disc2") { DiscHeader(2) }
        itemsIndexed(songs.drop(3), key = { _, it -> it.mediaId }) { index, song ->
            AlbumTrack(song, index + 1, onClick = {})
        }
    }
}
