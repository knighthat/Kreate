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
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.source.SMALL_COVER
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException

private const val TAG = "ArtworkProvider"

/** Query parameter holding the address of the cover, see [autoArtworkUri]. */
private const val SOURCE = "source"

/** Path of every cover address, see [autoArtworkUri]. */
private const val COVER_PATH = "cover"

/** Quality of the covers handed to Auto, they are drawn small. */
private const val QUALITY = 85

private const val AUTHORITY_SUFFIX = ".artwork"

private val Context.artworkAuthority: String get() = "$packageName$AUTHORITY_SUFFIX"

/**
 * Hands the covers of the library to Android Auto. Auto only reads `content://` addresses, and
 * the covers are on the web, behind the server's sign in or in the device's media index, which
 * Auto can't read. They are loaded with the app's image loader, so the gates and proxies of the
 * sources apply, as do its caches.
 *
 * The queue's songs carry these addresses too ([withServedArtwork]), so the media notification
 * and the lock screen load their covers here as well.
 *
 * Not exported: [grantArtwork] lets the app that browses the library read it.
 */
class ArtworkProvider : ContentProvider(), KoinComponent {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = checkNotNull(context)
        val source = uri.getQueryParameter(SOURCE)?.toUri()
            ?.takeIf { it.scheme in LOADED_SCHEMES && it.authority != context.artworkAuthority }
            ?: throw FileNotFoundException("$uri has no cover")
        // Called on a binder thread, which waits for the answer anyway
        val bitmap = runBlocking { context.smallCover(source) }
            ?: throw FileNotFoundException("Couldn't load $source")

        val (read, write) = ParcelFileDescriptor.createPipe()
        // A pipe holds little, the cover is written while Auto reads the other end
        get<CoroutineScope>().launch(Dispatchers.IO) {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(write).use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it)
                }
            } catch (e: IOException) {
                // Auto stopped reading, like when the row scrolled away
                Log.d(TAG, "Couldn't send $source", e)
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

    private companion object {

        /** Covers come from these, the app's other files are never handed out. */
        val LOADED_SCHEMES = setOf("http", "https", ContentResolver.SCHEME_CONTENT)
    }
}

/**
 * The cover at [source], drawn small, loaded like [ArtworkProvider] does; `null` when it can't
 * be loaded.
 */
internal suspend fun Context.smallCover(source: Uri): Bitmap? = cover(source, SMALL_COVER)

/**
 * The cover at [source], drawn at most [size] wide and high, as a bitmap whose pixels can be
 * read; `null` when it can't be loaded.
 */
internal suspend fun Context.cover(source: Uri, size: Int): Bitmap? {
    val request = ImageRequest.Builder(this)
        .data(source)
        .size(size)
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
    else -> Uri.Builder()
        .scheme(ContentResolver.SCHEME_CONTENT)
        .authority(artworkAuthority)
        .appendPath(COVER_PATH)
        .appendQueryParameter(SOURCE, cover.toString())
        .build()
}

/**
 * The song with its cover read through [ArtworkProvider], for the player's queue: Auto's player
 * only shows the playing song's cover from a `content://` address, and the queue's items are the
 * ones it shows. The app reads the cover itself, see [coverSource].
 */
internal fun MediaItem.withServedArtwork(context: Context): MediaItem {
    val cover = mediaMetadata.artworkUri
    val served = context.autoArtworkUri(cover)
    if (served == cover) return this
    return buildUpon()
        .setMediaMetadata(mediaMetadata.buildUpon().setArtworkUri(served).build())
        .build()
}

/**
 * The cover an address of [ArtworkProvider] stands for, the address itself for any other. The
 * app loads the original at the size it draws, not the provider's small copy.
 */
fun Uri.coverSource(): Uri =
    if (isServedCover) getQueryParameter(SOURCE)?.toUri() ?: this else this

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
