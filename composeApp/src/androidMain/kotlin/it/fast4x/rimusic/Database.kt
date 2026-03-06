package it.fast4x.rimusic

import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastZip
import androidx.media3.common.MediaItem
import androidx.room.Transaction
import androidx.room.useWriterConnection
import app.kreate.android.Preferences
import app.kreate.database.AlbumTable
import app.kreate.database.ArtistTable
import app.kreate.database.EventTable
import app.kreate.database.FormatTable
import app.kreate.database.LyricsTable
import app.kreate.database.PlaylistTable
import app.kreate.database.QueuedMediaItemTable
import app.kreate.database.SearchQueryTable
import app.kreate.database.SongAlbumMapTable
import app.kreate.database.SongArtistMapTable
import app.kreate.database.SongPlaylistMapTable
import app.kreate.database.SongTable
import app.kreate.database.getAppDatabase
import app.kreate.database.getAppDatabaseBuilder
import app.kreate.database.models.Album
import app.kreate.database.models.Artist
import app.kreate.database.models.Playlist
import app.kreate.database.models.Song
import app.kreate.database.models.SongArtistMap
import it.fast4x.rimusic.Database.asyncQuery
import it.fast4x.rimusic.Database.asyncTransaction
import it.fast4x.rimusic.Database.insertIgnore
import it.fast4x.rimusic.utils.asSong
import kotlinx.coroutines.flow.first
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.utils.PropUtils
import timber.log.Timber

object Database {
    val FILE_NAME = if ( Preferences.ACTIVE_PROFILE.value == "default" ) "data.db" else "data_${Preferences.ACTIVE_PROFILE.value}.db"

    private val _internal by lazy {
        val builder = getAppDatabaseBuilder( appContext() )
        getAppDatabase( builder )
    }

    val songTable: SongTable
        get() = _internal.songTable
    val albumTable: AlbumTable
        get() = _internal.albumTable
    val artistTable: ArtistTable
        get() = _internal.artistTable
    val eventTable: EventTable
        get() = _internal.eventTable
    val formatTable: FormatTable
        get() = _internal.formatTable
    val lyricsTable: LyricsTable
        get() = _internal.lyricsTable
    val playlistTable: PlaylistTable
        get() = _internal.playlistTable
    val queueTable: QueuedMediaItemTable
        get() = _internal.queueTable
    val searchTable: SearchQueryTable
        get() = _internal.searchQueryTable
    val songAlbumMapTable: SongAlbumMapTable
        get() = _internal.songAlbumMapTable
    val songArtistMapTable: SongArtistMapTable
        get() = _internal.songArtistMapTable
    val songPlaylistMapTable: SongPlaylistMapTable
        get() = _internal.songPlaylistMapTable

    //**********************************************

    /**
     * Attempt to insert a [MediaItem] into `Song` table
     *
     * If [mediaItem] comes with album and artist(s) then
     * this method handles the insertion automatically.
     */
    fun insertIgnore( mediaItem: MediaItem ) {
        // Insert song
        songTable.insertIgnore( mediaItem.asSong )

        // Insert album
        mediaItem.mediaMetadata
                 .extras
                 ?.getString("albumId")
                 ?.let {
                     Album(
                         id = it,
                         title =  mediaItem.mediaMetadata.albumTitle.toString()
                     )
                 }
                 // Passing MediaItem causes infinite loop
                 ?.also( albumTable::insertIgnore )
                 ?.also {
                     songAlbumMapTable.map( mediaItem.mediaId, it.id )
                 }

        // Insert artist
        val artistsNames = mediaItem.mediaMetadata.extras?.getStringArrayList("artistNames").orEmpty()
        val artistsIds = mediaItem.mediaMetadata.extras?.getStringArrayList("artistIds").orEmpty()
        artistsNames.fastZip( artistsIds ) { name, id -> Artist( id, name ) }
                    .also( artistTable::insertIgnore )
                    .map { SongArtistMap(mediaItem.mediaId, it.id) }
                    .also( songArtistMapTable::insertIgnore )
    }

    @Transaction
    suspend fun upsert( innertubeSong: InnertubeSong ) {
        //<editor-fold desc="Insert song">
        val dbSong = songTable.findById( innertubeSong.id ).first()
        songTable.upsert(Song(
            id = innertubeSong.id,
            title = PropUtils.retainIfModified(
                dbSong?.title,
                innertubeSong.name
            ).orEmpty(),
            artistsText = PropUtils.retainIfModified( dbSong?.artistsText, innertubeSong.artistsText ),
            durationText = innertubeSong.durationText,       // Force update to new duration text
            thumbnailUrl = PropUtils.retainIfModified( dbSong?.thumbnailUrl, innertubeSong.thumbnails.firstOrNull()?.url ),
            likedAt = dbSong?.likedAt,
            totalPlayTimeMs = dbSong?.totalPlayTimeMs ?: 0
        ))
        //</editor-fold>

        // Insert album
        innertubeSong.album?.let { run ->
            run.navigationEndpoint
               ?.browseEndpoint
               ?.browseId
               ?.let { Album(it, run.text) }
               ?.also {
                   // Upsert will cause existing album to be overridden
                   // with only [Album.id] and [Album.title].
                   // We only need album to be existed in the database
                   albumTable.insertIgnore( it )
                   songAlbumMapTable.map( innertubeSong.id, it.id )
               }
        }

        // Insert artist
        innertubeSong.artists.fastForEach { run ->
            run.navigationEndpoint
                ?.browseEndpoint
                ?.browseId
                ?.let { Artist(it, run.text) }
                // Upsert will cause existing album to be overridden
                // with only [Artist.id] and [Artist.name].
                // We only need artist to be existed in the database
                ?.also( artistTable::insertIgnore )
                ?.let { SongArtistMap(innertubeSong.id, it.id) }
                ?.also( songArtistMapTable::insertIgnore )
        }
    }

    /**
     * Attempt to map [Song] to [Album].
     *
     * [song] and [album] are ensured to be existed
     * in the database before attempting to map two together.
     *
     * @param album to map
     * @param song to map
     * @param position of song in album, **default** or `-1` results in
     * database puts song to next available position in map
     */
    fun mapIgnore( album: Album, song: Song, position: Int = -1 ) {
        albumTable.insertIgnore( album )
        songTable.insertIgnore( song )
        songAlbumMapTable.map( song.id, album.id, position )
    }

    /**
     * Attempt to put [mediaItem] into `Song` table and map it to [Album].
     *
     * [mediaItem] is first inserted to database with [insertIgnore]
     * then [album] to ensure to be existed in the database  before
     * attempting to map two together.
     *
     * @param album to map
     * @param mediaItem song to map
     * @param position of song in album, **default** or `-1` results in
     * database puts song to next available position in map
     */
    fun mapIgnore( album: Album, mediaItem: MediaItem, position: Int = -1 ) =
        mapIgnore( album, mediaItem.asSong, position )


    /**
     * Attempt to map [Song] to [Artist].
     *
     * [songs] and [artist] are ensured to be existed in
     * the database before attempting to map two together.
     *
     * @param artist to map
     * @param songs to map
     */
    fun mapIgnore( artist: Artist, vararg songs: Song ) {
        if( songs.isEmpty() ) return

        artistTable.insertIgnore( artist )
        songs.forEach {
            songTable.insertIgnore( it )
            songArtistMapTable.insertIgnore(
                SongArtistMap(it.id, artist.id)
            )
        }
    }

    /**
     * Attempt to put [mediaItems] into `Song` table and map it to [Album].
     *
     * [mediaItems] are first inserted to database with [insertIgnore]
     * then [artist] to ensure to be existed in the database  before
     * attempting to map two together.
     *
     * @param artist to map
     * @param mediaItems list of songs to map
     */
    fun mapIgnore( artist: Artist, vararg mediaItems: MediaItem ) =
        mapIgnore( artist, *mediaItems.map( MediaItem::asSong ).toTypedArray() )

    /**
     * Attempt to map [Song] to [Playlist].
     *
     * [songs] and [playlist] are ensured to be existed in
     * the database before attempting to map two together.
     *
     * @param playlist to map
     * @param songs to map
     */
    fun mapIgnore( playlist: Playlist, vararg songs: Song ) {
        if( songs.isEmpty() ) return

        /**
         * [Playlist] has its [Playlist.id] `autogenerated`, therefore,
         * it's unknown until it's inserted into database with [PlaylistTable.insert].
         *
         *
         */
        val pId =
            if( playlist.id > 0 ) {
                playlistTable.insertIgnore( playlist )
                playlist.id
            } else
                playlistTable.insert( playlist )

        songs.forEach {
            songTable.insertIgnore( it )
            songPlaylistMapTable.map( it.id, pId )
        }
    }

    /**
     * Attempt to put [mediaItems] into `Song` table and map it to [Playlist].
     *
     * [mediaItems] are first inserted to database with [insertIgnore]
     * then [playlist] to ensure to be existed in the database  before
     * attempting to map two together.
     *
     * @param playlist to map
     * @param mediaItems list of songs to map
     */
    fun mapIgnore( playlist: Playlist, vararg mediaItems: MediaItem ) =
        mapIgnore( playlist, *mediaItems.map( MediaItem::asSong ).toTypedArray() )

    /**
     * Commit statements in BULK. If anything goes wrong during the transaction,
     * other statements will be cancelled and reversed to preserve database's integrity.
     * [Read more](https://sqlite.org/lang_transaction.html)
     *
     * [asyncTransaction] runs all statements on non-blocking
     * thread to prevent UI from going unresponsive.
     *
     * ## Best use cases:
     * - Commit multiple write statements that require data integrity
     * - Processes that take longer time to complete
     *
     * > Do NOT use this to retrieve data from the database.
     * > Use [asyncQuery] to retrieve records.
     *
     * @param block of statements to write to database
     */
    fun asyncTransaction( block: Database.() -> Unit ) =
        _internal.transactionExecutor.execute {
            this.block()
        }


    /**
     * Access and retrieve records from database.
     *
     * [asyncQuery] runs all statements asynchronously to
     * prevent blocking UI thread from going unresponsive.
     *
     * ## Best use cases:
     * - Background data retrieval
     * - Non-immediate UI component update (i.e. count number of songs)
     *
     * > Do NOT use this method to write data to database
     * > because it offers no fail-safe during write.
     * > Use [asyncTransaction] to modify database.
     *
     * @param block of statements to retrieve data from database
     */
    fun asyncQuery( block: Database.() -> Unit ) =
        _internal.queryExecutor.execute {
            this.block()
        }

    suspend fun checkpoint() = _internal.useWriterConnection { connection ->
        connection.usePrepared("PRAGMA wal_checkpoint(FULL)") { statement ->
            if (statement.step()) {
                val isBusy = statement.getLong(0) // 0 if not busy, 1 if busy
                val logFrames = statement.getLong(1)
                val checkpointedFrames = statement.getLong(2)

                Timber.tag("database").d( "Checkpoint performed! (is busy: $isBusy, logged frames: $logFrames, checkpointed frames: $checkpointedFrames)" )
            }
        }
    }

    fun close() = _internal.close()
}
