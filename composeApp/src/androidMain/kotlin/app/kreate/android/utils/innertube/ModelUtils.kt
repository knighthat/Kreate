package app.kreate.android.utils.innertube

import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapNotNull
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.kreate.database.models.Artist
import app.kreate.database.models.Song
import app.kreate.gateway.innertube.models.InnertubeArtist
import app.kreate.gateway.innertube.models.InnertubeSong
import it.fast4x.rimusic.utils.EXPLICIT_BUNDLE_TAG


val InnertubeSong.toSong: Song
    get() = Song(
        id = this.id,
        title = name,
        artistsText = this.artistsText,
        durationText = this.durationText,
        thumbnailUrl = this.thumbnails.firstOrNull()?.url,
        likedAt = null,
        totalPlayTimeMs = 0,
        isExplicit = isExplicit
    )

val InnertubeSong.toMediaItem: MediaItem
    get() = MediaItem.Builder()
                     .setMediaMetadata(
                         MediaMetadata.Builder()
                                      .setTitle( name )
                                      .setArtist( artistsText )
                                      .setAlbumTitle( album?.text )
                                      .setArtworkUri( thumbnails.firstOrNull()?.url?.toUri() )
                                      .setExtras(
                                          bundleOf(
                                              "albumId" to album?.navigationEndpoint?.browseEndpoint?.browseId,
                                              EXPLICIT_BUNDLE_TAG to isExplicit,
                                              "artistNames" to artists.fastMap { it.text },
                                              "artistIds" to artists.fastMapNotNull { it.navigationEndpoint?.browseEndpoint?.browseId }
                                          )
                                      )
                                      .build()
                     )
                     .setMediaId( id )
                     .setUri( id.toUri() )
                     .build()

val InnertubeArtist.toArtist: Artist
    get() = Artist(
        id = id,
        name = name,
        thumbnailUrl = thumbnails.firstOrNull()?.url,
        isYoutubeArtist = true
    )

