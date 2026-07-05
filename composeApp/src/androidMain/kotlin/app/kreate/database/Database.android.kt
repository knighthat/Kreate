package app.kreate.database

import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastZip
import androidx.media3.common.MediaItem
import androidx.room.Transaction
import app.kreate.database.models.Album
import app.kreate.database.models.Artist
import app.kreate.database.models.Playlist
import app.kreate.database.models.Song
import app.kreate.database.models.SongArtistMap
import app.kreate.database.repositories.PlaylistTable
import it.fast4x.rimusic.utils.asSong
import kotlinx.coroutines.flow.first
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.utils.PropUtils


/**
 * Attempt to insert a [MediaItem] into `Song` table
 *
 * If [mediaItem] comes with album and artist(s) then
 * this method handles the insertion automatically.
 */
fun Database.insertIgnore(mediaItem: MediaItem ) {
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
suspend fun Database.upsert(innertubeSong: InnertubeSong ) {
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
fun Database.mapIgnore(album: Album, song: Song, position: Int = -1 ) {
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
fun Database.mapIgnore(album: Album, mediaItem: MediaItem, position: Int = -1 ) =
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
fun Database.mapIgnore(artist: Artist, vararg songs: Song ) {
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
fun Database.mapIgnore(artist: Artist, vararg mediaItems: MediaItem ) =
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
fun Database.mapIgnore(playlist: Playlist, vararg songs: Song ) {
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
fun Database.mapIgnore(playlist: Playlist, vararg mediaItems: MediaItem ) =
    mapIgnore( playlist, *mediaItems.map( MediaItem::asSong ).toTypedArray() )
