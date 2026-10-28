/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.ui.component


import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Rating
import androidx.media3.common.ThumbRating
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import androidx.paging.PagingData
import androidx.paging.flatMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.UserAlbumView
import me.knighthat.kreate3.database.model.UserArtistView
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.model.UserSongView
import me.knighthat.kreate3.database.repository.RemoteSong
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.source.local.albumArtUri
import org.koin.core.context.GlobalContext
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Keys of the values kept in `MediaMetadata.extras`, for the code that reads a [MediaItem] back.
 * [EXTRA_ORIGIN] holds the name of an [Origin]; [EXTRA_ARTIST_IDS] is a `StringArray`.
 */
const val EXTRA_ORIGIN = "origin"
const val EXTRA_URL = "url"
const val EXTRA_ALBUM_ID = "album_id"
const val EXTRA_ARTIST_IDS = "artist_ids"

/**
 * Names of the artists of [EXTRA_ARTIST_IDS] as a `StringArray` in the same order, empty for a
 * name that isn't known. Only songs fetched live have it, the library's songs keep names and ids
 * in no matching order.
 */
const val EXTRA_ARTIST_NAMES = "artist_names"
const val EXTRA_IS_MONTHLY = "is_monthly"

/** Id of a playlist at its origin, see [UserPlaylist.remoteId]. */
const val EXTRA_REMOTE_ID = "remote_id"

/** The [UserPlaylist.covers] of a playlist as a `StringArray` of their pictures. */
const val EXTRA_COVERS = "covers"

/**
 * For each of [EXTRA_COVERS], as a `StringArray` in the same order, the id of its song when the
 * song is on this device, empty otherwise.
 */
const val EXTRA_COVER_LOCAL_IDS = "cover_local_ids"

/**
 * Builds a [MediaItem] whose metadata is described by [block].
 */
private inline fun mediaItem(mediaId: String, block: MediaMetadata.Builder.() -> Unit): MediaItem =
    MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(MediaMetadata.Builder().apply(block).build())
        .build()

private fun List<String>.joined(): String? = takeIf { it.isNotEmpty() }?.joinToString(", ")

private inline fun extras(block: Bundle.() -> Unit): Bundle = Bundle().apply(block)

/**
 * The reaction kept in a timestamp: `null` is neutral, negative epoch millis are a dislike and
 * any other value is a like, follow or bookmark.
 */
private fun Instant?.toRating(): Rating = when {
    this == null -> ThumbRating()
    toEpochMilliseconds() < 0 -> ThumbRating(false)
    else -> ThumbRating(true)
}

/**
 * Marks the item as explicit for media controllers that show it. The standard key is a flag, it
 * is either there or not, so a value that is `false` or unknown (`null`) adds nothing.
 */
@OptIn(UnstableApi::class)
private fun Bundle.putExplicit(isExplicit: Boolean?) =
    putBoolean(MediaConstants.EXTRAS_KEY_IS_EXPLICIT, isExplicit == true)

/**
 * Second line under the title, the parts that are known joined with a middle dot.
 */
private fun subtitle(vararg parts: String?): String? =
    parts.filterNotNull().filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(" · ")

private fun Duration?.toMillis(): Long? = this?.inWholeMilliseconds

/**
 * Whether [ItemBadges] has anything to show for the item.
 */
val MediaItem.hasBadges: Boolean
    get() = origin != null || isExplicit || isLiked || isBookmarked || isFollowed

/**
 * Whether the item was marked explicit, see [toMediaItem].
 */
val MediaItem.isExplicit: Boolean
    @OptIn(UnstableApi::class)
    get() = mediaMetadata.extras?.getBoolean(MediaConstants.EXTRAS_KEY_IS_EXPLICIT) == true

private fun MediaItem.isRatedUp(mediaType: Int): Boolean =
    mediaMetadata.mediaType == mediaType
            && (mediaMetadata.userRating as? ThumbRating)?.let { it.isRated && it.isThumbsUp } == true

/**
 * Whether the user liked the song, see [toMediaItem].
 */
val MediaItem.isLiked: Boolean
    get() = isRatedUp(MediaMetadata.MEDIA_TYPE_MUSIC)

/**
 * Whether the user bookmarked the album, see [UserAlbumView.toMediaItem].
 */
val MediaItem.isBookmarked: Boolean
    get() = isRatedUp(MediaMetadata.MEDIA_TYPE_ALBUM)

/**
 * Whether the user follows the artist, see [UserArtistView.toMediaItem].
 */
val MediaItem.isFollowed: Boolean
    get() = isRatedUp(MediaMetadata.MEDIA_TYPE_ARTIST)

/**
 * Whether the item is a file on this device, see [toMediaItem].
 */
val MediaItem.isLocal: Boolean
    get() = origin == Origin.LOCAL

/**
 * Id of a playlist at its origin, `null` for a playlist made in the app.
 */
val MediaItem.remoteId: String?
    get() = mediaMetadata.extras?.getString(EXTRA_REMOTE_ID)

/**
 * Where the item comes from, `null` for an item made without one.
 */
val MediaItem.origin: Origin?
    get() = mediaMetadata.extras?.getString(EXTRA_ORIGIN)
        ?.let { name -> Origin.entries.firstOrNull { it.name == name } }

/**
 * A song with the values the user sees. It has no address to play, the player finds it from the
 * id and the origin when the song is read, see `DataSourceResolver`.
 *
 * @return `null` when the song can't be shown, like an unavailable file that cannot be read
 */
fun UserSongView.toMediaItem(): MediaItem? {
    if (!isAvailable) return null

    return mediaItem(songId) {
        setTitle(name)
        setSubtitle(artists.joined())
        setArtist(artists.joined())
        setAlbumArtist(artists.firstOrNull())
        setAlbumTitle(albumName)
        setTrackNumber(albumPosition)
        setArtworkUri(thumbnail)
        setDurationMs(duration.toMillis())
        setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        setUserRating(likedAt.toRating())
        setExtras(extras {
            putString(EXTRA_ORIGIN, origin.name)
            putString(EXTRA_ALBUM_ID, albumId)
            putStringArray(EXTRA_ARTIST_IDS, artistIds.toTypedArray())
            putExplicit(isExplicit)
        })
        setIsBrowsable(false)
        setIsPlayable(true)
    }
}

/**
 * An album to browse into. It has no source of its own, play its songs instead.
 */
fun UserAlbumView.toMediaItem(): MediaItem =
    mediaItem(albumId) {
        setTitle(name)
        setSubtitle(subtitle(artists.joined(), year?.toString()))
        setArtist(artists.joined())
        setAlbumArtist(artists.firstOrNull())
        setAlbumTitle(name)
        setReleaseYear(year)
        setTotalTrackCount(songCount)
        // Local albums keep no cover of their own, the index has one for each
        setArtworkUri(thumbnail ?: albumId.toLongOrNull()?.takeIf { origin == Origin.LOCAL }
            ?.let(::albumArtUri))
        setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
        setUserRating(bookmarkedAt.toRating())
        setExtras(extras {
            putString(EXTRA_ORIGIN, origin.name)
            putString(EXTRA_URL, url)
            putExplicit(isExplicit)
        })
        setIsBrowsable(true)
        setIsPlayable(false)
    }

/**
 * An artist to browse into. It has no source of its own, play its songs instead.
 */
fun UserArtistView.toMediaItem(): MediaItem =
    mediaItem(artistId) {
        setTitle(name)
        setSubtitle(
            songCount.takeIf { it > 0 }?.let {
                GlobalContext.get().get<Context>().resources.getQuantityString(
                    R.plurals.song,
                    it,
                    it
                )
            }
        )
        setArtist(name)
        setTotalTrackCount(songCount)
        setArtworkUri(thumbnail)
        setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
        setUserRating(followSince.toRating())
        setExtras(extras { putString(EXTRA_ORIGIN, origin.name) })
        setIsBrowsable(true)
        setIsPlayable(false)
    }

/**
 * A playlist to browse into. It has no source of its own, play its songs instead.
 */
fun UserPlaylist.toMediaItem(): MediaItem =
    mediaItem(id.toString()) {
        setTitle(name)
        setSubtitle(
            subtitle(
                if (isMonthly) GlobalContext.get().get<Context>()
                    .getString(R.string.playlist_type_monthly) else null,
                GlobalContext.get().get<Context>().getString(origin.sourceRes)
            )
        )
        setArtworkUri(thumbnail)
        setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
        setExtras(extras {
            putString(EXTRA_ORIGIN, origin.name)
            putBoolean(EXTRA_IS_MONTHLY, isMonthly)
            putString(EXTRA_REMOTE_ID, remoteId)
            putStringArray(EXTRA_COVERS, covers.map { it.thumbnail.toString() }.toTypedArray())
            putStringArray(
                EXTRA_COVER_LOCAL_IDS,
                covers.map { if (it.origin == Origin.LOCAL) it.songId else "" }.toTypedArray(),
            )
        })
        setIsBrowsable(true)
        setIsPlayable(false)
    }

/**
 * The songs as media items, ready to show. A song that can't be played right now is left out.
 * Convert before `cachedIn` so the items are kept with the pages.
 */
fun Flow<PagingData<UserSongView>>.toMediaItems(): Flow<PagingData<MediaItem>> =
    map { page -> page.flatMap { listOfNotNull(it.toMediaItem()) } }

/*
 * Items fetched live from an online catalog and shown without being stored. Nothing about them
 * is known to the user's library, so they carry no reaction.
 */

/**
 * A song of an online catalog. It is stored once it starts playing, see `PlayedSongRecorder`.
 *
 * @param thumbnail the picture to show, the song's or its album's when it has none
 */
fun RemoteSong.toLiveMediaItem(
    thumbnail: android.net.Uri? = song.thumbnail ?: album?.thumbnail
): MediaItem {
    val names = artists.mapNotNull { it.name }
    return mediaItem(song.id) {
        setTitle(song.name)
        setSubtitle(names.joined())
        setArtist(names.joined())
        setAlbumArtist(names.firstOrNull())
        setAlbumTitle(album?.name)
        setTrackNumber(song.albumPosition)
        setArtworkUri(thumbnail)
        setDurationMs(song.duration.toMillis())
        setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        setExtras(extras {
            putString(EXTRA_ORIGIN, song.origin.name)
            putString(EXTRA_ALBUM_ID, song.albumId)
            putStringArray(EXTRA_ARTIST_IDS, artists.map { it.id }.toTypedArray())
            putStringArray(EXTRA_ARTIST_NAMES, artists.map { it.name.orEmpty() }.toTypedArray())
            putExplicit(song.isExplicit)
        })
        setIsBrowsable(false)
        setIsPlayable(true)
    }
}

/**
 * An album of an online catalog.
 */
fun Album.toLiveMediaItem(thumbnail: android.net.Uri? = this.thumbnail): MediaItem {
    val names = artists.orEmpty()
    return mediaItem(id) {
        setTitle(name)
        setSubtitle(subtitle(names.joined(), year?.toString()))
        setArtist(names.joined())
        setAlbumArtist(names.firstOrNull())
        setAlbumTitle(name)
        setReleaseYear(year)
        setArtworkUri(thumbnail)
        setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
        setExtras(extras {
            putString(EXTRA_ORIGIN, origin.name)
            putString(EXTRA_URL, url)
            putExplicit(isExplicit)
        })
        setIsBrowsable(true)
        setIsPlayable(false)
    }
}

/**
 * An artist of an online catalog.
 */
fun Artist.toLiveMediaItem(thumbnail: android.net.Uri? = this.thumbnail): MediaItem =
    mediaItem(id) {
        setTitle(name)
        setArtist(name)
        setArtworkUri(thumbnail)
        setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
        setExtras(extras { putString(EXTRA_ORIGIN, origin.name) })
        setIsBrowsable(true)
        setIsPlayable(false)
    }

/**
 * A playlist of an online catalog that isn't saved, its media id is [UserPlaylist.remoteId].
 */
fun UserPlaylist.toLiveMediaItem(thumbnail: android.net.Uri? = this.thumbnail): MediaItem =
    mediaItem(checkNotNull(remoteId) { "A playlist of an online catalog has a remote id" }) {
        setTitle(name)
        setSubtitle(origin.name)
        setArtworkUri(thumbnail)
        setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
        setExtras(extras {
            putString(EXTRA_ORIGIN, origin.name)
            putBoolean(EXTRA_IS_MONTHLY, false)
            putString(EXTRA_REMOTE_ID, remoteId)
        })
        setIsBrowsable(true)
        setIsPlayable(false)
    }
