package it.fast4x.rimusic.utils


import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateUtils
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import app.kreate.database.models.Album
import app.kreate.database.models.Artist
import app.kreate.database.models.Lyrics
import app.kreate.database.models.Song
import com.zionhuang.innertube.pages.LibraryPage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.UserAgent
import io.ktor.http.HttpStatusCode
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic.addToPlaylist
import it.fast4x.innertube.YtMusic.likeVideoOrSong
import it.fast4x.innertube.YtMusic.removelikeVideoOrSong
import it.fast4x.innertube.models.bodies.ContinuationBody
import it.fast4x.innertube.requests.playlistPage
import it.fast4x.innertube.utils.ProxyPreferences
import it.fast4x.innertube.utils.getProxy
import it.fast4x.kugou.KuGou
import it.fast4x.lrclib.LrcLib
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import me.knighthat.utils.Toaster
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

const val EXPLICIT_BUNDLE_TAG = "is_explicit"
const val LOCAL_BUNDLE_TAG = "is_local"

val Innertube.AlbumItem.asAlbum: Album
    get() = Album (
        id = key,
        title = info?.name,
        thumbnailUrl = thumbnail?.url,
        year = year,
        authorsText = authors?.joinToString(", ") { it.name ?: "" },
        //shareUrl =
    )

val Innertube.Podcast.EpisodeItem.asMediaItem: MediaItem
    @UnstableApi
    get() = MediaItem.Builder()
        .setMediaId(videoId)
        .setUri(videoId)
        .setCustomCacheKey(videoId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(author.toString())
                .setAlbumTitle(title)
                .setArtworkUri(thumbnail.firstOrNull()?.url?.toUri())
                .setDurationMs( durationToMillis(durationString.orEmpty()) )
                .setExtras(
                    bundleOf(
                        //"albumId" to album?.endpoint?.browseId,
                        "durationText" to durationString,
                        "artistNames" to author,
                        //"artistIds" to authors?.mapNotNull { it.endpoint?.browseId },
                    )
                )

                .build()
        )
        .build()

val Innertube.SongItem.asMediaItem: MediaItem
    @UnstableApi
    get() = MediaItem.Builder()
        .setMediaId(key)
        .setUri(key)
        .setCustomCacheKey(key)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(info?.name)
                .setArtist(authors?.filter {it.name?.matches(Regex("\\s*([,&])\\s*")) == false }?.joinToString(", ") { it.name ?: "" })
                .setAlbumTitle(album?.name)
                .setArtworkUri(thumbnail?.url?.toUri())
                .setDurationMs( durationToMillis( durationText.orEmpty() ) )
                .setExtras(
                    bundleOf(
                        "albumId" to album?.endpoint?.browseId,
                        "durationText" to durationText,
                        "artistNames" to authors?.filter { it.endpoint != null }
                            ?.mapNotNull { it.name },
                        "artistIds" to authors?.mapNotNull { it.endpoint?.browseId },
                        EXPLICIT_BUNDLE_TAG to explicit,
                        "setVideoId" to setVideoId,
                    )
                )
                .build()
        )
        .build()

val Innertube.SongItem.asSong: Song
    get() = Song (
        id = key,
        title = info?.name.orEmpty(),
        artistsText = authors?.joinToString(", ") { it.name ?: "" },
        durationText = durationText,
        thumbnailUrl = thumbnail?.url,
        isExplicit = explicit
    )

val Innertube.VideoItem.asMediaItem: MediaItem
    @UnstableApi
    get() = MediaItem.Builder()
        .setMediaId(key)
        .setUri(key)
        .setCustomCacheKey(key)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(info?.name)
                .setArtist(authors?.joinToString(", ") { it.name ?: "" })
                .setArtworkUri(thumbnail?.url?.toUri())
                .setDurationMs( durationToMillis( durationText.orEmpty() ) )
                .setExtras(
                    bundleOf(
                        "durationText" to durationText,
                        "artistNames" to authors?.filter { it.endpoint != null }
                            ?.mapNotNull { it.name },
                        "artistIds" to authors?.mapNotNull { it.endpoint?.browseId },
                        "isOfficialMusicVideo" to isOfficialMusicVideo,
                        "isUserGeneratedContent" to isUserGeneratedContent,
                        "isVideo" to true,
                        // "artistNames" to if (isOfficialMusicVideo) authors?.filter { it.endpoint != null }?.mapNotNull { it.name } else null,
                        // "artistIds" to if (isOfficialMusicVideo) authors?.mapNotNull { it.endpoint?.browseId } else null,
                    )
                )
                .build()
        )
        .build()


val Song.asMediaItem: MediaItem
    @UnstableApi
    get() = MediaItem.Builder()
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistsText)
                .setArtworkUri(thumbnailUrl?.toUri())
                .setDurationMs( durationToMillis( durationText.orEmpty() ) )
                .setExtras(
                    bundleOf(
                        "durationText" to durationText,
                        EXPLICIT_BUNDLE_TAG to isExplicit,
                        LOCAL_BUNDLE_TAG to isLocal
                    )
                )
                .build()
        )
        .setMediaId(id)
        .setUri(
            if ( isLocal )
                ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id.toLong()
                )
            else
                id.toUri()
        )
        .setCustomCacheKey(id)
        .build()

val Song.asCleanedMediaItem: MediaItem
    get() = MediaItem.Builder()
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle( cleanTitle() )
                .setArtist( cleanArtistsText() )
                .setArtworkUri( thumbnailUrl?.toUri() )
                .setDurationMs( durationToMillis( durationText.orEmpty() ) )
                .setExtras(
                    bundleOf(
                        "durationText" to durationText,
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
        .build()

val Innertube.ArtistItem.asArtist: Artist
    get() = Artist(
        id = key,
        name = title,
        thumbnailUrl = thumbnail?.url,
        isYoutubeArtist = true
    )

val MediaItem.asSong: Song
    get() = Song (
        id = mediaId,
        title = mediaMetadata.title.toString(),
        artistsText = mediaMetadata.artist.toString(),
        durationText = mediaMetadata.extras?.getString("durationText"),
        thumbnailUrl = mediaMetadata.artworkUri.toString(),
        isExplicit = isExplicit,
        isLocal = mediaMetadata.extras?.getBoolean( LOCAL_BUNDLE_TAG, false ) ?: false
    )

val MediaItem.isVideo: Boolean
    get() = mediaMetadata.extras?.getBoolean("isVideo") == true

val MediaItem.isExplicit: Boolean
    get() = mediaMetadata.extras?.getBoolean( EXPLICIT_BUNDLE_TAG, false ) ?: false

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
fun Uri?.thumbnail(size: Int): Uri? {
    return toString().thumbnail(size)?.toUri()
}

fun formatAsDuration(millis: Long) = DateUtils.formatElapsedTime(millis / 1000).removePrefix("0")
fun durationToMillis(duration: String): Long {
    if( duration.isBlank() || !duration.contains( ":" ) )
        return 0

    val parts = duration.split(":")
    if (parts.size == 3){
        val hours = parts[0].toLong()
        val minutes = parts[1].toLong()
        val seconds = parts[2].toLong()
        return hours * 3600000 + minutes * 60000 + seconds * 1000
    } else {
        val minutes = parts[0].toLong()
        val seconds = parts[1].toLong()
        return minutes * 60000 + seconds * 1000
    }
}

fun durationTextToMillis(duration: String): Long {
    return try {
        durationToMillis(duration)
    } catch (e: Exception) {
        0L
    }
}


fun formatAsTime(millis: Long): String {
    //if (millis == 0L) return ""
    val timePart1 = Duration.ofMillis(millis).toMinutes().minutes
    val timePart2 = Duration.ofMillis(millis).seconds % 60

    return "${timePart1} ${timePart2}s"
}

@JvmName("ResultInnertubeItemsPageCompleted")
suspend fun Result<Innertube.ItemsPage<Innertube.SongItem>?>.completed(
    maxDepth: Int =  Int.MAX_VALUE
): Result<Innertube.ItemsPage<Innertube.SongItem>?> = runCatching {
    val page = getOrThrow()
    val songs = page?.items.orEmpty().toMutableList()
    var continuation = page?.continuation

    var depth = 0
    var continuationsList = arrayOf<String>()
    //continuationsList += continuation.orEmpty()

    println("mediaItem playlist completed() continuation? $continuation")

    while (continuation != null && depth++ < maxDepth) {
        val newSongs = Innertube
            .playlistPage(
                body = ContinuationBody(continuation = continuation)
            )
            ?.getOrNull()
            ?.takeUnless { it.items.isNullOrEmpty() } ?: break

        newSongs.items?.let { songs += it.filter { it !in songs } }
        continuation = newSongs.continuation

        //println("mediaItem loop $depth continuation founded ${continuationsList.contains(continuation)} $continuation")
        if (continuationsList.contains(continuation)) break

        continuationsList += continuation.orEmpty()
        //println("mediaItem loop continuationList size ${continuationsList.size}")
    }

    page?.copy(items = songs, continuation = null)
}.also { it.exceptionOrNull()?.printStackTrace() }

@JvmName("ResultInnertubePlaylistOrAlbumPageCompleted")
suspend fun Result<Innertube.PlaylistOrAlbumPage>.completed(
    maxDepth: Int =  Int.MAX_VALUE
): Result<Innertube.PlaylistOrAlbumPage> = runCatching {
    val page = getOrThrow()
    val songsPage = runCatching {
        page.songsPage
    }.onFailure {
        println("Innertube songsPage PlaylistOrAlbumPage>.completed ${it.stackTraceToString()}")
    }
    val itemsPage = songsPage.completed(maxDepth).getOrThrow()
    page.copy(songsPage = itemsPage)
}.onFailure {
    println("Innertube PlaylistOrAlbumPage>.completed ${it.stackTraceToString()}")
}

//@JvmName("completedPlaylist")
suspend fun Result<LibraryPage?>.completed(): Result<LibraryPage> = runCatching {
    val page = getOrThrow()
    val items = page?.items?.toMutableList()
    var continuation = page?.continuation
    while (continuation != null) {
        val continuationPage = Innertube.libraryContinuation(continuation).getOrNull()
        if (continuationPage != null)
            if (items != null) {
                items += continuationPage.items
            }

        continuation = continuationPage?.continuation
    }
    LibraryPage(
        items = items ?: emptyList(),
        continuation = page?.continuation
    )
}

fun isNetworkConnected( context: Context ): Boolean {
    val cm = context.getSystemService( Context.CONNECTIVITY_SERVICE ) as ConnectivityManager
    val networkInfo = cm.getNetworkCapabilities( cm.activeNetwork )

    return networkInfo != null
            && networkInfo.hasCapability( NetworkCapabilities.NET_CAPABILITY_INTERNET )
            && networkInfo.hasCapability( NetworkCapabilities.NET_CAPABILITY_VALIDATED )
}

fun isNetworkAvailable( context: Context ): Boolean {
    val cm = context.getSystemService( Context.CONNECTIVITY_SERVICE ) as ConnectivityManager
    val networkInfo = cm.getNetworkCapabilities( cm.activeNetwork )

    return networkInfo != null
            && networkInfo.hasCapability( NetworkCapabilities.NET_CAPABILITY_INTERNET )
}

@Composable
fun isNetworkAvailableComposable(): Boolean = isNetworkAvailable( LocalContext.current )

fun getHttpClient() = HttpClient() {
    install(UserAgent) {
        agent = "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0"
    }
    engine {
        ProxyPreferences.preference?.let{
            proxy = getProxy(it)
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

suspend fun downloadSyncedLyrics( song: Song ) {
    val storedLyrics = Database.lyricsTable.findBySongId( song.id ).first()
    if( storedLyrics?.synced != null ) return

    var fetchedLyrics: Lyrics? = null
    LrcLib.lyrics(
        artist = song.cleanArtistsText(),
        title = song.cleanTitle(),
        duration = durationTextToMillis( song.durationText.orEmpty() ).milliseconds
    )?.onSuccess {
        fetchedLyrics = Lyrics(
            songId = song.id,
            fixed = storedLyrics?.fixed,
            synced = it?.text.orEmpty()
        )
    }?.onFailure {
        // Try out different source for lyrics
        KuGou.lyrics(
            artist = song.cleanArtistsText(),
            title = song.cleanTitle(),
            duration = durationTextToMillis( song.durationText.orEmpty() ) / 1000
        )?.onSuccess {
            fetchedLyrics = Lyrics(
                songId = song.id,
                fixed = storedLyrics?.fixed,
                synced = it?.value.orEmpty()
            )
        }
    }

    if( fetchedLyrics != null )
        Database.asyncTransaction {
            lyricsTable.upsert( fetchedLyrics!! )
        }
}

suspend fun addToYtPlaylist(localPlaylistId: Long, position: Int, ytplaylistId: String, mediaItems: List<MediaItem>){
    val mediaItemsChunks = mediaItems.chunked(50)
    mediaItemsChunks.forEachIndexed { index, items ->
        if (mediaItems.size <= 50) {}
        else if (index == 0) {
            Toaster.i(
                "${mediaItems.size} "+appContext().resources.getString(R.string.songs_adding_in_yt)
            )
        } else {
            delay(2000)
        }
        addToPlaylist(ytplaylistId, items.map { it.mediaId })
            .onSuccess {
                Database.playlistTable
                        .findById( localPlaylistId )
                        .first()
                        ?.let {
                            Database.mapIgnore( it, *items.toTypedArray() )
                        }
                if (items.size == 50)
                    Toaster.i( "${mediaItems.size - (index + 1) * 50} Songs Remaining" )
            }
            .onFailure {
                println("YtMusic addToPlaylist (list of size ${items.size}) error: ${it.stackTraceToString()}")
                if(it is ClientRequestException && it.response.status == HttpStatusCode.BadRequest) {
                    Toaster.w( R.string.adding_yt_to_pl_failed )
                    items.forEach { item ->
                        delay(500)
                        addToPlaylist(ytplaylistId, item.mediaId).onFailure {
                            println("YtMusic addToPlaylist (list insert backup) error: ${it.stackTraceToString()}")
                                Toaster.e(
                                    appContext().resources.getString(R.string.songs_add_yt_failed)+"${item.mediaMetadata.title} - ${item.mediaMetadata.artist}"
                                )
                        }.onSuccess {
                            Database.playlistTable
                                    .findById( localPlaylistId )
                                    .first()
                                    ?.let { playlist ->
                                        Database.mapIgnore( playlist, *items.toTypedArray() )
                                    }
                            Toaster.n( "${items.size - (index + 1)} Songs Remaining" )
                        }
                    }
                }
            }
    }

    Toaster.n(
        "${mediaItems.size} "+ appContext().resources.getString(R.string.songs_added_in_yt)
    )
}

suspend fun addSongToYtPlaylist(localPlaylistId: Long, position: Int, ytplaylistId: String, mediaItem: MediaItem){
    if (isYouTubeSyncEnabled()) {
        addToPlaylist(ytplaylistId,mediaItem.mediaId)
            .onSuccess {
                Database.playlistTable.findById( localPlaylistId ).first()?.let {
                    Database.mapIgnore( it, mediaItem )
                }
                Toaster.s( R.string.songs_add_yt_success )
            }
            .onFailure {
                Toaster.e( R.string.songs_add_yt_failed )
            }

    }
}


@OptIn(UnstableApi::class)
suspend fun addToYtLikedSong(mediaItem: MediaItem) {
    if( !isYouTubeSyncEnabled() ) return

    Database.asyncTransaction {
        insertIgnore( mediaItem )
    }

    val isSongLiked = Database.songTable.isLiked( mediaItem.mediaId ).first()

    val isSuccess: Boolean =
        (if( isSongLiked ) likeVideoOrSong( mediaItem.mediaId ) else removelikeVideoOrSong( mediaItem.mediaId )).isSuccess

    val messageId = when {
        isSongLiked && isSuccess -> R.string.songs_liked_yt
        isSongLiked && !isSuccess -> R.string.songs_liked_yt_failed
        !isSongLiked && isSuccess -> R.string.song_unliked_yt
        !isSongLiked && !isSuccess -> R.string.songs_unliked_yt_failed
        else -> throw RuntimeException()
    }

    if( isSuccess ) {
        Database.songTable.toggleLike(mediaItem.mediaId)
        Toaster.s( messageId )
    } else
        Toaster.e( messageId)
}

@OptIn(UnstableApi::class)
suspend fun addToYtLikedSongs(mediaItems: List<MediaItem>){
    if( !isYouTubeSyncEnabled() ) return

    mediaItems.forEachIndexed { index, item ->
        delay(1000)

        likeVideoOrSong( item.mediaId )
            .onSuccess {
                Database.asyncTransaction {
                    insertIgnore( item )
                    songTable.likeState( item.mediaId, true )
                    MyDownloadHelper.autoDownloadWhenLiked(item)

                    Toaster.s(
                        "${index + 1}/${mediaItems.size} " + appContext().resources.getString(R.string.songs_liked_yt)
                    )
                }
            }
            .onFailure {
                Toaster.e( "${index + 1}/${mediaItems.size} " + appContext().resources.getString(R.string.songs_liked_yt_failed) )
            }
    }
}

