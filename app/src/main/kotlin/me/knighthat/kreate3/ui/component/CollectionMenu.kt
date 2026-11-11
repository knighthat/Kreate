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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.QueueAction
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.database.repository.RemoteSong
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.Shareable
import me.knighthat.kreate3.provider.youtube.YouTubeLinks
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.SongStore
import me.knighthat.kreate3.source.withoutDisliked
import me.knighthat.kreate3.source.youtube.SONG_ARTWORK
import me.knighthat.kreate3.source.youtube.artwork
import me.knighthat.kreate3.ui.navigation.AlbumDetails
import me.knighthat.kreate3.ui.navigation.ArtistDetails
import me.knighthat.kreate3.ui.navigation.EditAlbum
import me.knighthat.kreate3.ui.navigation.EditArtist
import me.knighthat.kreate3.ui.navigation.EditOnlinePlaylist
import me.knighthat.kreate3.ui.navigation.EditPlaylist
import me.knighthat.kreate3.ui.navigation.LocalNavigator
import me.knighthat.kreate3.ui.navigation.artistRoute
import me.knighthat.kreate3.ui.screen.detail.ShareKind
import me.knighthat.kreate3.ui.screen.detail.canShare
import me.knighthat.kreate3.ui.screen.detail.itemName
import me.knighthat.kreate3.ui.screen.detail.rememberLinkShare
import me.knighthat.kreate3.ui.screen.detail.shareLinkOf
import org.koin.compose.koinInject

/** The height of an album or artist card once it is lifted as a row above its menu. */
internal val LIFTED_ROW_HEIGHT = ROW_THUMBNAIL_SIZE + 12.dp

/** What a menu of a list item with songs is for. */
internal enum class CollectionKind(val shareKind: ShareKind) {
    ALBUM(ShareKind.ALBUM),
    ARTIST(ShareKind.ARTIST),
    PLAYLIST(ShareKind.PLAYLIST),
}

/**
 * The menu of an album, an artist or a playlist, in a [SongMenuRequest].
 *
 * @property slides whether the copy comes up with the sheet instead of rising out of the item's
 * place in the list: as wide as the sheet and [LIFTED_ROW_HEIGHT] tall, for a card of a grid
 * @property lifted draws the copy that hovers above the sheet
 */
@Immutable
internal class CollectionMenu(
    val kind: CollectionKind,
    val item: MediaItem,
    val slides: Boolean,
    val lifted: @Composable () -> Unit,
)

/**
 * What an album, artist or playlist item needs to open its menu by a long press: [modifier] goes
 * on the item, [onLongClick] is `null` where there is no menu host, like in previews.
 */
@Stable
internal class CollectionAnchor(val modifier: Modifier, val onLongClick: (() -> Unit)?)

/**
 * Lets the long press of an item open its [CollectionMenu]. The item stays empty in the list while
 * its menu is open, [lifted] draws the copy that hovers above the sheet.
 */
@Composable
internal fun rememberCollectionAnchor(
    item: MediaItem,
    kind: CollectionKind,
    slides: Boolean,
    lifted: @Composable () -> Unit,
): CollectionAnchor {
    val host = LocalSongMenuHost.current
    val anchor = remember { RowAnchor() }
    val isLifted by remember(host, anchor) { derivedStateOf { host?.request?.owner === anchor } }
    val currentLifted by rememberUpdatedState(lifted)

    return CollectionAnchor(
        modifier = Modifier
            .onGloballyPositioned { anchor.coordinates = it }
            // A card that doesn't rise out of its place stays where it is
            .graphicsLayer { alpha = if (isLifted && !slides) 0f else 1f },
        onLongClick = if (host == null) {
            null
        } else {
            {
                anchor.coordinates?.takeIf { it.isAttached }?.let {
                    host.request = SongMenuRequest(
                        owner = anchor,
                        item = item,
                        rowBounds = Rect(it.positionOnScreen(), it.size.toSize()),
                        showThumbnail = true,
                        showAlbum = true,
                        actions = emptyList(),
                        trailing = null,
                        collection = CollectionMenu(kind, item, slides) { currentLifted() },
                    )
                }
            }
        },
    )
}

/**
 * What an album or artist card is lifted as above its menu: a row. It doesn't come out of the grid,
 * it slides up with the sheet, see [CollectionMenu.slides].
 */
@Composable
internal fun LiftedItemRow(item: MediaItem) =
    ItemRow(item = item, onClick = {}, modifier = Modifier.fillMaxSize())

/**
 * The songs of albums, artists and playlists as the items of a list, wherever they are kept. The
 * ones the user disliked are left out. Remote ones are fetched, nothing is stored.
 */
internal class CollectionSongs(
    private val library: UserSongRepository,
    private val youTube: YouTubeProvider,
    private val openSubsonic: OpenSubsonicProvider,
) {

    /**
     * The songs of [item], in its order: an album's or a playlist's, every song of an artist of
     * the device or the server, the most played ones of an artist of YouTube (what its page plays).
     *
     * @return `null` when the source is off or doesn't have it
     * @throws Exception when the source can't be asked
     */
    suspend fun of(kind: CollectionKind, item: MediaItem): List<MediaItem>? =
        withContext(Dispatchers.Default) {
            val songs = when (item.origin) {
                Origin.YOUTUBE -> fromYouTube(kind, item)
                Origin.OPEN_SUBSONIC -> fromServer(kind, item)
                else -> fromDevice(kind, item)
            }
            songs?.withoutDisliked(library.listDislikedIds())
        }

    private suspend fun fromDevice(kind: CollectionKind, item: MediaItem): List<MediaItem> =
        when (kind) {
            CollectionKind.ALBUM -> library.listOfAlbum(item.mediaId)
            CollectionKind.ARTIST -> library.listOfArtist(item.mediaId)
            CollectionKind.PLAYLIST -> library.listOfPlaylist(item.mediaId.toLong())
        }.mapNotNull { it.toMediaItem() }

    private suspend fun fromServer(kind: CollectionKind, item: MediaItem): List<MediaItem>? {
        val songs = when (kind) {
            CollectionKind.ALBUM -> openSubsonic.albumWithSongs(item.mediaId)?.second
            CollectionKind.ARTIST -> openSubsonic.artistWithSongs(item.mediaId)?.songs
            CollectionKind.PLAYLIST ->
                openSubsonic.playlistWithSongs(item.remoteId ?: item.mediaId)?.songs
        }
        return songs?.map { it.toLiveMediaItem() }?.distinctBy { it.mediaId }
    }

    private suspend fun fromYouTube(kind: CollectionKind, item: MediaItem): List<MediaItem>? {
        fun RemoteSong.item() = toLiveMediaItem(
            youTube.artwork(song.thumbnail ?: album?.thumbnail, SONG_ARTWORK)
        )

        val songs = when (kind) {
            CollectionKind.ALBUM -> youTube.albumPage(item.mediaId)?.let { page ->
                page.songs.map {
                    it.toLiveMediaItem(
                        youTube.artwork(it.song.thumbnail ?: page.album.thumbnail, SONG_ARTWORK)
                    )
                }
            }

            CollectionKind.ARTIST -> youTube.artistPage(item.mediaId)?.topSongs?.map { it.item() }

            CollectionKind.PLAYLIST -> {
                val page = youTube.playlistPage(item.remoteId ?: item.mediaId) ?: return null
                val entries = page.entries.toMutableList()
                var next = page.continuation
                while (next != null) {
                    val more = youTube.playlistEntries(next)
                    entries += more.items
                    next = more.continuation
                }
                entries.map { it.song.item() }
            }
        }
        return songs?.distinctBy { it.mediaId }
    }
}

/** The artists an album item names, taken from the credits of its songs. */
private fun albumArtists(album: MediaItem, songs: List<MediaItem>): List<SongArtist> {
    val credits = songs.flatMap { song ->
        val extras = song.mediaMetadata.extras
        val ids = extras?.getStringArray(EXTRA_ARTIST_IDS).orEmpty()
        val names = extras?.getStringArray(EXTRA_ARTIST_NAMES).orEmpty()
        ids.mapIndexed { index, id ->
            SongArtist(id, names.getOrNull(index), artistRoute(song.origin, id))
        }
    }.distinctBy { it.id }
    // The album names its artists, a song can have guests the album isn't credited to
    val named = album.mediaMetadata.artist?.toString().orEmpty()
    return credits.filter { it.name != null && named.contains(it.name) }
        .ifEmpty { credits.take(1) }
}

private enum class CollectionPage { ACTIONS, ARTISTS, PLAYLISTS }

/** What a control waits for while the songs are fetched, the artists row or the picked playlist. */
private object FindingArtists

/**
 * The buttons and rows of the menu of an album, artist or playlist: Play, Shuffle and Share on top
 * (no Share for the device's items), then Play next, Add to queue and what else can be done. The songs are fetched when a
 * control needs them, the sheet stays open with a spinner on it until they are there.
 */
@Composable
internal fun CollectionMenuContent(
    menu: CollectionMenu,
    close: (then: () -> Unit) -> Unit,
) {
    val item = menu.item
    val kind = menu.kind
    val origin = item.origin
    val resources = LocalResources.current
    val notifier = LocalNotifier.current
    val navigator = LocalNavigator.current
    val playback = koinInject<PlaybackController>()
    val appScope = koinInject<CoroutineScope>()
    val library = koinInject<UserSongRepository>()
    val youTube = koinInject<YouTubeProvider>()
    val openSubsonic = koinInject<OpenSubsonicProvider>()
    val songSource = remember(library, youTube, openSubsonic) {
        CollectionSongs(library, youTube, openSubsonic)
    }
    val scope = rememberCoroutineScope()
    val openRoute = rememberRouteOpener()
    var page by remember { mutableStateOf(CollectionPage.ACTIONS) }
    var waiting by remember { mutableStateOf<Any?>(null) }
    var artists by remember { mutableStateOf<List<SongArtist>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
    val name = resources.itemName(
        title,
        when (kind) {
            CollectionKind.ALBUM -> R.string.snackbar_this_album
            CollectionKind.ARTIST -> R.string.snackbar_this_artist
            CollectionKind.PLAYLIST -> R.string.snackbar_this_playlist
        }
    )

    /** Fetches the songs under a spinner on the control [key], then hands them to [then]. */
    fun withSongs(key: Any, then: (List<MediaItem>) -> Unit) {
        if (waiting != null) return
        waiting = key
        scope.launch {
            val songs = try {
                songSource.of(kind, item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, "CollectionMenu") { "Couldn't get the songs of ${item.mediaId}" }
                null
            }
            waiting = null
            if (songs == null)
                notifier.post(Message(resources.getString(R.string.error_collection_songs, name)))
            else
                then(songs)
        }
    }

    fun queue(action: QueueAction) = withSongs(action) { songs ->
        close {
            appScope.launch(Dispatchers.Default) {
                playback.queue(resources, action, songs)?.let(notifier::post)
            }
        }
    }

    // The device's items have no link
    val shares = origin != null && origin != Origin.LOCAL
    val serverShares by remember(openSubsonic) { openSubsonic.canShare(scope) }
        .collectAsStateWithLifecycle()
    val share = rememberLinkShare(title, kind.shareKind) {
        shareLink(kind, item, title, openSubsonic)
    }

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
    ) {
        LabeledIconButton(
            icon = R.drawable.play_arrow,
            label = stringResource(R.string.menu_shortcut_play),
            loading = waiting == QueueAction.PLAY,
            enabled = waiting == null,
        ) { queue(QueueAction.PLAY) }
        LabeledIconButton(
            icon = R.drawable.shuffle,
            label = stringResource(R.string.menu_shortcut_shuffle),
            loading = waiting == QueueAction.SHUFFLE,
            enabled = waiting == null,
        ) { queue(QueueAction.SHUFFLE) }
        if (shares)
            LabeledIconButton(
                icon = R.drawable.share,
                label = stringResource(R.string.menu_shortcut_share),
                enabled = share.enabled && (origin != Origin.OPEN_SUBSONIC || serverShares),
                loading = share.sharing,
                // A link of the server can take a moment, the sheet stays until it is there
            ) { share.share { close {} } }
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))

    when (page) {
        CollectionPage.ACTIONS -> {
            MenuRow(
                icon = R.drawable.queue_play_next,
                label = stringResource(R.string.menu_play_next),
                loading = waiting == QueueAction.PLAY_NEXT,
                enabled = waiting == null,
            ) { queue(QueueAction.PLAY_NEXT) }
            MenuRow(
                icon = R.drawable.playlist_add,
                label = stringResource(R.string.menu_add_to_queue),
                loading = waiting == QueueAction.ENQUEUE,
                enabled = waiting == null,
            ) { queue(QueueAction.ENQUEUE) }
            if (kind != CollectionKind.PLAYLIST)
                MenuRow(
                    icon = R.drawable.library_add,
                    label = stringResource(R.string.menu_add_to_playlist),
                    trailing = R.drawable.chevron_right,
                ) { page = CollectionPage.PLAYLISTS }
            if (navigator != null && kind != CollectionKind.PLAYLIST)
                MenuRow(
                    R.drawable.info,
                    stringResource(R.string.menu_details),
                    enabled = title != null
                ) {
                    close {
                        openRoute(
                            if (kind == CollectionKind.ALBUM)
                                AlbumDetails(
                                    title.orEmpty(),
                                    (item.mediaMetadata.albumArtist ?: item.mediaMetadata.artist)
                                        ?.toString()
                                )
                            else
                                ArtistDetails(title.orEmpty())
                        )
                    }
                }
            if (navigator != null && kind == CollectionKind.ALBUM)
                MenuRow(
                    icon = R.drawable.artist,
                    label = pluralStringResource(R.plurals.menu_go_to_artist, 1)
                        .replaceFirstChar { it.uppercase() },
                    loading = waiting === FindingArtists,
                    enabled = waiting == null,
                ) {
                    withSongs(FindingArtists) { songs ->
                        val found = albumArtists(item, songs)
                        when (found.size) {
                            0 -> notifier.post(
                                Message(
                                    resources.getString(
                                        R.string.error_artist_not_found,
                                        name
                                    )
                                )
                            )

                            1 -> close { openRoute(found.single().route) }

                            else -> {
                                artists = found
                                page = CollectionPage.ARTISTS
                            }
                        }
                    }
                }
            if (navigator != null && origin != null) {
                val edit = when (kind) {
                    CollectionKind.ALBUM -> EditAlbum(item.mediaId, origin)
                    CollectionKind.ARTIST -> EditArtist(item.mediaId, origin)
                    CollectionKind.PLAYLIST ->
                        if (origin == Origin.LOCAL)
                            item.mediaId.toLongOrNull()?.let(::EditPlaylist)
                        else
                            EditOnlinePlaylist(item.remoteId ?: item.mediaId, origin)
                }
                if (edit != null)
                    MenuRow(R.drawable.edit, stringResource(R.string.menu_edit)) {
                        close { openRoute(edit) }
                    }
            }
            if (kind == CollectionKind.PLAYLIST && origin == Origin.LOCAL)
                MenuRow(R.drawable.delete, stringResource(R.string.semantic_delete_playlist)) {
                    confirmDelete = true
                }
        }

        CollectionPage.ARTISTS -> ArtistPicker(
            artists = artists,
            onBack = { page = CollectionPage.ACTIONS },
            close = close,
        )

        CollectionPage.PLAYLISTS -> CollectionPlaylistPicker(
            item = item,
            onBack = { page = CollectionPage.ACTIONS },
            waiting = waiting,
            withSongs = ::withSongs,
            close = close,
        )
    }

    if (confirmDelete) {
        val repository = koinInject<PlaylistRepository>()

        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.dialog_title_delete_playlist)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_body_delete_playlist,
                        stringResource(R.string.quoted, title.orEmpty())
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    close {
                        appScope.launch {
                            val deleted = try {
                                repository.delete(item.mediaId.toLong())
                                true
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Logger.w(e, "CollectionMenu") { "Couldn't delete ${item.mediaId}" }
                                false
                            }
                            notifier.post(
                                if (deleted)
                                    resources.getString(R.string.snackbar_playlist_deleted, name)
                                else
                                    resources.getString(R.string.error_playlist_delete)
                            )
                        }
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** The link to [item] on its source: YouTube's needs no request, the server's is asked for. */
private suspend fun shareLink(
    kind: CollectionKind,
    item: MediaItem,
    title: String?,
    openSubsonic: OpenSubsonicProvider,
): Result<String> = when (item.origin) {
    Origin.YOUTUBE -> Result.success(
        when (kind) {
            CollectionKind.ALBUM -> YouTubeLinks.album(item.mediaId)
            CollectionKind.ARTIST -> YouTubeLinks.artist(item.mediaId)
            CollectionKind.PLAYLIST -> YouTubeLinks.playlist(item.remoteId ?: item.mediaId)
        }
    )

    Origin.OPEN_SUBSONIC -> openSubsonic.shareLinkOf(
        when (kind) {
            CollectionKind.ALBUM -> Shareable.Album(item.mediaId)
            CollectionKind.ARTIST -> Shareable.Artist(item.mediaId)
            CollectionKind.PLAYLIST -> Shareable.Playlist(item.remoteId ?: item.mediaId)
        },
        title,
    )

    else -> Result.failure(IllegalStateException("Nothing to share"))
}

/**
 * The playlists made in the app, the newest first, to put every song of [item] in. A song fetched
 * live is stored first. The sheet stays until they are in, a long playlist can take a while.
 */
@Composable
private fun CollectionPlaylistPicker(
    item: MediaItem,
    onBack: () -> Unit,
    waiting: Any?,
    withSongs: (key: Any, then: (List<MediaItem>) -> Unit) -> Unit,
    close: (then: () -> Unit) -> Unit,
) {
    val repository = koinInject<PlaylistRepository>()
    val store = koinInject<SongStore>()
    val appScope = koinInject<CoroutineScope>()
    val notifier = LocalNotifier.current
    val resources = LocalResources.current
    val playlists by produceState<List<UserPlaylist>?>(null, repository) {
        value = repository.list(NEWEST_FIRST, setOf(Origin.LOCAL))
    }

    suspend fun add(playlist: UserPlaylist, songs: List<MediaItem>): Message {
        var added = 0
        var failed = false
        for (song in songs) {
            try {
                store.store(song.mediaId, song.origin)
                if (repository.addSong(playlist.id, song.mediaId)) added++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, "CollectionMenu") { "Couldn't add ${song.mediaId} to ${playlist.name}" }
                failed = true
            }
        }
        return Message(
            resources.getString(
                when {
                    added > 0 -> R.string.snackbar_added_to_playlist
                    failed -> R.string.snackbar_couldnt_add_to_playlist
                    else -> R.string.snackbar_already_in_playlist
                },
                playlist.name,
            )
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        BackButton(onBack)
        Text(
            text = stringResource(R.string.menu_title_add_to_playlist),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
    }
    val shown =
        playlists?.filterNot { item.origin == Origin.LOCAL && it.id.toString() == item.mediaId }
    if (shown?.isEmpty() == true)
        PickerStatus(stringResource(R.string.status_no_playlists_yet))
    shown?.forEach { playlist ->
        MenuRow(
            icon = R.drawable.queue_music,
            label = playlist.name,
            loading = waiting == playlist.id,
            enabled = waiting == null,
        ) {
            withSongs(playlist.id) { songs ->
                close { appScope.launch { notifier.post(add(playlist, songs)) } }
            }
        }
    }
}
