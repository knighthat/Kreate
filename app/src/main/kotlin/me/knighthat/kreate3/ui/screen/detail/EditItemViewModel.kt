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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.repository.AlbumRepository
import me.knighthat.kreate3.database.repository.ArtistRepository
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.database.repository.UserAlbumRepository
import me.knighthat.kreate3.database.repository.UserArtistRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.Providers
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.PickedCovers
import me.knighthat.kreate3.source.SongStore
import me.knighthat.kreate3.source.songDetails
import me.knighthat.kreate3.ui.navigation.EDIT_ITEM_ID
import me.knighthat.kreate3.ui.navigation.EDIT_ITEM_ORIGIN

private const val TAG = "EditItem"

/**
 * What the user edits of a song, an album or an artist.
 *
 * @property name the title or name, not trimmed yet
 * @property artists the artists' names, one per line; empty for an artist
 * @property thumbnail the picture: the item's own, or the one picked from the device (a copy of it
 * once applied), `null` for none
 */
data class ItemDraft(
    val name: String = "",
    val artists: String = "",
    val thumbnail: Uri? = null,
) {

    /** [artists] as a list, without empty lines. */
    val artistList: List<String>
        get() = artists.lines().map(String::trim).filter(String::isNotEmpty)

    /** What applying it would store, so changing nothing that shows is no change. */
    internal val result: Triple<String, List<String>, Uri?>
        get() = Triple(name.trim(), artistList, thumbnail)
}

/**
 * @property original the values everyone sees, what "Use original" goes back to
 * @property saved what the user sees now, their own values on top of [original]
 * @property draft what the user made of it, only in memory until applied
 * @property saving whether applying is under way, nothing can change meanwhile
 */
data class EditItemState(
    val original: ItemDraft,
    val saved: ItemDraft,
    val draft: ItemDraft,
    val saving: Boolean = false,
) {
    val dirty: Boolean get() = draft.result != saved.result

    /** Whether it can be applied: only a name the item has no name of its own can be empty. */
    val valid: Boolean
        get() = draft.name.isNotBlank() || draft.name.trim() == original.name.trim()
}

/**
 * Edits what the active user keeps of their own about the song, album or artist in the saved state
 * (an id and its [Origin]): title or name, artists and picture. Each is an override of the
 * item's own value, so one that is changed back to the original is no override anymore. The
 * changes stay in [state] until [apply] stores them. A remote item that isn't stored yet is
 * fetched when the screen opens and stored, into the library, by [apply] and not before.
 * A picture from the device is copied into the profile's folder first, see [PickedCovers].
 *
 * A subclass calls [begin] from its `init`.
 */
abstract class EditItemViewModel(
    savedStateHandle: SavedStateHandle,
    private val pictures: PickedCovers,
    /** `song`, `album` or `artist`, names the picture files. */
    private val kind: String,
) : ViewModel() {

    protected val itemId: String = checkNotNull(savedStateHandle[EDIT_ITEM_ID]) {
        "EDIT_ITEM_ID is missing from the saved state"
    }

    protected val origin: Origin = Origin.valueOf(
        checkNotNull(savedStateHandle[EDIT_ITEM_ORIGIN]) {
            "EDIT_ITEM_ORIGIN is missing from the saved state"
        }
    )

    /** Whether the item has artists to edit. */
    abstract val hasArtists: Boolean

    /** Id in the media index when this is a song on the device, for the picture it has there. */
    open val localSongId: Long? get() = null

    private val _state = MutableStateFlow<EditItemState?>(null)

    /** `null` while the item loads, or when it couldn't be. */
    val state: StateFlow<EditItemState?> = _state.asStateFlow()

    private val _failed = MutableStateFlow(false)

    /** Whether the item couldn't be found or fetched. */
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    /**
     * The values everyone sees and the user's own, `null` when the item can't be found, from the
     * database or its source.
     */
    protected abstract suspend fun load(): Pair<ItemDraft, ItemDraft>?

    /**
     * Stores the item when it isn't stored yet, then what the user keeps of their own about it.
     * A `null` value is no override of its own: the original shows.
     */
    protected abstract suspend fun store(name: String?, artists: List<String>?, thumbnail: Uri?)

    protected fun begin() {
        viewModelScope.launch {
            val loaded = try {
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't load $kind $itemId" }
                null
            }
            if (loaded == null)
                _failed.value = true
            else
                _state.value = EditItemState(loaded.first, loaded.second, loaded.second)
        }
    }

    /** Changes the draft. */
    fun edit(change: (ItemDraft) -> ItemDraft) {
        _state.update { if (it == null || it.saving) it else it.copy(draft = change(it.draft)) }
    }

    /** Throws the changes away, back to what is stored. */
    fun reset() {
        _state.update { if (it == null || it.saving) it else it.copy(draft = it.saved) }
    }

    /** Stores the draft, [onDone] tells whether it worked. The draft stays as it is on failure. */
    fun apply(onDone: (Boolean) -> Unit) {
        val current = _state.value ?: return
        if (current.saving || !current.valid) return

        _state.value = current.copy(saving = true)
        viewModelScope.launch {
            val stored = try {
                write(current)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't save $kind $itemId" }
                null
            }
            _state.update {
                when {
                    it == null -> null
                    stored != null -> it.copy(saved = stored, draft = stored, saving = false)
                    else -> it.copy(saving = false)
                }
            }
            onDone(stored != null)
        }
    }

    /** @return the draft as it is stored now */
    private suspend fun write(state: EditItemState): ItemDraft {
        val draft = state.draft
        val original = state.original
        val owner = PickedCovers.ownerOf(kind, origin, itemId)

        // A picture from the device is copied, one already stored stays as it is
        val picture = pictures.keep(owner, draft.thumbnail)
        val name = draft.name.trim()
        try {
            store(
                name = name.takeIf { it != original.name.trim() },
                artists = draft.artistList.takeIf { hasArtists && it != original.artistList },
                thumbnail = picture.takeIf { it != original.thumbnail },
            )
        } catch (e: Exception) {
            if (picture != draft.thumbnail) pictures.discard(owner, picture, null)
            throw e
        }
        // The earlier copy has no use once the item points elsewhere
        pictures.discard(owner, state.saved.thumbnail, picture)

        return draft.copy(
            name = name,
            artists = draft.artistList.joinToString("\n"),
            thumbnail = picture,
        )
    }
}

/** The values of an item as a draft; `null` ones are empty. */
private fun draftOf(name: String?, artists: List<String>?, thumbnail: Uri?) =
    ItemDraft(name.orEmpty(), artists.orEmpty().joinToString("\n"), thumbnail)

class EditSongViewModel(
    savedStateHandle: SavedStateHandle,
    pictures: PickedCovers,
    private val songs: SongRepository,
    private val userSongs: UserSongRepository,
    private val songStore: SongStore,
    private val youTube: YouTubeProvider,
    private val openSubsonic: OpenSubsonicProvider,
) : EditItemViewModel(savedStateHandle, pictures, "song") {

    override val hasArtists = true

    override val localSongId: Long? get() = itemId.toLongOrNull()?.takeIf { origin == Origin.LOCAL }

    init {
        begin()
    }

    override suspend fun load(): Pair<ItemDraft, ItemDraft>? {
        val shared = songs.find(itemId).first()
        val original = if (shared != null) {
            val artists = songs.artistsOf(itemId).first().mapNotNull { it.name }
            draftOf(shared.name, artists, shared.thumbnail)
        } else {
            // Not stored, so it is fetched, and stored only when applied
            val remote = songDetails(origin, itemId, youTube, openSubsonic) ?: return null
            draftOf(remote.song.name, remote.artists.mapNotNull { it.name }, remote.song.thumbnail)
        }
        val own = userSongs.own(itemId)
        return original to draftOf(
            own?.name ?: original.name,
            own?.artists ?: original.artistList,
            own?.thumbnail ?: original.thumbnail,
        )
    }

    override suspend fun store(name: String?, artists: List<String>?, thumbnail: Uri?) {
        songStore.store(itemId, origin)
        userSongs.update(itemId) { it.copy(name = name, artists = artists, thumbnail = thumbnail) }
    }
}

class EditAlbumViewModel(
    savedStateHandle: SavedStateHandle,
    pictures: PickedCovers,
    private val albums: AlbumRepository,
    private val userAlbums: UserAlbumRepository,
    private val providers: Providers,
) : EditItemViewModel(savedStateHandle, pictures, "album") {

    override val hasArtists = true

    /** The album as its source has it, `null` when it is stored already. */
    private var fetched: Album? = null

    init {
        begin()
    }

    override suspend fun load(): Pair<ItemDraft, ItemDraft>? {
        val shared = albums.find(itemId).first()
            ?: providers[origin].album(itemId)?.also { fetched = it }
            ?: return null
        val original = draftOf(shared.name, shared.artists, shared.thumbnail)
        val own = userAlbums.own(itemId)
        return original to draftOf(
            own?.name ?: original.name,
            own?.artists ?: original.artistList,
            own?.thumbnail ?: original.thumbnail,
        )
    }

    override suspend fun store(name: String?, artists: List<String>?, thumbnail: Uri?) {
        fetched?.let { if (albums.find(itemId).first() == null) albums.save(it) }
        userAlbums.update(itemId) { it.copy(name = name, artists = artists, thumbnail = thumbnail) }
    }
}

class EditArtistViewModel(
    savedStateHandle: SavedStateHandle,
    pictures: PickedCovers,
    private val artistRepository: ArtistRepository,
    private val userArtists: UserArtistRepository,
    private val providers: Providers,
) : EditItemViewModel(savedStateHandle, pictures, "artist") {

    override val hasArtists = false

    /** The artist as its source has them, `null` when they are stored already. */
    private var fetched: Artist? = null

    init {
        begin()
    }

    override suspend fun load(): Pair<ItemDraft, ItemDraft>? {
        val shared = artistRepository.find(itemId).first()
            ?: providers[origin].artist(itemId)?.also { fetched = it }
            ?: return null
        val original = draftOf(shared.name, null, shared.thumbnail)
        val own = userArtists.own(itemId)
        return original to draftOf(
            own?.name ?: original.name,
            null,
            own?.thumbnail ?: original.thumbnail
        )
    }

    override suspend fun store(name: String?, artists: List<String>?, thumbnail: Uri?) {
        fetched?.let {
            if (artistRepository.find(itemId).first() == null) artistRepository.save(it)
        }
        userArtists.update(itemId) { it.copy(name = name, thumbnail = thumbnail) }
    }
}
