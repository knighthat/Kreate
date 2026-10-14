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


import android.content.ContentUris
import android.os.Bundle
import android.provider.MediaStore
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
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.model.UserAlbumView
import me.knighthat.kreate3.database.model.UserArtistView
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.model.UserSongView
import me.knighthat.kreate3.source.local.albumArtUri
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
const val EXTRA_IS_MONTHLY = "is_monthly"

/**
 * Builds a [MediaItem] whose metadata is described by [block].
 */
private inline fun mediaItem(
    mediaId: String,
    uri: android.net.Uri? = null,
    block: MediaMetadata.Builder.() -> Unit,
): MediaItem =
    MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().apply(block).build())
        .build()

private fun List<String>.joined(): String? = takeIf { it.isNotEmpty() }?.joinToString(", ")

private inline fun extras(block: Bundle.() -> Unit): Bundle = Bundle().apply(block)

/**
 * The reaction kept in a timestamp: `null` is neutral, epoch millis `-1` is a dislike and any
 * other value is a like, follow or bookmark.
 */
private fun Instant?.toRating(): Rating = when {
    this == null -> ThumbRating()
    toEpochMilliseconds() == -1L -> ThumbRating(false)
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
    get() = isLocal || isExplicit || isLiked || isBookmarked || isFollowed

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
 * Where the item comes from, `null` for an item made without one.
 */
val MediaItem.origin: Origin?
    get() = mediaMetadata.extras?.getString(EXTRA_ORIGIN)
        ?.let { name -> Origin.entries.firstOrNull { it.name == name } }

/**
 * A song with the values the user sees. A song from an OpenSubsonic server is shown but can't be
 * played yet, its stream needs the sign in, which isn't added to the player yet.
 *
 * @return `null` when the song can't be shown: a song from YouTube needs a stream address that
 * is not resolved yet, and an unavailable file cannot be read.
 */
fun UserSongView.toMediaItem(): MediaItem? {
    if (!isAvailable) return null

    val uri = when (origin) {
        Origin.LOCAL -> ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            songId.toLongOrNull() ?: return null,
        )

        Origin.YOUTUBE -> return null
        Origin.OPEN_SUBSONIC -> null
    }

    return mediaItem(songId, uri) {
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
        setIsPlayable(uri != null)
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
        setSubtitle(songCount.takeIf { it > 0 }?.let { if (it == 1) "1 song" else "$it songs" })
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
                if (isMonthly) "Monthly" else null,
                if (origin == Origin.LOCAL) "On this device" else origin.name
            )
        )
        setArtworkUri(thumbnail)
        setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
        setExtras(extras {
            putString(EXTRA_ORIGIN, origin.name)
            putBoolean(EXTRA_IS_MONTHLY, isMonthly)
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