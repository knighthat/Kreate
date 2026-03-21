package app.kreate.android.service.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import app.kreate.android.utils.isLocal
import app.kreate.database.models.Song
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent


@OptIn(UnstableApi::class)
class DownloadHelperImpl(
    private val context: Context,
    private val cache: Cache,
    private val downloadCache: Cache
) : DownloadHelper, KoinComponent {

    private val logger = Logger.withTag("DownloadHelper")
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    /**
     * Attempt to move media with [mediaId] from [cache] to [downloadCache].
     *
     * Only returns `true` if move is successful, `false` otherwise.
     */
    private suspend fun moveFromCache( mediaId: CharSequence ): Boolean = withContext( Dispatchers.IO ) {
        logger.v { "Attempting to move $mediaId from cache to download storage" }

        val mediaId = mediaId.toString()
        val format = Database.formatTable.findBySongId( mediaId ).firstOrNull()

        val contentLength = format?.contentLength ?: 0L
        if( format == null
            || contentLength < 1
            || !cache.isCached(mediaId, 0, contentLength)
        ) {
            logger.i { "$mediaId doesn't exist in cache storage!" }
            return@withContext false
        }

        try {
            val upstreamDataSource = CacheDataSource.Factory()
                .setCache( cache )
                .setUpstreamDataSourceFactory( null )
                .createDataSource()
            val dataspec = DataSpec.Builder()
                .setUri( mediaId )
                .build()
            val cacheWriter = CacheWriter(
                CacheDataSource(downloadCache, upstreamDataSource),
                dataspec,
                null,
                null
            )

            cacheWriter.cache()
            logger.d { "Moved $mediaId successfully from cache" }

            true
        } catch( e: Exception ) {
            logger.w( e ) { "Can't move $mediaId from cache to download storage" }

            false
        }
    }

    private suspend fun isDownloaded( mediaId: CharSequence ): Boolean = withContext( Dispatchers.IO ) {
        if( !downloadCache.keys.contains(mediaId) ) {
            logger.v { "Download storage doesn't contain $mediaId" }
            return@withContext false
        }

        val mediaId = mediaId.toString()
        val format = Database.formatTable.findBySongId( mediaId ).firstOrNull()
        if( format == null || format.contentLength == null || format.contentLength < 1 ) {
            logger.v { "$mediaId's format isn't stored" }
            false
        } else
            downloadCache.isCached( mediaId, 0, format.contentLength )
                         .also {
                             logger.d {
                                 if( it )
                                     "$mediaId is cached by download"
                                 else
                                     "$mediaId is not cached"
                             }
                         }
    }

    private fun toDownloadRequest( mediaItem: MediaItem ): DownloadRequest {
        val (title, artist) = with( mediaItem.mediaMetadata ) {
            title?.toString().orEmpty() to artist?.toString().orEmpty()
        }
        return DownloadRequest.Builder(mediaItem.mediaId, mediaItem.mediaId.toUri())
                              .setCustomCacheKey( mediaItem.mediaId )
                              // Set song's name here so it can be retrieved in [DownloadServiceImpl]
                              .setData( "$artist - $title".toByteArray() )
                              .build()
    }

    private fun toDownloadRequest( song: Song ): DownloadRequest {
        val title = song.cleanTitle()
        val artist =  song.cleanArtistsText()
        return DownloadRequest.Builder(song.id, song.id.toUri())
                              .setCustomCacheKey( song.id )
                              // Set song's name here so it can be retrieved in [DownloadServiceImpl]
                              .setData( "$artist - $title".toByteArray() )
                              .build()
    }

    private fun download( vararg requests: DownloadRequest ) {
        for( req in requests )
            DownloadService.sendAddDownload(
                context,
                DownloadServiceImpl::class.java,
                req,
                false
            )
    }

    override fun downloadMediaItem( mediaItem: MediaItem ) {
        coroutineScope.launch {
            if( mediaItem.isLocal || isDownloaded(mediaItem.mediaId) || moveFromCache(mediaItem.mediaId) )
                return@launch

            logger.v { "Downloading ${mediaItem.mediaMetadata.title} (${mediaItem.mediaId})" }

            Database.asyncTransaction { insertIgnore(mediaItem) }

            val request = toDownloadRequest( mediaItem )
            download( request )
        }
    }

    override fun downloadMediaItems( mediaItems: List<MediaItem> ) {
        coroutineScope.launch {
            val originalSize = mediaItems.size
            // Filter out what to download by removing local songs
            // then attempt to move some of them from cache (if available).
            val mediaItems = mediaItems.filterNot { it.isLocal || isDownloaded(it.mediaId) || moveFromCache(it.mediaId) }

            logger.v { "Downloading ${mediaItems.size} out of $originalSize media items" }

            Database.asyncTransaction {
                for( item in mediaItems )
                    insertIgnore( item )
            }

            val requests = mediaItems.map( ::toDownloadRequest )
            download( *requests.toTypedArray() )
        }
    }

    override fun downloadSong( song: Song ) {
        coroutineScope.launch {
            if( song.isLocal || isDownloaded(song.id) || moveFromCache(song.id) )
                return@launch

            logger.v { "Downloading ${song.cleanTitle()} (${song.id})" }

            Database.asyncTransaction { songTable.insertIgnore(song) }

            val request = toDownloadRequest( song )
            download( request )
        }
    }

    override fun downloadSongs( songs: List<Song> ) {
        coroutineScope.launch {
            val originalSize = songs.size
            // Filter out what to download by removing local songs
            // then attempt to move some of them from cache (if available).
            val songs = songs.filterNot { it.isLocal || isDownloaded(it.id) || moveFromCache(it.id) }

            logger.v { "Downloading ${songs.size} out of $originalSize songs" }

            Database.asyncTransaction {
                songTable.insertIgnore( songs )
            }

            val requests = songs.map( ::toDownloadRequest )
            download( *requests.toTypedArray() )
        }
    }

    override fun remove( vararg ids: String ) {
        for( id in ids )
            DownloadService.sendRemoveDownload(context, DownloadServiceImpl::class.java, id, false)
    }

    override fun removeMediaItem( mediaItem: MediaItem ) {
        if( mediaItem.isLocal ) return

        logger.v { "Removing ${mediaItem.mediaId} from download storage" }

        Database.asyncTransaction {
            formatTable.deleteById(mediaItem.mediaId)
        }

        remove( mediaItem.mediaId )
    }

    override fun removeMediaItems( mediaItems: List<MediaItem> ) {
        coroutineScope.launch {
            val mediaIds = mediaItems.filterNot( MediaItem::isLocal ).map( MediaItem::mediaId )

            logger.v { "Removing ${mediaIds.size} out of ${mediaItems.size} media items from download storage" }

            Database.asyncTransaction {
                formatTable.deleteByIds( mediaIds )
            }

            remove( *mediaIds.toTypedArray() )
        }
    }

    override fun removeSong( song: Song ) {
        if( song.isLocal ) return

        logger.v { "Removing ${song.id} from download storage" }

        Database.asyncTransaction {
            formatTable.deleteById(song.id)
        }

        remove( song.id )
    }

    override fun removeSongs( songs: List<Song> ) {
        coroutineScope.launch {
            val ids = songs.filterNot( Song::isLocal ).map( Song::id )

            logger.v { "Removing ${ids.size} out of ${songs.size} songs from download storage" }

            Database.asyncTransaction {
                formatTable.deleteByIds( ids )
            }

            remove( *ids.toTypedArray() )
        }
    }
}