package it.fast4x.rimusic

import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMapNotNull
import androidx.compose.ui.util.fastZip
import androidx.media3.common.MediaItem
import androidx.room.AutoMigration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SimpleSQLiteQuery
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.models.Album
import it.fast4x.rimusic.models.Artist
import it.fast4x.rimusic.models.Event
import it.fast4x.rimusic.models.Format
import it.fast4x.rimusic.models.Lyrics
import it.fast4x.rimusic.models.Playlist
import it.fast4x.rimusic.models.QueuedMediaItem
import it.fast4x.rimusic.models.SearchQuery
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.models.SongAlbumMap
import it.fast4x.rimusic.models.SongArtistMap
import it.fast4x.rimusic.models.SongPlaylistMap
import it.fast4x.rimusic.models.SortedSongPlaylistMap
import it.fast4x.rimusic.utils.asSong
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.knighthat.database.AlbumTable
import me.knighthat.database.ArtistTable
import me.knighthat.database.Converters
import me.knighthat.database.EventTable
import me.knighthat.database.FormatTable
import me.knighthat.database.LyricsTable
import me.knighthat.database.PlaylistTable
import me.knighthat.database.QueuedMediaItemTable
import me.knighthat.database.SearchQueryTable
import me.knighthat.database.SongAlbumMapTable
import me.knighthat.database.SongArtistMapTable
import me.knighthat.database.SongPlaylistMapTable
import me.knighthat.database.SongTable
import me.knighthat.database.migration.From10To11Migration
import me.knighthat.database.migration.From11To12Migration
import me.knighthat.database.migration.From14To15Migration
import me.knighthat.database.migration.From20To21Migration
import me.knighthat.database.migration.From21To22Migration
import me.knighthat.database.migration.From22To23Migration
import me.knighthat.database.migration.From23To24Migration
import me.knighthat.database.migration.From24To25Migration
import me.knighthat.database.migration.From25To26Migration
import me.knighthat.database.migration.From26To27Migration
import me.knighthat.database.migration.From3To4Migration
import me.knighthat.database.migration.From7To8Migration
import me.knighthat.database.migration.From8To9Migration
import me.knighthat.utils.PropUtils

object Database {
    const val FILE_NAME = "data.db"

    private val _internal: DatabaseInitializer
        get() = DatabaseInitializer.Instance

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

    fun upsert( songItem: Innertube.SongItem ) = asyncTransaction {
        val song =  songItem.asSong

        //<editor-fold defaultstate="collapsed" desc="Upsert song">
        val dbSong = runBlocking {
            songTable.findById( song.id ).first()
        }
        songTable.upsert(Song(
            id = song.id,
            title = PropUtils.retainIfModified( dbSong?.title, song.title ).orEmpty(),
            artistsText = PropUtils.retainIfModified( dbSong?.artistsText, song.artistsText ),
            durationText = song.durationText,       // Force update to new duration text
            thumbnailUrl = PropUtils.retainIfModified( dbSong?.thumbnailUrl, song.thumbnailUrl ),
            likedAt = dbSong?.likedAt,
            totalPlayTimeMs = dbSong?.totalPlayTimeMs ?: 0
        ))
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Upsert artists">
        songItem.authors
                ?.fastMapNotNull {
                    val browseId = it.endpoint?.browseId ?: return@fastMapNotNull null

                    val dbArtist = runBlocking {
                        artistTable.findById( browseId ).first()
                    }

                    Artist(
                        id = browseId,
                        name = PropUtils.retainIfModified( dbArtist?.name, it.name ),
                        thumbnailUrl = dbArtist?.thumbnailUrl,
                        timestamp = dbArtist?.timestamp,
                        bookmarkedAt = dbArtist?.bookmarkedAt,
                        isYoutubeArtist = dbArtist?.isYoutubeArtist == true
                    )
                }
                ?.also( artistTable::upsert )
                ?.fastForEach { mapIgnore( it, song ) }
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Upsert album">
        songItem.album
                ?.let {
                    val browseId = it.endpoint?.browseId ?: return@let

                    val dbAlbum = runBlocking {
                        albumTable.findById( browseId ).first()
                    }

                    val fetchedAlbum = Album(
                        id = browseId,
                        title = PropUtils.retainIfModified( dbAlbum?.title, it.name ),
                        thumbnailUrl = PropUtils.retainIfModified( dbAlbum?.thumbnailUrl, song.thumbnailUrl ),
                        year = dbAlbum?.year,
                        authorsText = PropUtils.retainIfModified( dbAlbum?.title, it.name ),
                        shareUrl = dbAlbum?.shareUrl,
                        timestamp = dbAlbum?.timestamp,
                        bookmarkedAt = dbAlbum?.bookmarkedAt,
                        isYoutubeAlbum = dbAlbum?.isYoutubeAlbum == true
                    )

                    albumTable.upsert( fetchedAlbum )
                    mapIgnore( fetchedAlbum, song )
                }
        //</editor-fold>
    }

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

    fun checkpoint() = _internal.query( SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)") )
                                     .use {
                                         if( it.moveToFirst() ) it.getInt( 0 ) else -1
                                     }

    fun close() = _internal.close()
}

@androidx.room.Database(
    entities = [
        Song::class,
        SongPlaylistMap::class,
        Playlist::class,
        Artist::class,
        SongArtistMap::class,
        Album::class,
        SongAlbumMap::class,
        SearchQuery::class,
        QueuedMediaItem::class,
        Format::class,
        Event::class,
        Lyrics::class,
    ],
    views = [
        SortedSongPlaylistMap::class
    ],
    version = 27,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = From3To4Migration::class),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = From7To8Migration::class),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 11, to = 12, spec = From11To12Migration::class),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21, spec = From20To21Migration::class),
        AutoMigration(from = 21, to = 22, spec = From21To22Migration::class),
    ],
)
@TypeConverters(Converters::class)
abstract class DatabaseInitializer protected constructor() : RoomDatabase() {
    abstract val albumTable: AlbumTable
    abstract val artistTable: ArtistTable
    abstract val eventTable: EventTable
    abstract val formatTable: FormatTable
    abstract val lyricsTable: LyricsTable
    abstract val playlistTable: PlaylistTable
    abstract val queueTable: QueuedMediaItemTable
    abstract val searchQueryTable: SearchQueryTable
    abstract val songAlbumMapTable: SongAlbumMapTable
    abstract val songArtistMapTable: SongArtistMapTable
    abstract val songPlaylistMapTable: SongPlaylistMapTable
    abstract val songTable: SongTable

    companion object {
        val Instance: DatabaseInitializer by lazy {
            Room.databaseBuilder(
                    context = appContext(),
                    klass = DatabaseInitializer::class.java,
                    name = Database.FILE_NAME
                )
                .addMigrations(
                    From8To9Migration(),
                    From10To11Migration(),
                    From14To15Migration(),
                    From22To23Migration(),
                    From23To24Migration(),
                    From24To25Migration(),
                    From25To26Migration(),
                    From26To27Migration()
                )
                .build()
        }
    }
}
