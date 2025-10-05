package app.kreate.android.utils.innertube

import androidx.compose.ui.util.fastJoinToString
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapNotNull
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.kreate.database.models.Album
import app.kreate.database.models.Artist
import app.kreate.database.models.Song
import app.kreate.util.EXPLICIT_PREFIX
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.Thumbnail
import it.fast4x.rimusic.utils.EXPLICIT_BUNDLE_TAG
import me.knighthat.innertube.model.InnertubeAlbum
import me.knighthat.innertube.model.InnertubeArtist
import me.knighthat.innertube.model.InnertubePlaylist
import me.knighthat.innertube.model.InnertubeSong

val InnertubeSong.toSong: Song
    get() = Song(
        id = this.id,
        title = "%s%s".format( if( isExplicit ) EXPLICIT_PREFIX else "", name ),
        artistsText = this.artistsText,
        durationText = this.durationText,
        thumbnailUrl = this.thumbnails.firstOrNull()?.url,
        likedAt = null,
        totalPlayTimeMs = 0
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
                                              "durationText" to durationText,
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

val InnertubeAlbum.toAlbum: Album
    get() = Album (
        id = id,
        title = name,
        thumbnailUrl = thumbnails.firstOrNull()?.url,
        year = year,
        authorsText = artists.fastJoinToString { it.text },
    )

val InnertubeArtist.toArtist: Artist
    get() = Artist(
        id = id,
        name = name,
        thumbnailUrl = thumbnails.firstOrNull()?.url,
        isYoutubeArtist = true
    )

val InnertubePlaylist.toOldInnertubePlaylist: Innertube.PlaylistItem
    get() = Innertube.PlaylistItem(
        info = Innertube.Info(
            name,
            NavigationEndpoint.Endpoint.Browse( null, id, null )
        ),
        channel = null,
        songCount = 0,
        isEditable = false,
        thumbnail = thumbnails.firstOrNull()?.let {
            Thumbnail(it.url, it.height.toInt(), it.width.toInt())
        }
    )

val InnertubeArtist.toOldInnertubeArtist: Innertube.ArtistItem
    get() = Innertube.ArtistItem(
        info = Innertube.Info(
            name,
            NavigationEndpoint.Endpoint.Browse( null, id, null )
        ),
        subscribersCountText = shortNumSubscribers,
        channelId = id,
        thumbnail = thumbnails.firstOrNull()?.let {
            Thumbnail(it.url, it.height.toInt(), it.width.toInt())
        }
    )
