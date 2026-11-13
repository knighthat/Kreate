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

package me.knighthat.kreate3.ui.screen.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.paging.cachedIn
import androidx.paging.map
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate3.auto.AutoLibrary
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.playback.isOnline
import me.knighthat.kreate3.playlist.ImportStep
import me.knighthat.kreate3.playlist.ImportTask
import me.knighthat.kreate3.playlist.ImportingPlaylist
import me.knighthat.kreate3.playlist.PlaylistImporter
import me.knighthat.kreate3.playlist.PlaylistToImport
import me.knighthat.kreate3.playlist.importFailedMessage
import me.knighthat.kreate3.playlist.importMessage
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.livePlaylists
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.ui.navigation.Tab

/**
 * The playlists of the active user, then their playlists on the external sources. Pages are kept while the screen lives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomePlaylistsViewModel internal constructor(
    private val playlistRepository: PlaylistRepository,
    private val library: AutoLibrary,
    openSubsonic: OpenSubsonicProvider,
    youTube: YouTubeProvider,
    private val context: Context,
    private val importer: PlaylistImporter,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
) : ViewModel() {

    private val remote = LiveItems(viewModelScope) { livePlaylists(openSubsonic, youTube) }

    val filter = OriginFilter(
        viewModelScope,
        Preferences.HOME_PLAYLISTS_ORIGINS,
        stored = playlistRepository.origins(),
        liveItems = remote.items,
    )

    private val _monthlyOnly = MutableStateFlow(false)

    /**
     * Whether the monthly playlists can be picked alone: they are the app's own, only while the
     * user has some and the origin filter shows them.
     */
    val offersMonthly: StateFlow<Boolean> =
        combine(playlistRepository.origins(), filter.selected) { stored, selected ->
            Origin.LOCAL in stored && (selected.isEmpty() || Origin.LOCAL in selected)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Whether the tab shows only the monthly playlists, off at first. It lives with the view model,
     * as long as the tab is in the back stack; it is kept while [offersMonthly] is off, and applies
     * again once it is on.
     */
    val monthlyOnly: StateFlow<Boolean> = _monthlyOnly.asStateFlow()

    // The filter as it applies
    private val monthly = combine(_monthlyOnly, offersMonthly) { only, offered -> only && offered }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val playlists = combine(filter.selected, monthly, ::Pair)
        .flatMapLatest { (origins, only) ->
            playlistRepository.all(origins = origins, monthlyOnly = only)
        }
        .map { page -> page.map { it.toMediaItem() } }
        .cachedIn(viewModelScope)

    /** The playlists of the server and of YouTube, fetched live and never stored. */
    val live: StateFlow<List<MediaItem>> =
        // None of them is monthly
        combine(filter.live, monthly) { list, only -> if (only) emptyList() else list }
            .stateIn(viewModelScope, SharingStarted.Eagerly, filter.live.value)

    /**
     * The playlists of files being imported that aren't made yet, shown above the others whatever
     * the filter.
     */
    val importing: StateFlow<List<ImportingPlaylist>> = importer.importing

    /** Whether the user's pull to refresh is waiting for the items of the external sources. */
    val pulling: StateFlow<Boolean> = remote.pulling

    // Files read while another waited for its answer wait behind it
    private val pendingImports = MutableStateFlow<List<PendingImport>>(emptyList())

    /**
     * A read playlist file waiting for the user to say whether to look its YouTube songs up, the
     * first of those read.
     */
    val pendingImport: StateFlow<PendingImport?> = pendingImports
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Fetches the playlists of the server and of YouTube again, call it when the tab shows. Each
     * adds nothing while it isn't set up.
     */
    fun refresh() = remote.refresh()

    /** Fetches them again because the user pulled the list down. */
    fun pull() = remote.pull()

    /** Turns [monthlyOnly] around. */
    fun toggleMonthlyOnly() = _monthlyOnly.update { !it }

    /** One playlist of the list at random, `null` when the list is empty. */
    suspend fun random(): MediaItem? {
        val origins = filter.selected.value
        val only = monthly.value
        return pickRandom(playlistRepository.count(origins, only), live.value) {
            playlistRepository.random(origins, only)?.toMediaItem()
        }
    }

    /**
     * The songs of one playlist of the list at random, as tapping it plays them; empty when the
     * list is, or the playlist's source can't give them.
     */
    suspend fun randomSongs(): List<MediaItem> {
        val playlist = random() ?: return emptyList()
        return try {
            library.songsOf(Tab.PLAYLISTS, playlist)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't get the songs of ${playlist.mediaId}" }
            emptyList()
        }
    }

    /**
     * Reads the playlist file at [uri]. When it has YouTube songs and the device is online it waits
     * in [pendingImport] for [answerLookUp], otherwise its local playlists are made right away,
     * from the file's values. It runs on the app's scope, leaving the tab doesn't stop it; it shows
     * in [importing] meanwhile, and the user is told how it went.
     */
    fun importFile(uri: Uri) {
        scope.launch {
            // The file picker just closed: the screen may not show messages again yet, and a
            // quick answer (a wrong file, a small import) would go to the system or be dropped
            val task = importer.startImport(uri)
            notifier.awaitScreen()
            val playlists = try {
                importer.readCsv(uri)
            } catch (e: CancellationException) {
                task.close()
                throw e
            } catch (e: Exception) {
                task.close()
                Logger.w(e, TAG) { "Couldn't read the playlist file" }
                notifier.post(importFailedMessage(context.resources, e))
                return@launch
            }
            val youTubeSongs = playlists.asSequence()
                .flatMap { it.songs }
                .filter { it.origin == Origin.YOUTUBE && it.id.isNotEmpty() }
                .distinctBy { it.id }
                .count()
            // Offline there's nothing to look up with, the file goes in as it is
            if (youTubeSongs > 0 && context.isOnline()) {
                task.playlists(playlists.map { it.name })
                task.step(ImportStep.WAITING)
                pendingImports.update { it + PendingImport(playlists, youTubeSongs, task) }
            } else {
                import(playlists, lookUp = false, task)
            }
        }
    }

    /**
     * The answer to [pendingImport]: `true` looks its YouTube songs up again first, `false`
     * imports them as the file has them, `null` drops the import.
     */
    fun answerLookUp(lookUp: Boolean?) {
        val pending = pendingImports.value.firstOrNull() ?: return
        pendingImports.update { it - pending }
        if (lookUp == null)
            pending.task.close()
        else
            scope.launch { import(pending.playlists, lookUp, pending.task) }
    }

    private suspend fun import(
        playlists: List<PlaylistToImport>,
        lookUp: Boolean,
        task: ImportTask,
    ) {
        val message = try {
            importMessage(context.resources, importer.importPlaylists(playlists, lookUp, task))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't import playlists" }
            importFailedMessage(context.resources, e)
        }
        notifier.post(message)
    }

    // Files still waiting for their answer are dropped with the tab: take their placeholders away
    override fun onCleared() {
        pendingImports.getAndUpdate { emptyList() }.forEach { it.task.close() }
    }

    private companion object {
        const val TAG = "HomePlaylists"
    }
}

/**
 * A playlist file read but not imported yet.
 *
 * @property playlists what the file holds
 * @property youTubeSongs how many different YouTube songs it has
 * @property task its placeholders, closed when the import is dropped
 */
class PendingImport(
    val playlists: List<PlaylistToImport>,
    val youTubeSongs: Int,
    val task: ImportTask,
)
