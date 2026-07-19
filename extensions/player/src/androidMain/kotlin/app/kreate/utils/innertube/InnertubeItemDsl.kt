@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.kreate.utils.innertube

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.gateway.innertube.models.InnertubeVideo
import app.kreate.player.MediaItem
import app.kreate.util.toDuration


const val EXPLICIT_BUNDLE_TAG = "explicit"
const val ALBUM_ID_BUNDLE_TAG = "album"
const val ARTISTS_IDS_BUNDLE_TAG = "artists"
const val IS_LOCAL_BUNDLE_TAG = "is_local"

fun InnertubeSong.toMediaItem(): MediaItem {
    //<editor-fold desc="Bundles">
    val extras = Bundle()
    extras.putBoolean( EXPLICIT_BUNDLE_TAG, isExplicit )
    val albumId = album?.navigationEndpoint?.browseEndpoint?.browseId
    albumId?.let { extras.putString( ALBUM_ID_BUNDLE_TAG, id ) }
    val artistIds = ArrayList(artists.mapNotNull { it.navigationEndpoint?.browseEndpoint?.browseId })
    extras.putStringArrayList( ARTISTS_IDS_BUNDLE_TAG, artistIds )
    extras.putBoolean( IS_LOCAL_BUNDLE_TAG, false )
    //</editor-fold>
    val isVideo = this is InnertubeVideo
    val mediaType = if( isVideo ) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC
    val metadata = MediaMetadata.Builder()
        .setTitle( name )
        .setDisplayTitle( name )
        .setArtist( artists.joinToString("") { it.text } )
        .setAlbumTitle( album?.text )
        .setMediaType( mediaType )
        .setArtworkUri( thumbnails.firstOrNull()?.url?.toUri() )
        .setDurationMs( durationText.toDuration().inWholeMilliseconds )
        .setSubtitle( subtitle )
        .setIsBrowsable( false )
        .setIsPlayable( true )
        .setExtras( extras )
        .build()

    return androidx.media3.common.MediaItem.Builder()
        .setMediaMetadata( metadata )
        .setMediaId( id )
        .setUri( id.toUri() )
        .setCustomCacheKey( id )
        .build()
}