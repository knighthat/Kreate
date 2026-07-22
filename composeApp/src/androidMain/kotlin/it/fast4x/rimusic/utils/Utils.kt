package it.fast4x.rimusic.utils


import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateUtils
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.ThumbRating
import androidx.media3.common.util.UnstableApi
import app.kreate.database.models.Album
import app.kreate.database.models.Song
import app.kreate.di.THUMBNAIL_SIZE
import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.util.toDuration
import it.fast4x.rimusic.service.modern.isLocal

const val EXPLICIT_BUNDLE_TAG = "is_explicit"


val InnertubeAlbum.toAlbum: Album
    get() = Album(
        id = id,
        title = name,
        thumbnailUrl = thumbnails.lastOrNull()?.url,
        year = year?.toString(),
        authorsText = artists.joinToString { it.text },
        shareUrl = urlCanonical,
        timestamp = System.currentTimeMillis(),
        bookmarkedAt = null,
        isYoutubeAlbum = true
    )

val Song.asMediaItem: MediaItem
    @UnstableApi
    get() = MediaItem.Builder()
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle( title )
                .setDisplayTitle( cleanTitle() )
                .setArtist( cleanArtistsText() )
                .setArtworkUri(
                    cleanThumbnailUrl()?.thumbnail( THUMBNAIL_SIZE )?.toUri()
                )
                .setDurationMs( durationText.toDuration().inWholeMilliseconds )
                .setMediaType( MediaMetadata.MEDIA_TYPE_MUSIC )
                .setUserRating( ThumbRating(likedAt != null && likedAt!! > 0) )
                .setIsBrowsable( false )
                .setIsPlayable( true )
                .setExtras(
                    bundleOf(
                        EXPLICIT_BUNDLE_TAG to isExplicit
                    )
                )
                .build()
        )
        .setMediaId( id )
        .setUri(
            if ( isLocal )
                ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id.toLong()
                )
            else
                id.toUri()
        )
        .setCustomCacheKey(
            // `null` cache key (may) prevent media3 from caching the local song
            id.takeIf { !isLocal }
        )
        .build()

val MediaItem.asSong: Song
    get() = Song (
        id = mediaId,
        title = mediaMetadata.title.toString(),
        artistsText = mediaMetadata.artist.toString(),
        durationText = mediaMetadata.durationMs?.let { DateUtils.formatElapsedTime(it / 1000) },
        thumbnailUrl = mediaMetadata.artworkUri.toString(),
        isLocal = isLocal
    )

val MediaItem.isVideo: Boolean
    get() = mediaMetadata.extras?.getBoolean("isVideo") == true

val MediaItem.isExplicit: Boolean
    get() = mediaMetadata.extras?.getBoolean( EXPLICIT_BUNDLE_TAG ) == true

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this
    "https://lh3\\.googleusercontent\\.com/.*=w(\\d+)-h(\\d+).*".toRegex().matchEntire(this)?.groupValues?.let { group ->
        val (W, H) = group.drop(1).map { it.toInt() }
        var w = width
        var h = height
        if (w != null && h == null) h = (w / W) * H
        if (w == null && h != null) w = (h / H) * W
        return "${split("=w")[0]}=w$w-h$h-p-l90-rj"
    }
    if (this matches "https://yt3\\.ggpht\\.com/.*=s(\\d+)".toRegex()) {
        return "$this-s${width ?: height}"
    }
    return this
}

fun String?.thumbnail(size: Int): String? {
    return when {
        this?.startsWith("https://lh3.googleusercontent.com") == true -> "$this-w$size-h$size"
        this?.startsWith("https://yt3.ggpht.com") == true -> "$this-w$size-h$size-s$size"
        else -> this
    }
}
fun String?.thumbnail(): String? {
    return this
}

fun isNetworkConnected(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    if (isAtLeastAndroid6) {
        val networkInfo = cm.getNetworkCapabilities(cm.activeNetwork)
        return networkInfo?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                networkInfo.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    } else {
        return try {
            if (cm.activeNetworkInfo == null) {
                false
            } else {
                cm.activeNetworkInfo?.isConnected!!
            }
        } catch (e: Exception) {
            false
        }
    }
}

fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        ?: return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val networkInfo = cm.getNetworkCapabilities(cm.activeNetwork)
        // if no network is available networkInfo will be null
        // otherwise check if we are connected to internet
        //return networkInfo != null
        return networkInfo?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
    } else {
        return try {
            if (cm.activeNetworkInfo == null) {
                false
            } else {
                cm.activeNetworkInfo?.isConnected!!
            }
        } catch (e: Exception) {
            false
        }
    }

}

@Composable
fun isNetworkAvailableComposable(): Boolean {
    val context = LocalContext.current
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        ?: return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val networkInfo = cm.getNetworkCapabilities(cm.activeNetwork)
        // if no network is available networkInfo will be null
        // otherwise check if we are connected
        return networkInfo?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
    } else {
        return try {
            if (cm.activeNetworkInfo == null) {
                false
            } else {
                cm.activeNetworkInfo?.isConnected!!
            }
        } catch (e: Exception) {
            false
        }
    }
}

@Composable
fun getVersionName(): String {
    val context = LocalContext.current
    try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return pInfo.versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
    return ""
}
@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun getLongVersionCode(): Long {
    val context = LocalContext.current
    try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return pInfo.longVersionCode
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
    return 0L
}


@Composable
fun getVersionCode(): Int {
    val context = LocalContext.current
    try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return pInfo.versionCode
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
    return 0
}


inline val isAtLeastAndroid6
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

inline val isAtLeastAndroid7
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

inline val isAtLeastAndroid8
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

inline val isAtLeastAndroid81
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1

inline val isAtLeastAndroid10
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

inline val isAtLeastAndroid11
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

inline val isAtLeastAndroid12
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

inline val isAtLeastAndroid13
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

inline val isAtLeastAndroid14
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

fun Modifier.conditional(condition : Boolean, modifier : Modifier.() -> Modifier) : Modifier {
    return if (condition) {
        then(modifier(Modifier))
    } else {
        this
    }
}
