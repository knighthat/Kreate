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

package me.knighthat.kreate3.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Parcel
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceException
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.notification.DownloadNotifications
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.playback.QueuedSong
import me.knighthat.kreate3.playback.SongCaches
import me.knighthat.kreate3.playback.playbackReasonRes
import me.knighthat.kreate3.playback.streamQuality
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.ui.component.origin
import kotlin.time.Clock

/** Where a song of a server or YouTube is kept on the device, see [SongDownloads.songs]. */
enum class SongStorage {
    /** A whole copy is in the cache, which may drop it. */
    CACHED,

    /** Waiting to be downloaded, or being downloaded. */
    DOWNLOADING,

    /** Paused by the user, see [SongDownloads.resume]. */
    PAUSED,

    /** Kept until the user deletes it. */
    DOWNLOADED,
}

/** Where an unfinished download stands, see [UnfinishedDownload]. */
enum class DownloadStage {
    /** Being downloaded now. */
    DOWNLOADING,

    /** Waiting for its turn, or for what the downloads wait for ([DownloadsOverview.waitingFor]). */
    QUEUED,

    /** Paused by the user, see [SongDownloads.resume]. */
    PAUSED,

    /** Gave up after its retries, see [SongDownloads.retry]. */
    FAILED,
}

/** What every download waits for, see [DownloadsOverview.waitingFor]. */
enum class DownloadWait {
    /** [Preferences.DOWNLOAD_WIFI_ONLY] and no Wi-Fi. */
    WIFI,

    /** [SongDownloads.pausedForBattery]. */
    BATTERY,
}

/**
 * A download of the active user that isn't whole yet.
 *
 * @property item the song as it was given to [SongDownloads.download]
 * @property percent how much of it is there while it's downloaded or paused, `null` when not
 * known
 * @property bytes how much of it is there
 * @property totalBytes its size, `null` when not known
 * @property failure why it failed, `null` when it didn't or that isn't known (the reason isn't
 * kept past the app's run)
 */
data class UnfinishedDownload(
    val item: MediaItem,
    val stage: DownloadStage,
    val percent: Int? = null,
    val bytes: Long = 0,
    val totalBytes: Long? = null,
    @param:StringRes val failure: Int? = null,
)

/**
 * The file of a whole download.
 *
 * @property bytes its size
 * @property quality the quality it is in, the best any user asked for
 */
data class DownloadedFile(val bytes: Long, val quality: StreamQuality?)

/**
 * The downloads of the active user right now, see [SongDownloads.overview].
 *
 * @property files the whole downloads by song id
 * @property unfinished the others, in the order they were asked for, failed ones last: a row
 * doesn't move when it's paused or resumed
 * @property waitingFor what every download waits for, `null` for nothing
 * @property userBytes room the active user's downloads take, a file shared with other users in
 * full: leaving it frees nothing while they keep it
 * @property allBytes room the downloads of every user take, what [Preferences.MEDIA3_DOWNLOAD_SIZE]
 * limits
 */
data class DownloadsOverview(
    val files: Map<String, DownloadedFile>,
    val unfinished: List<UnfinishedDownload>,
    val waitingFor: DownloadWait?,
    val userBytes: Long,
    val allBytes: Long,
)

/**
 * What a download of a song would be, for the user to decide on.
 *
 * @property cached the best quality the cache has the whole song in, `null` for none
 * @property quality the quality the song would be downloaded in: [cached] when it is as good as
 * the song is set to now, which moves the copy without streaming, else the one it is set to
 */
data class DownloadPlan(val cached: StreamQuality?, val quality: StreamQuality) {

    /** Whether the cached copy is moved to the downloads, no new one is streamed. */
    val movesCache: Boolean
        get() = cached == quality
}

/**
 * The songs of servers and YouTube the active user downloads, and those the cache has whole, as
 * [songs]. What a downloaded song needs offline is kept by [stored]. Downloads run in
 * [SongDownloadService] through [manager], into
 * [SongCaches.downloads] under the copy's key ([QueuedSong.cacheKey]); a completed one drops the
 * song's copies from the cache, the download replaces them.
 *
 * Every user downloads on their own (`user_song.downloaded_at`), a song's file is shared: one per
 * song, in the best quality any of them asked for, kept while one of them still wants it. A user
 * downloading a song another one already has gets it at once, or a better copy when theirs is set
 * higher. Leaving a download, from the menu or its notification, is the active user's alone.
 *
 * Downloads don't wait for a network unless [Preferences.DOWNLOAD_WIFI_ONLY] asks for Wi-Fi: a
 * cached song moves without one, any other fails offline. With
 * [Preferences.DOWNLOAD_ON_LOW_BATTERY] off, they pause while Android says the battery is low
 * ([pausedForBattery]). The user pauses one song with a stop reason ([STOP_REASON_PAUSED]).
 *
 * @param scope the app's global scope, the downloads outlive any screen
 */
@OptIn(UnstableApi::class)
class SongDownloads(
    private val context: Context,
    internal val manager: DownloadManager,
    private val caches: SongCaches,
    private val youTube: YouTubeProvider,
    private val notifications: DownloadNotifications,
    private val stored: DownloadedSongStore,
    private val userSongs: UserSongRepository,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
) {

    /** The state of each download by its song's key ([key]), as last heard from [manager]. */
    private val downloads = mutableMapOf<String, Int>()

    /** The best quality the cache keeps each song whole in, by its key ([key]). */
    private val cached = mutableMapOf<String, StreamQuality>()

    /** The ids of the songs the active user downloaded. */
    private var mine = emptySet<String>()

    /** Why each download failed, by its id, for this run of the app. */
    private val failures = mutableMapOf<String, Int>()

    /** The songs of the downloads, as [DownloadRequest.songItem] gives them, by download id. */
    private val items = mutableMapOf<String, MediaItem>()

    private val _songs = MutableStateFlow<Map<String, SongStorage>>(emptyMap())

    /**
     * Counts every change of a download or the cache, for [overview]: [songs] stays the same when
     * a download the user already left is removed.
     */
    private val revision = MutableStateFlow(0L)

    /**
     * Where each song is kept for the active user, by its key ([key]), a song kept nowhere is
     * missing. A download wins over the cache; one only other users made doesn't show.
     */
    val songs: StateFlow<Map<String, SongStorage>> = _songs.asStateFlow()

    /** Whether the downloads wait for the battery, see [Preferences.DOWNLOAD_ON_LOW_BATTERY]. */
    val pausedForBattery: StateFlow<Boolean> =
        combine(Preferences.DOWNLOAD_ON_LOW_BATTERY.state, context.batteryLow()) { allowed, low ->
            low && !allowed
        }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        manager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) {
                setDownload(download.request.id, download.state)
                when (download.state) {
                    // Tried again, like from its Retry button
                    Download.STATE_QUEUED -> notifications.retrying(download.request.id)

                    Download.STATE_COMPLETED -> {
                        notifications.downloaded(download)
                        download.request.songItem?.let { scope.launch { stored.completed(it) } }
                        val quality = download.request.customCacheKey
                            ?.let(QueuedSong::ofCacheKey)?.quality
                        // The download replaces the copies of the cache, and its worse copies
                        QueuedSong.ofCacheKey(download.request.id)?.let {
                            scope.launch(Dispatchers.IO) {
                                caches.dropAll(it)
                                if (quality != null) caches.dropDownloads(it, kept = quality)
                            }
                        }
                    }

                    Download.STATE_FAILED -> {
                        Log.w(TAG, "Couldn't download ${download.request.id}", finalException)
                        finalException?.failureRes()?.let {
                            synchronized(this@SongDownloads) { failures[download.request.id] = it }
                        }
                        notifications.failed(download)
                    }
                }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                setDownload(download.request.id, null)
                notifications.removed(download.request.id)
                download.request.songItem?.let {
                    scope.launch { stored.removed(it, downloadedItems()) }
                }
                QueuedSong.ofCacheKey(download.request.id)?.let {
                    scope.launch(Dispatchers.IO) { caches.dropDownloads(it) }
                }
            }

            override fun onDownloadsPausedChanged(
                downloadManager: DownloadManager,
                downloadsPaused: Boolean,
            ) {
                // The service stops while nothing runs, its songs show that they wait
                if (downloadsPaused)
                    notifications.downloading(downloadManager.currentDownloads, 0, true)
            }
        })
        // The settings apply as they change
        scope.launch(Dispatchers.Main) {
            Preferences.DOWNLOAD_PARALLEL_COUNT.state.collect {
                manager.maxParallelDownloads = it.coerceAtLeast(1)
            }
        }
        scope.launch(Dispatchers.Main) {
            Preferences.DOWNLOAD_RETRY_COUNT.state.collect {
                manager.minRetryCount = it.coerceAtLeast(0)
            }
        }
        scope.launch(Dispatchers.Main) {
            Preferences.DOWNLOAD_WIFI_ONLY.state.collect {
                manager.requirements = Requirements(if (it) Requirements.NETWORK_UNMETERED else 0)
            }
        }
        scope.launch(Dispatchers.Main) {
            var wasPaused = false
            pausedForBattery.collect { paused ->
                applyBatteryPause()
                // Back in the foreground, when Android allows it from here
                if (wasPaused && !paused && manager.currentDownloads.any { isRunning(it.state) })
                    resumeService()
                wasPaused = paused
            }
        }
        // What the active user downloaded, another one's when the profile changes
        scope.launch {
            userSongs.downloadedIds().collect { ids ->
                synchronized(this@SongDownloads) {
                    mine = ids
                    publish()
                }
            }
        }
        // The cache changes as songs play
        scope.launch {
            caches.changed.collect { song ->
                val quality = withContext(Dispatchers.IO) { caches.cached(song) }
                setCached(key(song), quality)
            }
        }
        scope.launch(Dispatchers.IO) {
            val songs = caches.cachedSongs()
            val known = buildMap {
                manager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext())
                        put(cursor.download.request.id, cursor.download.state)
                }
            }
            synchronized(this@SongDownloads) {
                songs.forEach { (song, quality) -> cached.putIfAbsent(key(song), quality) }
                known.forEach { (id, state) -> downloads.putIfAbsent(id, state) }
                publish()
            }
            sweep(known.keys)
            // Downloads left unfinished by the last run go on
            if (known.values.any(::isRunning)) withContext(Dispatchers.Main) { resumeService() }
        }
    }

    /**
     * Pauses every download while [pausedForBattery], resumes them otherwise. The download
     * service resumes them when it starts, it calls this again after. Main thread only.
     */
    internal fun applyBatteryPause() {
        if (pausedForBattery.value) manager.pauseDownloads() else manager.resumeDownloads()
    }

    /**
     * The active user no longer wants the download with [id], their cover of it goes. Its file
     * goes once no user wants it, a download still running stops then.
     */
    internal fun leave(id: String) = scope.launch {
        val song = QueuedSong.ofCacheKey(id) ?: return@launch
        try {
            val cover = item(id)?.let(stored::coverOf)
            userSongs.forgetDownload(song.id, cover)
            if (song.id in userSongs.downloadedByAnyone()) return@launch
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't forget the download of $id", e)
        }
        withContext(Dispatchers.Main) {
            DownloadService.sendRemoveDownload(context, SongDownloadService::class.java, id, false)
        }
    }

    /**
     * Downloads nobody wants anymore go, like those of a deleted profile; users' downloads with
     * no file anymore are forgotten. [known] are the downloads there are.
     */
    private suspend fun sweep(known: Set<String>) = try {
        val wanted = userSongs.downloadedByAnyone()
        val songIds = known.mapNotNull { QueuedSong.ofCacheKey(it)?.id }.toSet()
        withContext(Dispatchers.Main) {
            known.filter { QueuedSong.ofCacheKey(it)?.id !in wanted }
                .forEach(manager::removeDownload)
        }
        (wanted - songIds).forEach { userSongs.forgetDownload(it, null, everyUser = true) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't sweep the downloads", e)
    }

    private fun resumeService() {
        try {
            DownloadService.sendResumeDownloads(context, SongDownloadService::class.java, true)
        } catch (e: IllegalStateException) {
            // Not allowed to start from the background, they go on with the next download
            Log.w(TAG, "Couldn't resume the downloads", e)
        }
    }

    /**
     * What downloading the song of [origin] with [id] would do: move its whole cached copy when
     * it is as good as the song is set to now, else stream it in that quality.
     */
    suspend fun plan(origin: Origin, id: String): DownloadPlan {
        val song = QueuedSong(origin, id)
        val quality = context.streamQuality(origin, youTube)
        val cached = withContext(Dispatchers.IO) { caches.cached(song) }
        return DownloadPlan(cached, cached?.takeIf { it >= quality } ?: quality)
    }

    /**
     * The active user downloads the song of [item], see [plan] for the quality. The song is
     * stored first, the user's download is kept with it. A file another user's download made is
     * shared at once when it is as good, else a better one replaces it once whole. The item is
     * kept with the download, for its notifications to show and play it ([songItem]). A new file
     * is refused with a message once the downloads hold [Preferences.MEDIA3_DOWNLOAD_SIZE].
     */
    fun download(item: MediaItem) = scope.launch {
        val origin = item.origin ?: return@launch
        val id = item.mediaId
        val song = QueuedSong(origin, id)
        val wanted = plan(origin, id).quality
        val existing = withContext(Dispatchers.IO) { manager.downloadIndex.getDownload(key(song)) }

        // The user's download needs the song stored
        if (!stored.store(item)) {
            notifier.post(context.getString(R.string.error_download_not_saved))
            return@launch
        }
        userSongs.update(id) { it.copy(downloadedAt = it.downloadedAt ?: Clock.System.now()) }

        val quality = existing?.request?.customCacheKey?.let(QueuedSong::ofCacheKey)?.quality
        val shared = existing != null && quality != null && quality >= wanted
                && existing.state != Download.STATE_FAILED
                && existing.state != Download.STATE_REMOVING
        if (shared) {
            // Their cover and lyrics, the file is there
            if (existing.state == Download.STATE_COMPLETED) stored.completed(item)
            return@launch
        }

        val full = withContext(Dispatchers.IO) {
            caches.downloads.cacheSpace >= Preferences.MEDIA3_DOWNLOAD_SIZE.value
        }
        if (full) {
            userSongs.forgetDownload(id, null)
            notifier.post(context.getString(R.string.error_downloads_full))
            return@launch
        }

        val request = DownloadRequest.Builder(key(song), song.uri)
            .setCustomCacheKey(song.copy(quality = wanted).cacheKey)
            .setData(item.toDownloadData())
            .build()
        setDownload(request.id, Download.STATE_QUEUED)
        withContext(Dispatchers.Main) {
            notifications.removed(request.id)
            DownloadService.sendAddDownload(context, SongDownloadService::class.java, request, true)
        }
    }

    /** Resumes the paused download of the song of [origin] with [id], for every user. */
    fun resume(origin: Origin, id: String) = scope.launch(Dispatchers.Main) {
        DownloadService.sendSetStopReason(
            context,
            SongDownloadService::class.java,
            key(QueuedSong(origin, id)),
            Download.STOP_REASON_NONE,
            true,
        )
    }

    /**
     * The active user deletes their download of the song of [origin] with [id], or stops it while
     * it runs, see [leave].
     */
    fun delete(origin: Origin, id: String) = leave(key(QueuedSong(origin, id)))

    /** The song downloaded as [id], as it was given to [download]; `null` when it isn't known. */
    suspend fun item(id: String): MediaItem? = withContext(Dispatchers.IO) {
        manager.downloadIndex.getDownload(id)?.request?.songItem
    }

    /** The songs downloaded whole, as they were given to [download]. */
    private suspend fun downloadedItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        buildList {
            manager.downloadIndex.getDownloads(Download.STATE_COMPLETED).use { cursor ->
                while (cursor.moveToNext()) cursor.download.request.songItem?.let(::add)
            }
        }
    }

    /** Pauses the download of the song of [origin] with [id], for every user. */
    fun pause(origin: Origin, id: String) = scope.launch(Dispatchers.Main) {
        DownloadService.sendSetStopReason(
            context,
            SongDownloadService::class.java,
            key(QueuedSong(origin, id)),
            STOP_REASON_PAUSED,
            false,
        )
    }

    /** Downloads the song of [origin] with [id] again after it failed, once more with its retries. */
    fun retry(origin: Origin, id: String) = scope.launch {
        val request = withContext(Dispatchers.IO) {
            manager.downloadIndex.getDownload(key(QueuedSong(origin, id)))?.request
        } ?: return@launch
        withContext(Dispatchers.Main) {
            DownloadService.sendAddDownload(context, SongDownloadService::class.java, request, true)
        }
    }

    /**
     * The active user's downloads, again about every second while one runs or waits, and when
     * one changes.
     */
    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    fun overview(): Flow<DownloadsOverview> =
        combine(revision, pausedForBattery, ::Pair).transformLatest {
            while (true) {
                val overview = overviewNow()
                emit(overview)
                val waits = overview.unfinished.any {
                    it.stage == DownloadStage.DOWNLOADING || it.stage == DownloadStage.QUEUED
                }
                if (!waits) break
                delay(OVERVIEW_INTERVAL)
            }
        }

    private suspend fun overviewNow(): DownloadsOverview {
        // The manager is the main thread's, the running downloads' progress is only there
        val (current, notMet) = withContext(Dispatchers.Main) {
            manager.currentDownloads to manager.notMetRequirements
        }
        return withContext(Dispatchers.IO) {
            val finished = buildList {
                manager.downloadIndex
                    .getDownloads(Download.STATE_COMPLETED, Download.STATE_FAILED)
                    .use { cursor -> while (cursor.moveToNext()) add(cursor.download) }
            }
            val ours = synchronized(this@SongDownloads) { mine }
            // Those being removed are going, their room with them
            val everyone = (current + finished).filter { it.state != Download.STATE_REMOVING }
            val downloads = everyone.filter { QueuedSong.ofCacheKey(it.request.id)?.id in ours }

            val files = downloads.filter { it.state == Download.STATE_COMPLETED }
                .mapNotNull { download ->
                    val song = QueuedSong.ofCacheKey(download.request.id) ?: return@mapNotNull null
                    val quality = download.request.customCacheKey
                        ?.let(QueuedSong::ofCacheKey)?.quality
                    song.id to DownloadedFile(download.bytesDownloaded, quality)
                }.toMap()
            val unfinished = downloads.mapNotNull { download ->
                val stage = when (download.state) {
                    Download.STATE_DOWNLOADING -> DownloadStage.DOWNLOADING
                    Download.STATE_QUEUED, Download.STATE_RESTARTING -> DownloadStage.QUEUED
                    Download.STATE_STOPPED -> DownloadStage.PAUSED
                    Download.STATE_FAILED -> DownloadStage.FAILED
                    else -> return@mapNotNull null
                }
                val percent = download.percentDownloaded
                    .takeIf {
                        (stage == DownloadStage.DOWNLOADING || stage == DownloadStage.PAUSED)
                                && it != C.PERCENTAGE_UNSET.toFloat()
                    }
                    ?.toInt()
                val item = itemOf(download) ?: return@mapNotNull null
                UnfinishedDownload(
                    item = item,
                    stage = stage,
                    percent = percent,
                    bytes = download.bytesDownloaded,
                    totalBytes = download.contentLength.takeIf { it != C.LENGTH_UNSET.toLong() },
                    failure = if (stage == DownloadStage.FAILED)
                        synchronized(this@SongDownloads) { failures[download.request.id] }
                    else null,
                )
            }
            synchronized(this@SongDownloads) {
                val ids = downloads.map { it.request.id }.toSet()
                items.keys.retainAll(ids)
                failures.keys.retainAll(downloads.filter { it.state == Download.STATE_FAILED }
                    .map { it.request.id }.toSet())
            }

            // The cache counts a part once it's written whole, a running download's only later
            val allBytes = maxOf(caches.downloads.cacheSpace, everyone.sumOf { it.bytesDownloaded })
            DownloadsOverview(
                files = files,
                unfinished = unfinished,
                waitingFor = when {
                    pausedForBattery.value -> DownloadWait.BATTERY
                    notMet and Requirements.NETWORK_UNMETERED != 0 -> DownloadWait.WIFI
                    else -> null
                },
                userBytes = downloads.sumOf { it.bytesDownloaded }.coerceAtMost(allBytes),
                allBytes = allBytes,
            )
        }
    }

    /** The song of [download], read from its request once. */
    private fun itemOf(download: Download): MediaItem? {
        val id = download.request.id
        synchronized(this) { items[id] }?.let { return it }
        val item = download.request.songItem ?: return null
        synchronized(this) { items[id] = item }
        return item
    }

    /** Drops every copy the cache keeps of the song of [origin] with [id]. */
    fun removeCached(origin: Origin, id: String) = scope.launch(Dispatchers.IO) {
        caches.dropAll(QueuedSong(origin, id))
    }

    private fun setDownload(id: String, state: Int?) = synchronized(this) {
        if (state == null) downloads.remove(id) else downloads[id] = state
        publish()
    }

    private fun setCached(key: String, quality: StreamQuality?) = synchronized(this) {
        if (quality == null) cached.remove(key) else cached[key] = quality
        publish()
    }

    private fun publish() {
        val ours = downloads.filterKeys { QueuedSong.ofCacheKey(it)?.id in mine }
        val stored = cached.mapValues { SongStorage.CACHED } + ours.mapNotNull { (id, state) ->
            when {
                state == Download.STATE_COMPLETED -> id to SongStorage.DOWNLOADED
                isRunning(state) -> id to SongStorage.DOWNLOADING
                state == Download.STATE_STOPPED -> id to SongStorage.PAUSED
                else -> null
            }
        }
        _songs.update { stored }
        revision.update { it + 1 }
    }

    companion object {
        private const val TAG = "SongDownloads"

        /** How often [overview] looks again while a download runs or waits. */
        private const val OVERVIEW_INTERVAL = 1000L

        /** The stop reason of a download the user paused. */
        const val STOP_REASON_PAUSED = 1

        /** The key of a song in [songs], and the id of its download: its address. */
        fun key(song: QueuedSong): String = song.song.uri.toString()

        /** The key of the song of [origin] with [id] in [songs]. */
        fun key(origin: Origin, id: String): String = key(QueuedSong(origin, id))

        internal fun isRunning(state: Int) = state == Download.STATE_QUEUED
                || state == Download.STATE_DOWNLOADING
                || state == Download.STATE_RESTARTING
    }
}

/**
 * Why a download failed, in words for the user, from the reason a data source gave on the way;
 * `null` when none said more than that it failed.
 */
@StringRes
private fun Exception.failureRes(): Int? =
    generateSequence<Throwable>(this) { it.cause }
        .firstNotNullOfOrNull { (it as? DataSourceException)?.reason }
        ?.let(::playbackReasonRes)
        ?.takeIf { it != R.string.error_playback_unknown }

/** The song as a download keeps it, without its address, which the player adds. */
@OptIn(UnstableApi::class)
internal fun MediaItem.toDownloadData(): ByteArray {
    val parcel = Parcel.obtain()
    return try {
        // The version it was written with, for a later Media3 to read it
        parcel.writeInt(MediaLibraryInfo.INTERFACE_VERSION)
        parcel.writeBundle(toBundle(MediaLibraryInfo.INTERFACE_VERSION))
        parcel.marshall()
    } finally {
        parcel.recycle()
    }
}

/** The song downloaded, `null` when the request doesn't hold one. */
@OptIn(UnstableApi::class)
internal val DownloadRequest.songItem: MediaItem?
    get() {
        if (data.isEmpty()) return null
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(data, 0, data.size)
            parcel.setDataPosition(0)
            val version = parcel.readInt()
            parcel.readBundle(MediaItem::class.java.classLoader)
                ?.let { MediaItem.fromBundle(it, version) }
        } catch (e: RuntimeException) {
            Log.w("SongDownloads", "Couldn't read the song of download $id", e)
            null
        } finally {
            parcel.recycle()
        }
    }

/**
 * Whether Android says the battery is low, as it changes: from its low and okay broadcasts, at
 * first from the battery's last state.
 */
private fun Context.batteryLow(): Flow<Boolean> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(intent.action == Intent.ACTION_BATTERY_LOW)
        }
    }
    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_BATTERY_LOW)
        addAction(Intent.ACTION_BATTERY_OKAY)
    }
    // Only Android sends these
    ContextCompat.registerReceiver(
        this@batteryLow,
        receiver,
        filter,
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    // The last state, sticky
    val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    if (battery != null) send(battery.isLow())
    awaitClose { unregisterReceiver(receiver) }
}.distinctUntilChanged()

/** Whether this [Intent.ACTION_BATTERY_CHANGED] says the battery is low. */
private fun Intent.isLow(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        return getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false)
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    // Android's own threshold before it tells
    return !plugged && level >= 0 && scale > 0 && level * 100 / scale <= LOW_BATTERY_PERCENT
}

private const val LOW_BATTERY_PERCENT = 15
