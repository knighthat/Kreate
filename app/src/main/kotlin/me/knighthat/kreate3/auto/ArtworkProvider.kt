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

package me.knighthat.kreate3.auto

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import co.touchlab.kermit.Logger
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.playback.OPENED_COVER_FOLDER
import me.knighthat.kreate3.provider.Providers
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.CoverFiles
import me.knighthat.kreate3.source.CoverKind
import me.knighthat.kreate3.source.LARGE_COVER
import me.knighthat.kreate3.source.SMALL_COVER
import me.knighthat.kreate3.source.youtube.HEADER_ARTWORK
import me.knighthat.kreate3.ui.component.EXTRA_ALBUM_ID
import me.knighthat.kreate3.ui.component.EXTRA_ORIGIN
import me.knighthat.kreate3.ui.component.localSongId
import me.knighthat.kreate3.ui.component.origin
import me.knighthat.kreate3.ui.component.songThumbnailModels
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.GlobalContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

private const val TAG = "ArtworkProvider"

/** Query parameter holding the address of the cover, see [autoArtworkUri]. */
private const val SOURCE = "source"

/**
 * Query parameter holding the id of a queued song, see [withServedArtwork]. An address with it
 * loads what the player shows for the song, one without it the small cover of a list.
 */
private const val SONG = "song"

/** Query parameter holding the origin of a queued song, see [SONG]. */
private const val ORIGIN = "origin"

/** Query parameter holding the album of a queued song, see [SONG]. */
private const val ALBUM = "album"

/** Path of every cover address, see [autoArtworkUri]. */
private const val COVER_PATH = "cover"

/** Quality of the covers handed out, they are encoded again on their way out. */
private const val QUALITY = 85

private const val AUTHORITY_SUFFIX = ".artwork"

private val Context.artworkAuthority: String get() = "$packageName$AUTHORITY_SUFFIX"

/**
 * Hands the covers of the library to Android Auto. Auto only reads `content://` addresses, and
 * the covers are on the web, behind the server's sign in or in the device's media index, which
 * Auto can't read. They are loaded with the app's image loader, so the gates and proxies of the
 * sources apply, as do its caches.
 *
 * What Auto browses is served small ([smallCover]). The queue's songs carry addresses of their
 * own ([withServedArtwork]), so the media notification, the lock screen and Auto's player load
 * here what the player of the app shows, at its size.
 *
 * Not exported: [grantArtwork] lets the app that browses the library read it.
 */
class ArtworkProvider : ContentProvider(), KoinComponent {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = checkNotNull(context)
        val source = uri.getQueryParameter(SOURCE)?.toUri()
        if (source != null && !context.isServedSource(source))
            throw FileNotFoundException("$uri has no cover")
        val song = uri.getQueryParameter(SONG)
        // Called on a binder thread, which waits for the answer anyway
        val bitmap = runBlocking {
            if (song != null)
                playerCover(context, uri, song, source)
            else
                source?.let { context.smallCover(it) }
        } ?: throw FileNotFoundException("Couldn't load $uri")

        val (read, write) = ParcelFileDescriptor.createPipe()
        // A pipe holds little, the cover is written while Auto reads the other end
        get<CoroutineScope>().launch(Dispatchers.IO) {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(write).use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it)
                }
            } catch (e: IOException) {
                // Auto stopped reading, like when the row scrolled away
                Logger.d(e, TAG) { "Couldn't send $source" }
            }
        }
        return read
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    /**
     * The first cover that loads of the ones the player tries for the queued [song], at the
     * player's size, no larger than the picture is.
     *
     * @param address the address it was asked with, see [withServedArtwork]
     * @param cover the cover the song was queued with
     */
    private suspend fun playerCover(
        context: Context,
        address: Uri,
        song: String,
        cover: Uri?,
    ): Bitmap? {
        val metadata = MediaMetadata.Builder()
            .setArtworkUri(cover)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(
                bundleOf(
                    EXTRA_ORIGIN to address.getQueryParameter(ORIGIN),
                    EXTRA_ALBUM_ID to address.getQueryParameter(ALBUM),
                )
            )
            .build()
        val item = MediaItem.Builder().setMediaId(song).setMediaMetadata(metadata).build()
        return songThumbnailModels(item, get<Providers>(), HEADER_ARTWORK)
            .firstNotNullOfOrNull { context.cover(it, LARGE_COVER, Precision.INEXACT) }
    }
}

/**
 * Whether [ArtworkProvider] may load the cover at [source]: from the web, from what other apps
 * share, or a cover file the player shows. The app's other files are never handed out.
 */
internal fun Context.isServedSource(source: Uri): Boolean = when (source.scheme) {
    "http", "https" -> true
    ContentResolver.SCHEME_CONTENT -> source.authority != artworkAuthority
    ContentResolver.SCHEME_FILE ->
        CoverFiles(this).kindOf(source).let { it == CoverKind.DOWNLOADED || it == CoverKind.CUSTOM }
                || source.path?.let(::File)?.parentFile == File(cacheDir, OPENED_COVER_FOLDER)

    else -> false
}

/**
 * The cover at [source], drawn small, loaded like [ArtworkProvider] does; `null` when it can't
 * be loaded. A YouTube cover is asked at that size, whatever size its address was given at.
 */
internal suspend fun Context.smallCover(source: Uri): Bitmap? {
    val sized = GlobalContext.get().get<YouTubeProvider>()
        .artworkUri(source.toString(), SMALL_COVER)
        ?: source
    return cover(sized, SMALL_COVER)
        ?: if (sized.toString() != source.toString()) cover(source, SMALL_COVER) else null
}

/**
 * The cover at [source], drawn at most [size] wide and high, as a bitmap whose pixels can be
 * read; `null` when it can't be loaded.
 *
 * @param source what the image loader takes, an address or a song's local cover
 * @param precision [Precision.INEXACT] to leave a smaller picture at its size
 */
internal suspend fun Context.cover(
    source: Any,
    size: Int,
    precision: Precision = Precision.EXACT,
): Bitmap? {
    val request = ImageRequest.Builder(this)
        .data(source)
        .size(size)
        .precision(precision)
        // Compressed afterwards, a hardware bitmap can't be read
        .allowHardware(false)
        .build()
    val result = SingletonImageLoader.get(this).execute(request)
    return (result as? SuccessResult)?.image?.toBitmap()
}

/**
 * The cover at [source] as a small JPEG, for what can't read [ArtworkProvider], like the system's
 * media controls after a restart; `null` when it can't be loaded.
 */
internal suspend fun Context.smallCoverJpeg(source: Uri): ByteArray? {
    val bitmap = smallCover(source) ?: return null
    return withContext(Dispatchers.Default) {
        ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it)
        }.toByteArray()
    }
}

/**
 * The address Auto reads [cover] from, see [ArtworkProvider]. Icons of the app's resources, and
 * addresses that already are one of these, are read as they are.
 */
internal fun Context.autoArtworkUri(cover: Uri?): Uri? = when {
    cover == null || cover.isServedCover -> cover
    cover.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE -> cover
    else -> coverAddress().appendQueryParameter(SOURCE, cover.toString()).build()
}

private fun Context.coverAddress(): Uri.Builder =
    Uri.Builder()
        .scheme(ContentResolver.SCHEME_CONTENT)
        .authority(artworkAuthority)
        .appendPath(COVER_PATH)

/**
 * The song with its cover read through [ArtworkProvider], for the player's queue: Auto's player
 * only shows the playing song's cover from a `content://` address, and the queue's items are the
 * ones it shows. The address says which song it is, for the provider to load what the player of
 * the app shows ([songThumbnailModels]), so the media controls show that too. A song of this
 * device has one without a cover as well, the picture can be inside its file. The app reads the
 * cover itself, see [coverSource].
 */
internal fun MediaItem.withServedArtwork(context: Context): MediaItem {
    val cover = mediaMetadata.artworkUri
    if (cover?.isServedCover == true || cover?.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE)
        return this
    if (cover == null && localSongId == null) return this

    val served = context.coverAddress()
    cover?.let { served.appendQueryParameter(SOURCE, it.toString()) }
    served.appendQueryParameter(SONG, mediaId)
    origin?.let { served.appendQueryParameter(ORIGIN, it.name) }
    mediaMetadata.extras?.getString(EXTRA_ALBUM_ID)
        ?.let { served.appendQueryParameter(ALBUM, it) }
    return buildUpon()
        .setMediaMetadata(mediaMetadata.buildUpon().setArtworkUri(served.build()).build())
        .build()
}

/**
 * The cover an address of [ArtworkProvider] stands for, the address itself for any other. The
 * app loads the original at the size it draws, not the provider's copy. `null` for the address
 * of a song of this device that came without a cover, see [withServedArtwork].
 */
fun Uri.coverSource(): Uri? =
    if (isServedCover) getQueryParameter(SOURCE)?.toUri() else this

// Told apart without a Context, the authority is the package's followed by this
private val Uri.isServedCover: Boolean
    get() = scheme == ContentResolver.SCHEME_CONTENT
            && authority?.endsWith(AUTHORITY_SUFFIX) == true
            && pathSegments == listOf(COVER_PATH)

/** Lets [packageName], an app browsing the library like Auto, read the covers. */
internal fun Context.grantArtwork(packageName: String) {
    if (packageName == this.packageName) return
    grantUriPermission(
        packageName,
        Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(artworkAuthority).build(),
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
    )
}
