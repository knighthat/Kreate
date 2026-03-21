package app.kreate.android.service.download

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import app.kreate.database.models.Format
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.java.KoinJavaComponent.inject


@OptIn(UnstableApi::class)
class CacheStateImpl(
    private val cache: Cache,
    private val downloadCache: Cache
) : CacheState, DownloadManager.Listener {

    private val logger = Logger.withTag("CacheState")
    private val lock = Mutex()
    private val _downloaded = MutableStateFlow(emptyMap<String, Int>())

    override val downloaded: StateFlow<Map<String, Int>> = _downloaded.asStateFlow()

    override fun isDownloaded( songId: String ) =
        downloaded.value.any { it.key == songId && it.value == Download.STATE_COMPLETED }

    override fun isDownloadedState(songId: String): Flow<Boolean> =
        downloaded.map { it[songId] == Download.STATE_COMPLETED }

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    override fun stateOf( songId: String ): Flow<CacheState.State> =
        downloaded.flatMapLatest {
            val state = it[songId]

            when {
                state == Download.STATE_COMPLETED -> flowOf(CacheState.State.Downloaded)

                state == Download.STATE_DOWNLOADING -> flowOf(CacheState.State.Downloading)

                cache.keys.contains( songId ) ->
                    Database.formatTable
                            .findBySongId( songId )
                            .map { format ->
                                val defaultLength = C.LENGTH_UNSET.toLong()
                                val contentLength = format?.contentLength ?: defaultLength
                                val amount = cache.getCachedBytes( songId, 0, defaultLength )

                                CacheState.State.Cached(amount, format, amount == contentLength)
                            }

                else -> flowOf(CacheState.State.Unknown)
            }
        }

    override suspend fun sync() {
        logger.v { "Syncing download state..." }

        val songIds = downloadCache.keys.toList()
        val formats = Database.formatTable
            .findBySongIds( songIds )
            .firstOrNull()
            ?.filter { it.contentLength != null && it.contentLength > 0 }
            .orEmpty()

        /**
         * Check and delete downloaded chunks that aren't
         * registered in [Format] table.
         */
        if( formats.size < songIds.size ) {
            val formatIds = formats.map(Format::songId)
            val missingIds = songIds.filterNot { it in formatIds }

            logger.w { "Missing ${missingIds.size} songs from format. Deleting unknown cache..." }

            val dlHelper: DownloadHelper by inject(DownloadHelper::class.java)
            dlHelper.remove( *missingIds.toTypedArray() )
        }

        lock.withLock {
            val downloaded = downloaded.value.toMutableMap()
            formats.forEach { (id, _, _, _, length) ->
                if( downloadCache.isCached(id, 0, length!!) )
                    downloaded[id] = Download.STATE_COMPLETED
            }
            _downloaded.update { downloaded.toMap() }
        }
    }

    /*
     *      DownloadManager listener
     */

    override fun onDownloadChanged(
        downloadManager: DownloadManager,
        download: Download,
        finalException: Exception?
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            lock.withLock {
                val downloaded = downloaded.value.toMutableMap()
                downloaded[download.request.id] = download.state

                _downloaded.update { downloaded.toMap() }
            }
        }
    }

    override fun onDownloadRemoved( downloadManager: DownloadManager, download: Download ) {
        CoroutineScope(Dispatchers.Default).launch {
            lock.withLock {
                val downloaded = downloaded.value.toMutableMap()
                val requestId = download.request.id
                downloaded.remove( requestId )

                _downloaded.update { downloaded.toMap() }
            }
        }
    }
}