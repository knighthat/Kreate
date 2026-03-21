package app.kreate.android.service.playback

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.service.download.CacheState
import app.kreate.constant.SongSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.ext.FormatWithSong
import app.kreate.database.models.PersistentQueue
import app.kreate.database.models.Song
import app.kreate.di.CacheType
import app.kreate.util.cleanPrefix
import co.touchlab.kermit.Logger
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.MainActivity
import it.fast4x.rimusic.enums.StatisticsType
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


@UnstableApi
class MediaLibrarySessionCallback(
    private val context: Context,
) : MediaLibrarySession.Callback, KoinComponent {

    private val cache: Cache by inject(CacheType.CACHE)
    private val cacheState: CacheState by inject()

    private val scope = CoroutineScope(Dispatchers.Main) + Job()
    var searchedSongs: List<Song> = emptyList()

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val connectionResult = super.onConnect(session, controller)
        return MediaSession.ConnectionResult.accept(
            connectionResult.availableSessionCommands
                .buildUpon()
                .add( Command.search )
                .add( Command.download )
                .add( Command.like )
                .add( Command.cycleRepeat )
                .add( Command.toggleShuffle )
                .add( Command.toggleRadio )
                .build(),
            connectionResult.availablePlayerCommands
        )
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        println("PlayerServiceModern MediaLibrarySessionCallback.onSearch: $query")
        session.notifySearchResultChanged(browser, query, 0, params)
        return Futures.immediateFuture(LibraryResult.ofVoid(params))
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        println("PlayerServiceModern MediaLibrarySessionCallback.onGetSearchResult: $query")
        runBlocking(Dispatchers.IO) {
            searchedSongs = Innertube.searchPage(
                body = SearchBody(
                    query = query,
                    params = Innertube.SearchFilter.Song.value
                ),
                fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
            )?.map {
                it?.items?.map { it.asSong }
            }?.getOrNull() ?: emptyList()

            val resultList = searchedSongs.map {
                it.toMediaItem(Tree.SEARCH_RESULTS)
            }
            return@runBlocking Futures.immediateFuture(LibraryResult.ofItemList(resultList, params))
        }

        return Futures.immediateFuture(LibraryResult.ofItemList(searchedSongs.map {
            it.toMediaItem(
                Tree.SEARCH_RESULTS
            )
        }, params))
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> = scope.future {
        try {
            if( customCommand == Command.search ) {
                val intent = Intent(context, MainActivity::class.java)
                    .setAction( MainActivity.action_search )
                    .setFlags( FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK )
                context.startActivity(  intent )
            } else {
                val intent = Intent(context, PlaybackService::class.java)
                    .setAction( customCommand.customAction )
                context.startService( intent )
            }

            SessionResult(SessionResult.RESULT_SUCCESS)
        } catch( err: Exception ) {
            Logger.e( "", err )
            SessionResult(SessionError.ERROR_UNKNOWN)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
        LibraryResult.ofItem(
            MediaItem.Builder()
                .setMediaId(Tree.ROOT)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsPlayable(false)
                        .setIsBrowsable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build(),
            params
        )
    )

    @OptIn(UnstableApi::class)
    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future(Dispatchers.IO) {
        LibraryResult.ofItemList(
            when (parentId) {
                Tree.ROOT -> listOf(
                    browsableMediaItem(
                        Tree.SONGS,
                        context.getString(R.string.songs),
                        null,
                        drawableUri(R.drawable.musical_notes),
                        MediaMetadata.MEDIA_TYPE_PLAYLIST
                    ),
                    browsableMediaItem(
                        Tree.ARTISTS,
                        context.getString(R.string.artists),
                        null,
                        drawableUri(R.drawable.people),
                        MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
                    ),
                    browsableMediaItem(
                        Tree.ALBUMS,
                        context.getString(R.string.albums),
                        null,
                        drawableUri(R.drawable.album),
                        MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                    ),
                    browsableMediaItem(
                        Tree.PLAYLISTS,
                        context.getString(R.string.playlists),
                        null,
                        drawableUri(R.drawable.library),
                        MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                    )
                )

                Tree.SONGS -> Database.eventTable
                                                    .findSongsMostPlayedBetween( StatisticsType.OneMonth.timeStampInMillis() )
                                                    .first()
                                                    .ifEmpty {
                                                        // Only here to avoid empty list
                                                        Database.eventTable
                                                                .findSongsMostPlayedBetween( 0L )
                                                                .first()
                                                    }
                                                    .map { it.toMediaItem(parentId) }

                Tree.ARTISTS -> Database.artistTable.allFollowing().first().map { artist ->
                    browsableMediaItem(
                        "${Tree.ARTISTS}/${artist.id}",
                        artist.name ?: "",
                        "",
                        artist.thumbnailUrl?.toUri(),
                        MediaMetadata.MEDIA_TYPE_ARTIST
                    )
                }

                Tree.ALBUMS -> Database.albumTable.blockingAll().map { album ->
                    browsableMediaItem(
                        "${Tree.ALBUMS}/${album.id}",
                        album.title ?: "",
                        album.authorsText,
                        album.cleanThumbnailUrl()?.toUri(),
                        MediaMetadata.MEDIA_TYPE_ALBUM
                    )
                }


                Tree.PLAYLISTS -> {
                    val likedSongCount = Database.songTable.allFavorites().first().size
                    val cachedSongCount = getCountCachedSongs().first()
                    val downloadedSongCount = getCountDownloadedSongs()
                    val onDeviceSongCount = Database.songTable.allOnDevice().first().size
                    val playlists = Database.playlistTable.sortPreviewsBySongCount().first()
                    listOf(
                        browsableMediaItem(
                            "${Tree.PLAYLISTS}/${Id.FAVORITES}",
                            context.getString(R.string.favorites),
                            likedSongCount.toString(),
                            drawableUri(R.drawable.heart),
                            MediaMetadata.MEDIA_TYPE_PLAYLIST
                        ),
                        browsableMediaItem(
                            "${Tree.PLAYLISTS}/${Id.CACHED}",
                            context.getString(R.string.cached),
                            cachedSongCount.toString(),
                            drawableUri(R.drawable.download),
                            MediaMetadata.MEDIA_TYPE_PLAYLIST
                        ),
                        browsableMediaItem(
                            "${Tree.PLAYLISTS}/${Id.DOWNLOADED}",
                            context.getString(R.string.downloaded),
                            downloadedSongCount.toString(),
                            drawableUri(R.drawable.downloaded),
                            MediaMetadata.MEDIA_TYPE_PLAYLIST
                        ),
                        browsableMediaItem(
                            "${Tree.PLAYLISTS}/${Id.TOP}",
                            context.getString(R.string.playlist_top),
                            Preferences.MAX_NUMBER_OF_TOP_PLAYED.value.name,
                            drawableUri(R.drawable.trending),
                            MediaMetadata.MEDIA_TYPE_PLAYLIST
                        ),
                        browsableMediaItem(
                            "${Tree.PLAYLISTS}/${Id.ON_DEVICE}",
                            context.getString(R.string.on_device),
                            onDeviceSongCount.toString(),
                            drawableUri(R.drawable.devices),
                            MediaMetadata.MEDIA_TYPE_PLAYLIST
                        )

                    ) + playlists.map { playlist ->
                        browsableMediaItem(
                            "${Tree.PLAYLISTS}/${playlist.playlist.id}",
                            playlist.playlist.name,
                            playlist.songCount.toString(),
                            drawableUri(R.drawable.playlist),
                            MediaMetadata.MEDIA_TYPE_PLAYLIST
                        )
                    }

                }


                else -> when {

                    parentId.startsWith("${Tree.ARTISTS}/") ->
                        Database.songArtistMapTable
                                .allSongsBy( parentId.removePrefix("${Tree.ARTISTS}/") )
                                .first()
                                .map { it.toMediaItem( parentId ) }

                    parentId.startsWith("${Tree.ALBUMS}/") -> {
                        val albumId = parentId.removePrefix("${Tree.ALBUMS}/")

                        Database.songAlbumMapTable
                                .allSongsOf( albumId )
                                .first()
                                .map { it.toMediaItem( parentId ) }
                    }

                    parentId.startsWith("${Tree.PLAYLISTS}/") -> {

                        when (val playlistId =
                            parentId.removePrefix("${Tree.PLAYLISTS}/")) {
                            Id.FAVORITES -> Database.songTable.allFavorites()
                            Id.CACHED -> Database.formatTable
                                                 .allWithSongs()
                                                 .map { list ->
                                                     list.filter {
                                                             val contentLength = it.format.contentLength
                                                             contentLength != null && cache.isCached( it.song.id, 0L, contentLength )
                                                         }
                                                         .map( FormatWithSong::song )
                                                         .reversed()
                                                 }
                            Id.TOP ->
                                Database.eventTable
                                        .findSongsMostPlayedBetween(
                                            from = 0,
                                            limit = Preferences.MAX_NUMBER_OF_TOP_PLAYED
                                                            .value
                                                            .toInt()
                                        )
                            Id.ON_DEVICE -> Database.songTable.allOnDevice()
                            Id.DOWNLOADED -> {
                                val downloads = cacheState.downloaded.value.filterValues { it == Download.STATE_COMPLETED }.keys
                                // TODO: Make a SQL statement that only queries for songs with ids in [downloads]
                                Database.songTable
                                        .sortAll(SongSortBy.TITLE, SortOrder.ASCENDING)
                                        .map { list ->
                                            list.filter { it.id in downloads }
                                        }
                            }

                            else -> Database.songPlaylistMapTable.allSongsOf( playlistId.toLong() )
                        }.first().map {
                            it.toMediaItem(parentId)
                        }


                    }

                    else -> emptyList()
                }

            },
            params
        )
    }

    @OptIn(UnstableApi::class)
    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future(Dispatchers.IO) {
        println("PlayerServiceModern MediaLibrarySessionCallback.onGetItem: $mediaId")

        Database.songTable
                .findById( mediaId )
                .first()
                ?.asMediaItem
                ?.let {
                    LibraryResult.ofItem( it, null )
                }
                ?: LibraryResult.ofError( SessionError.ERROR_UNKNOWN )
    }

    // Play from Android Auto
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        var queryList = emptyList<Song>()
        var startIdx = startIndex

        runCatching {
            var songId = ""

            val paths = mediaItems.first().mediaId.split( "/" )
            when( paths.first() ) {
                Tree.SEARCH_RESULTS -> {
                    songId = paths[1]
                    queryList = searchedSongs
                }
                Tree.SONGS -> {
                    songId = paths[1]
                    queryList = Database.songTable.blockingAll()
                }
                Tree.ARTISTS -> {
                    songId = paths[2]
                    queryList = Database.songArtistMapTable.allSongsBy( paths[1] ).first()
                }
                Tree.ALBUMS -> {
                    songId = paths[2]
                    queryList = Database.songAlbumMapTable.allSongsOf( paths[1] ).first()
                }
                Tree.PLAYLISTS -> {
                    val playlistId = paths[1]
                    songId = paths[2]
                    queryList = when ( playlistId ) {
                        Id.FAVORITES -> Database.songTable.allFavorites().map { it.reversed() }
                        Id.CACHED -> Database.formatTable
                                             .allWithSongs()
                                             .map { list ->
                                                 list.fastFilter {
                                                     val contentLength = it.format.contentLength
                                                     contentLength != null && cache.isCached( it.song.id, 0L, contentLength )
                                                 }
                                                     .reversed()
                                                     .fastMap( FormatWithSong::song )
                                             }
                        Id.TOP -> Database.eventTable
                                           // Already in DESC order
                                           .findSongsMostPlayedBetween(
                                               from = 0,
                                               limit = Preferences.MAX_NUMBER_OF_TOP_PLAYED
                                                               .value
                                                               .toInt()
                                           )
                        Id.ON_DEVICE -> Database.songTable.allOnDevice()
                        Id.DOWNLOADED -> {
                            val downloads = cacheState.downloaded.value.filterValues { it == Download.STATE_COMPLETED }.keys
                            // TODO: Make a SQL statement that only queries for songs with ids in [downloads]
                            Database.songTable
                                    .sortAll(SongSortBy.TITLE, SortOrder.ASCENDING)
                                    .map { list ->
                                        list.filter { it.id in downloads }
                                    }
                        }

                        else -> Database.songPlaylistMapTable.allSongsOf( playlistId.toLong() )
                    }.first()
                }
            }

            startIdx = queryList.indexOfFirst { it.id == songId }.coerceAtLeast( 0 )
        }

        return@future MediaSession.MediaItemsWithStartPosition( queryList.map( Song::asMediaItem ), startIdx, startPositionMs )
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val settablePlaylist = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        val defaultResult =
            MediaSession.MediaItemsWithStartPosition(
                emptyList(),
                0,
                0
            )
        if( !Preferences.ENABLE_PERSISTENT_QUEUE.value )
            return Futures.immediateFuture(defaultResult)

        scope.future {
            val queue = Database.queueTable.blockingItems()
            val startIndex = queue.indexOfFirst { it.position != null }
            val startPositionMs = queue[startIndex].position ?: C.TIME_UNSET
            val mediaItems = queue.map { it.song.asMediaItem.buildUpon().setTag( PersistentQueue.Tag ).build() }
            val resumptionPlaylist = MediaSession.MediaItemsWithStartPosition( mediaItems, startIndex, startPositionMs )

            settablePlaylist.set(resumptionPlaylist)
        }
        return settablePlaylist
    }

    private fun drawableUri(@DrawableRes id: Int) = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.resources.getResourcePackageName(id))
        .appendPath(context.resources.getResourceTypeName(id))
        .appendPath(context.resources.getResourceEntryName(id))
        .build()

    private fun browsableMediaItem(
        id: String,
        title: String,
        subtitle: String?,
        iconUri: Uri?,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_MUSIC
    ) =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(cleanPrefix(title))
                    .setSubtitle(subtitle)
                    .setArtist(subtitle)
                    .setArtworkUri(iconUri)
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(mediaType)
                    .build()
            )
            .build()

    private fun Song.toMediaItem(path: String) =
        MediaItem.Builder()
            .setMediaId("$path/$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(cleanTitle())
                    .setSubtitle(cleanArtistsText())
                    .setArtist(cleanArtistsText())
                    .setArtworkUri(cleanThumbnailUrl()?.toUri())
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()

    private fun getCountCachedSongs() =
        Database.formatTable
                .allWithSongs()
                .map { list ->
                    list.filter {
                            val contentLength = it.format.contentLength
                            contentLength != null && cache.isCached( it.song.id, 0L, contentLength )
                        }
                        .size
                }

    private fun getCountDownloadedSongs() =
        cacheState.downloaded.value.values.count { it == Download.STATE_COMPLETED }

    object Command {

        val search = SessionCommand("SEARCH", Bundle.EMPTY)
        val download = SessionCommand(PlaybackService.ACTION_DOWNLOAD, Bundle.EMPTY)
        val like = SessionCommand(PlaybackService.ACTION_LIKE, Bundle.EMPTY)
        val cycleRepeat = SessionCommand(PlaybackService.PLAYER_ACTION_CYCLE_REPEAT, Bundle.EMPTY)
        val toggleShuffle = SessionCommand(PlaybackService.PLAYER_ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
        val toggleRadio = SessionCommand(PlaybackService.PLAYER_ACTION_TOGGLE_RADIO, Bundle.EMPTY)
    }

    object Id {
        const val FAVORITES = "FAVORITES"
        const val CACHED = "CACHED"
        const val DOWNLOADED = "DOWNLOADED"
        const val TOP = "TOP"
        const val ON_DEVICE = "ON_DEVICE"
    }

    object Tree {
        const val ROOT = "ROOT"
        const val SONGS = "SONGS"
        const val ARTISTS = "ARTISTS"
        const val ALBUMS = "ALBUMS"
        const val PLAYLISTS = "PLAYLISTS"
        const val SEARCH_RESULTS = "SEARCH_RESULTS"
    }
}
