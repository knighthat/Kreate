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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.repository.UserArtistRepository
import me.knighthat.kreate3.download.SongStorage
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.ui.navigation.LocalNavigator
import me.knighthat.kreate3.ui.navigation.Route
import me.knighthat.kreate3.ui.navigation.albumRoute
import me.knighthat.kreate3.ui.navigation.artistRoute
import me.knighthat.kreate3.ui.screen.detail.rememberSongShare
import me.knighthat.kreate3.ui.screen.player.Reaction
import me.knighthat.kreate3.ui.screen.player.rememberSongReaction
import org.koin.compose.koinInject
import kotlin.math.max
import kotlin.math.roundToInt

/** Space between the lifted row and the open sheet, and between the row and the screen's sides. */
private val LIFTED_ROW_GAP = 12.dp

/** Corners of the lifted row once the sheet is open. */
private val LIFTED_ROW_CORNER = 16.dp

/** Shadow of the lifted row once the sheet is open. */
private val LIFTED_ROW_ELEVATION = 6.dp

/**
 * An action of a song's menu that only the list the song is in has, like taking it out of a
 * playlist. The menu closes before [onClick] runs.
 */
@Immutable
class SongMenuAction(
    val label: String,
    @param:DrawableRes val icon: Int,
    val onClick: () -> Unit,
)

/** The menu a row asked for, see [SongMenuHost]. */
@Immutable
internal class SongMenuRequest(
    /** The row that is lifted, it stays empty while the menu is open. */
    val owner: Any,
    val item: MediaItem,
    /** Where the row is on the screen. */
    val rowBounds: Rect,
    val showThumbnail: Boolean,
    val showAlbum: Boolean,
    val actions: List<SongMenuAction>,
    /** What the row shows at the end in place of the length, see [SongItem]. */
    val trailing: (@Composable () -> Unit)?,
    /** The line above the title and the badges as the row shows them, see [SongItem]. */
    val overline: String? = null,
    val overlineColor: Color = Color.Unspecified,
    val showBadges: Boolean = true,
    val showDisliked: Boolean = true,
)

/**
 * Shows the [SongMenu] of one song at a time, for the whole app, see [SongMenuOverlay]. A row
 * opens it by setting [request].
 */
@Stable
class SongMenuHost {
    internal var request: SongMenuRequest? by mutableStateOf(null)
}

/** The app's [SongMenuHost], `null` where there is none, like in previews: rows have no menu. */
val LocalSongMenuHost = staticCompositionLocalOf<SongMenuHost?> { null }

/** An artist of a song, [name] is `null` while it isn't known. */
@Immutable
private class SongArtist(val id: String, val name: String?, val route: Route)

private enum class SongMenuPage { ACTIONS, ARTISTS, PLAYLISTS }

/**
 * Where the sheet of [sheetState] is on the screen, followed by the lifted row. The sheet moves
 * by its offset, read in the same frame. Its size and window come from its layout, which reaches
 * the app's window a frame late.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
private class SheetPlacement(private val sheetState: SheetState) {
    /** The sheet as last laid out, `null` until it is. */
    var sheet: Rect? by mutableStateOf(null)

    /** Top of the window the sheet slides in. */
    var windowTop by mutableFloatStateOf(0f)

    /** Bottom of the window the sheet slides in, where it hides. */
    var windowBottom by mutableFloatStateOf(0f)

    /**
     * Top edge of the sheet once it's open, `null` until it is laid out. Like `ModalBottomSheet`,
     * it opens as high as its height allows, and hides at the bottom of the window.
     */
    val openTop: Float?
        get() = sheet?.let { max(windowTop, windowBottom - it.height) }

    /** Top edge of the sheet now, `null` until it is laid out. */
    val top: Float?
        get() {
            val sheet = sheet ?: return null
            // Its offset is from the top of the window, unknown before the sheet is measured
            val offset = runCatching { sheetState.requireOffset() }.getOrNull()
            return offset?.let { windowTop + it } ?: sheet.top
        }

    /** How far the sheet is open, from `0` hidden to `1` open. */
    val progress: Float
        get() {
            val top = top ?: return 0f
            val travel = windowBottom - (openTop ?: return 0f)
            return if (travel <= 0f) 1f else ((windowBottom - top) / travel).coerceIn(0f, 1f)
        }

    fun update(coordinates: LayoutCoordinates) {
        val window = coordinates.findRootCoordinates()
        windowTop = window.positionOnScreen().y
        windowBottom = windowTop + window.size.height
        sheet = Rect(coordinates.positionOnScreen(), coordinates.size.toSize())
    }
}

/**
 * The menu [host] has open, over everything else: the song's row lifted out of its list to hover
 * above the [SongMenu]. Place it last, over the whole window.
 */
@Composable
fun SongMenuOverlay(host: SongMenuHost) {
    val request = host.request ?: return

    key(request) {
        SongMenu(
            request = request,
            onClosed = { if (host.request === request) host.request = null },
        )
    }
}

/**
 * The menu of the song of [request], as a bottom sheet: the reactions and Share at its top, then
 * what can be done with the song. The song's row hovers above the sheet, it rises out of the list
 * as the sheet opens and goes back as it closes. Actions of the list the song is in come after
 * the song's own. Going to the album or an artist is left out when that screen is the one open.
 *
 * Reactions are off for a song that isn't in the library, and playing it for one that can't be.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongMenu(request: SongMenuRequest, onClosed: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val placement = remember(sheetState) { SheetPlacement(sheetState) }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(SongMenuPage.ACTIONS) }
    val artists = rememberSongArtists(request.item)

    // Slides the sheet away, taking the row back to the list, before doing what was picked
    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onClosed()
            then()
        }
    }

    LiftedRow(request, placement)

    ModalBottomSheet(
        onDismissRequest = onClosed,
        sheetState = sheetState,
        // The lifted row dims the screen, so that it stays clear itself
        scrimColor = Color.Transparent,
        dragHandle = null,
        // Padded inside, so the content is as big as the sheet, see SheetPlacement
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            Modifier
                .onGloballyPositioned(placement::update)
                .windowInsetsPadding(BottomSheetDefaults.windowInsets)
                .verticalScroll(rememberScrollState())
        ) {
            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
            SongMenuButtons(request.item, ::close)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            when (page) {
                SongMenuPage.ACTIONS -> SongMenuActions(
                    item = request.item,
                    artists = artists,
                    actions = request.actions,
                    close = ::close,
                    onPickArtist = { page = SongMenuPage.ARTISTS },
                    onPickPlaylist = { page = SongMenuPage.PLAYLISTS },
                )

                SongMenuPage.ARTISTS -> ArtistPicker(
                    artists = artists,
                    onBack = { page = SongMenuPage.ACTIONS },
                    close = ::close,
                )

                SongMenuPage.PLAYLISTS -> PlaylistPicker(
                    item = request.item,
                    onBack = { page = SongMenuPage.ACTIONS },
                    close = ::close,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * The scrim of the menu, and the row of [request] over it: in its place in the list while the
 * sheet is hidden, hovering above the sheet like a card once it's open, and in between as it
 * moves. Drawn in the app's window, under the sheet's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiftedRow(request: SongMenuRequest, placement: SheetPlacement) {
    val scrim = BottomSheetDefaults.ScrimColor
    val container = BottomSheetDefaults.ContainerColor
    val statusBars = WindowInsets.statusBars
    var origin by remember { mutableStateOf(Offset.Zero) }
    val row = request.rowBounds

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionOnScreen() }
            .drawBehind { drawRect(scrim, alpha = placement.progress) }
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            SongRow(
                item = request.item,
                // The sheet's window takes the taps, a tap here closes the menu
                onClick = {},
                showThumbnail = request.showThumbnail,
                showAlbum = request.showAlbum,
                trailing = request.trailing,
                overline = request.overline,
                overlineColor = request.overlineColor,
                showBadges = request.showBadges,
                showDisliked = request.showDisliked,
                modifier = Modifier
                    .layout { measurable, _ ->
                        val progress = placement.progress
                        val sheet = placement.sheet
                        val gap = LIFTED_ROW_GAP.toPx()
                        val left =
                            lerp(row.left, sheet?.let { it.left + gap } ?: row.left, progress)
                        val right =
                            lerp(row.right, sheet?.let { it.right - gap } ?: row.right, progress)
                        // Above the open sheet, not where the sheet is now: on the way the row
                        // would dip under the rising sheet. Both move with the progress, so the
                        // sheet stays below the row all along. A tall sheet leaves no room above
                        // it, the row then stays below the status bar and covers the sheet's top
                        val highest = origin.y + statusBars.getTop(this) + gap
                        val above = placement.openTop
                            ?.let { max(it - gap - row.height, highest) } ?: row.top
                        val top = lerp(row.top, above, progress)

                        val width = (right - left).roundToInt().coerceAtLeast(0)
                        val placeable =
                            measurable.measure(Constraints.fixed(width, row.height.roundToInt()))
                        layout(placeable.width, placeable.height) {
                            placeable.place(
                                (left - origin.x).roundToInt(),
                                (top - origin.y).roundToInt()
                            )
                        }
                    }
                    .graphicsLayer {
                        val progress = placement.progress
                        shadowElevation = LIFTED_ROW_ELEVATION.toPx() * progress
                        shape = RoundedCornerShape(LIFTED_ROW_CORNER.toPx() * progress)
                        clip = true
                    }
                    .drawBehind { drawRect(container, alpha = placement.progress) },
            )
        }
    }
}

/**
 * The artists of [item] that have a screen to go to, without the one open. Names come with a
 * song fetched live, the library's are looked up.
 */
@Composable
private fun rememberSongArtists(item: MediaItem): List<SongArtist> {
    val navigator = LocalNavigator.current
    val library = koinInject<UserArtistRepository>()
    val origin = item.origin
    val extras = item.mediaMetadata.extras
    val known = remember(item, navigator?.current) {
        val ids = extras?.getStringArray(EXTRA_ARTIST_IDS).orEmpty()
        val names = extras?.getStringArray(EXTRA_ARTIST_NAMES)?.takeIf { it.size == ids.size }
        ids.mapIndexed { index, id ->
            SongArtist(id, names?.get(index)?.ifBlank { null }, artistRoute(origin, id))
        }.filter { it.route != navigator?.current }
    }

    val artists by produceState(known, known, library) {
        // Only the picker shows names, a single artist is gone to without one
        if (known.size < 2 || known.all { it.name != null }) return@produceState
        value = known.map {
            if (it.name != null) it
            else SongArtist(it.id, library.find(it.id).first()?.name, it.route)
        }
    }
    return artists
}

/** The reactions, Share and the download button, at the top of the sheet. */
@Composable
private fun SongMenuButtons(item: MediaItem, close: (then: () -> Unit) -> Unit) {
    val reaction = rememberSongReaction(item)
    val share = rememberSongShare(item)

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ReactionToggle(
            isSet = reaction.reaction == Reaction.DISLIKED,
            enabled = reaction.reaction != null,
            setIcon = R.drawable.thumb_down,
            unsetIcon = R.drawable.thumb_down_outline,
            label = stringResource(R.string.menu_dislike),
            onClick = reaction.toggleDislike,
        )
        ReactionToggle(
            isSet = reaction.reaction == Reaction.LIKED,
            enabled = reaction.reaction != null,
            setIcon = R.drawable.thumb_up,
            unsetIcon = R.drawable.thumb_up_outline,
            label = stringResource(R.string.menu_like),
            onClick = reaction.toggleLike,
        )
        LabeledIconButton(
            icon = R.drawable.share,
            label = stringResource(R.string.menu_share),
            enabled = share.enabled,
            // A link of the server can take a moment, the sheet stays until it is there
            onClick = { share.share { close {} } },
        )
        SongDownloadAction(item) { icon, label, enabled, onClick ->
            LabeledIconButton(icon = icon, label = label, enabled = enabled, onClick = onClick)
        }
    }
}

@Composable
private fun SongMenuActions(
    item: MediaItem,
    artists: List<SongArtist>,
    actions: List<SongMenuAction>,
    close: (then: () -> Unit) -> Unit,
    onPickArtist: () -> Unit,
    onPickPlaylist: () -> Unit,
) {
    val playback = koinInject<PlaybackController>()
    val notifier = LocalNotifier.current
    val resources = LocalResources.current
    val willPlayNext = stringResource(R.string.snackbar_will_play_next_one)
    val navigator = LocalNavigator.current
    val playable = item.mediaMetadata.isPlayable == true
    val album = remember(item, navigator?.current) {
        item.mediaMetadata.extras?.getString(EXTRA_ALBUM_ID)
            ?.let { albumRoute(item.origin, it) }
            ?.takeIf { it != navigator?.current }
    }

    MenuRow(
        R.drawable.queue_play_next,
        stringResource(R.string.menu_play_next),
        enabled = playable
    ) {
        close {
            playback.playNext(listOf(item))
            notifier.post(willPlayNext)
        }
    }
    MenuRow(
        R.drawable.playlist_add,
        stringResource(R.string.menu_add_to_queue),
        enabled = playable
    ) {
        close { playback.queue(resources, QueueAction.ENQUEUE, listOf(item))?.let(notifier::post) }
    }
    MenuRow(
        icon = R.drawable.library_add,
        label = stringResource(R.string.menu_add_to_playlist),
        trailing = R.drawable.chevron_right,
        onClick = onPickPlaylist,
    )
    MenuRow(R.drawable.radio, stringResource(R.string.menu_start_radio), enabled = playable) {
        close { playback.radio(item) }
    }
    if (navigator != null && album != null)
        MenuRow(
            R.drawable.album,
            stringResource(R.string.menu_go_to, pluralStringResource(R.plurals.unit_album, 1))
        ) { close { navigator.open(album) } }
    if (navigator != null && artists.isNotEmpty())
        MenuRow(
            icon = R.drawable.artist,
            label = stringResource(
                R.string.menu_go_to,
                pluralStringResource(R.plurals.unit_artist, artists.size),
            ),
            trailing = R.drawable.chevron_right.takeIf { artists.size > 1 },
        ) {
            if (artists.size == 1) close { navigator.open(artists.single().route) }
            else onPickArtist()
        }
    actions.forEach { action ->
        MenuRow(action.icon, action.label) { close(action.onClick) }
    }
}

@Composable
private fun ArtistPicker(
    artists: List<SongArtist>,
    onBack: () -> Unit,
    close: (then: () -> Unit) -> Unit,
) {
    val navigator = LocalNavigator.current ?: return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        BackButton(onBack)
        Text(
            stringResource(R.string.menu_title_artists),
            style = MaterialTheme.typography.titleMedium
        )
    }
    artists.forEach { artist ->
        MenuRow(R.drawable.artist, artist.name ?: stringResource(R.string.status_unknown_artist)) {
            close { navigator.open(artist.route) }
        }
    }
}

/**
 * The playlists made in the app, the newest first, to put the song of [item] in. A new one can be
 * made on the spot, the song goes in it.
 */
@Composable
private fun PlaylistPicker(
    item: MediaItem,
    onBack: () -> Unit,
    close: (then: () -> Unit) -> Unit,
) {
    val picker = rememberAddToPlaylist(item)
    var creating by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        BackButton(onBack)
        Text(
            text = stringResource(R.string.menu_title_add_to_playlist),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        CreatePlaylistButton({ creating = true })
    }
    if (picker.playlists?.isEmpty() == true)
        Text(
            text = stringResource(R.string.status_no_playlists_yet),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    picker.playlists?.forEach { playlist ->
        MenuRow(R.drawable.queue_music, playlist.name) { close { picker.add(playlist) } }
    }

    if (creating)
        CreatePlaylistDialog(
            onCreate = { name ->
                creating = false
                close { picker.create(name) }
            },
            onDismiss = { creating = false },
        )
}

@Composable
private fun MenuRow(
    @DrawableRes icon: Int,
    label: String,
    enabled: Boolean = true,
    @DrawableRes trailing: Int? = null,
    onClick: () -> Unit,
) = ListItem(
    headlineContent = { Text(label) },
    leadingContent = { Icon(painterResource(icon), contentDescription = null) },
    trailingContent = trailing?.let { { Icon(painterResource(it), contentDescription = null) } },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier = Modifier
        .clickable(enabled = enabled, onClick = onClick)
        // The look of a disabled button
        .alpha(if (enabled) 1f else 0.38f),
)

@Composable
private fun ReactionToggle(
    isSet: Boolean,
    enabled: Boolean,
    @DrawableRes setIcon: Int,
    @DrawableRes unsetIcon: Int,
    label: String,
    onClick: () -> Unit,
) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    IconToggleButton(checked = isSet, onCheckedChange = { onClick() }, enabled = enabled) {
        Icon(painterResource(if (isSet) setIcon else unsetIcon), contentDescription = label)
    }
    ButtonLabel(label, enabled)
}

/**
 * A button of the row at the top of a menu sheet: [icon] over [label], dimmed and off unless
 * [enabled]. Without [showLabel] the label only describes the icon to screen readers.
 */
@Composable
internal fun LabeledIconButton(
    @DrawableRes icon: Int,
    label: String,
    enabled: Boolean = true,
    showLabel: Boolean = true,
    onClick: () -> Unit,
) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), contentDescription = label)
    }
    if (showLabel) ButtonLabel(label, enabled)
}

@Composable
private fun ButtonLabel(text: String, enabled: Boolean) = Text(
    text = text,
    style = MaterialTheme.typography.labelMedium,
    modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
)

//<editor-fold defaultstate="collapsed" desc="Previews">
/** What the sheet shows, from its parts: the menu itself reads the song's reaction from Koin. */
@OptIn(ExperimentalMaterial3Api::class)
@ComponentPreviews
@Composable
private fun SongMenuPreview() = PreviewTheme {
    Surface(color = BottomSheetDefaults.ContainerColor) {
        Column(modifier = Modifier.width(360.dp)) {
            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ReactionToggle(
                    isSet = false,
                    enabled = true,
                    setIcon = R.drawable.thumb_down,
                    unsetIcon = R.drawable.thumb_down_outline,
                    label = stringResource(R.string.menu_dislike),
                    onClick = {},
                )
                ReactionToggle(
                    isSet = true,
                    enabled = true,
                    setIcon = R.drawable.thumb_up,
                    unsetIcon = R.drawable.thumb_up_outline,
                    label = stringResource(R.string.menu_like),
                    onClick = {},
                )
                LabeledIconButton(
                    icon = R.drawable.share,
                    label = stringResource(R.string.menu_share),
                    enabled = false,
                    onClick = {},
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            // Not playable
            MenuRow(
                R.drawable.queue_play_next,
                stringResource(R.string.menu_play_next),
                enabled = false
            ) {}
            MenuRow(
                icon = R.drawable.library_add,
                label = stringResource(R.string.menu_add_to_playlist),
                trailing = R.drawable.chevron_right,
            ) {}
            MenuRow(R.drawable.radio, stringResource(R.string.menu_start_radio)) {}
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * The download button of the header in each stage, from nothing kept to downloaded, and last
 * disabled while downloads are off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@ComponentPreviews
@Composable
private fun SongMenuDownloadStagesPreview() = PreviewTheme {
    Surface(color = BottomSheetDefaults.ContainerColor) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.width(500.dp),
        ) {
            listOf(
                null,
                SongStorage.DOWNLOADING,
                SongStorage.PAUSED,
                SongStorage.CACHED,
                SongStorage.DOWNLOADED,
            ).forEach { storage ->
                LabeledIconButton(
                    icon = storage.icon,
                    label = stringResource(storage.labelRes),
                    enabled = true,
                    onClick = {},
                )
            }
            LabeledIconButton(
                icon = (null as SongStorage?).icon,
                label = stringResource((null as SongStorage?).labelRes),
                enabled = false,
                onClick = {},
            )
        }
    }
}
//</editor-fold>
