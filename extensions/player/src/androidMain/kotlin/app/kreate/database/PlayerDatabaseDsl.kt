package app.kreate.database

import androidx.room.Transaction
import app.kreate.database.models.Album
import app.kreate.database.models.Artist
import app.kreate.database.models.Song
import app.kreate.database.models.SongArtistMap
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.utils.PropUtils
import kotlinx.coroutines.flow.first


@Transaction
suspend fun Database.upsert( innertubeSong: InnertubeSong ) {
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
    innertubeSong.artists.forEach { run ->
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