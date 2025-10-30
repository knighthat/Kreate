package it.fast4x.rimusic.utils


import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import app.kreate.android.Preferences
import app.kreate.android.R
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.enums.DurationInMinutes
import it.fast4x.rimusic.models.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.knighthat.utils.Toaster
import org.jetbrains.annotations.Blocking
import timber.log.Timber


private fun Player.playWhenReady() {
    prepare()
    restoreGlobalVolume()
    playWhenReady = true
}

@Blocking
private fun <T> filterDurationAndLimit(
    items: List<T>,
    toMediaItem: T.() -> MediaItem,
    getDuration: (T) -> Long
): List<MediaItem> {
    val durationLimit by Preferences.LIMIT_SONGS_WITH_DURATION
    val maxCount by Preferences.MAX_NUMBER_OF_SONG_IN_QUEUE

    val result = mutableListOf<MediaItem>()
    for( s in items ) {
        if( result.size > maxCount.toInt() )
            break

        val durationMillis = getDuration(s)
        if( durationLimit != DurationInMinutes.Disabled
            && durationMillis > durationLimit.asMillis
        ) continue

        val cleanedMediaItem = s.toMediaItem()
        result.add( cleanedMediaItem )
    }

    return result.toList()      // Make it immutable
}

var GlobalVolume: Float = 0.5f

fun Player.restoreGlobalVolume() {
    volume = GlobalVolume
}

fun Player.setGlobalVolume(v: Float) {
    GlobalVolume = v
}

fun Player.isNowPlaying(mediaId: String): Boolean {
    return mediaId == currentMediaItem?.mediaId
}

val Player.currentWindow: Timeline.Window?
    get() = if (mediaItemCount == 0) null else currentTimeline.getWindow(currentMediaItemIndex, Timeline.Window())

val Timeline.mediaItems: List<MediaItem>
    get() = List(windowCount) {
        getWindow(it, Timeline.Window()).mediaItem
    }

inline val Timeline.windows: List<Timeline.Window>
    get() = List(windowCount) {
        getWindow(it, Timeline.Window())
    }

val Player.shouldBePlaying: Boolean
    get() = !(playbackState == Player.STATE_ENDED || !playWhenReady)

fun Player.shuffleQueue() {
    val mediaItems = currentTimeline.mediaItems.toMutableList().apply { removeAt(currentMediaItemIndex) }
    if (currentMediaItemIndex > 0) removeMediaItems(0, currentMediaItemIndex)
    if (currentMediaItemIndex < mediaItemCount - 1) removeMediaItems(currentMediaItemIndex + 1, mediaItemCount)
    addMediaItems(mediaItems.shuffled())
}

fun Player.forcePlay(mediaItem: MediaItem) {
    setMediaItem(mediaItem.cleaned, true)
    playWhenReady()
}

fun Player.playVideo(mediaItem: MediaItem) {
    setMediaItem(mediaItem.cleaned, true)
    pause()
}

fun Player.playAtIndex(mediaItemIndex: Int) {
    seekTo(mediaItemIndex, C.TIME_UNSET)
    playWhenReady()
}

fun <T> Player.forcePlayAtIndex(
    items: List<T>,
    index: Int,
    toMediaItem: T.() -> MediaItem,
    getDuration: (T) -> Long
) =
    CoroutineScope(Dispatchers.Default).launch {
        val realList = items.subList( index.coerceAtLeast(0), items.size )
        val mediaItems = filterDurationAndLimit( realList, toMediaItem, getDuration )
        if( mediaItems.isEmpty() ) {
            Toaster.w( R.string.warning_no_valid_songs )
            return@launch
        }

        // [index] equals to -1 means whatever first
        val startIndex = if( index > -1 ) {
            val item = items[index].toMediaItem()
            // This index should be 0 in most cases
            mediaItems.indexOfFirst {
                it.mediaId == item.mediaId
            }
        } else 0

        // When selected item is no longer in the list,
        // we only warn user and do nothing.
        if( startIndex == -1 ) {
            Toaster.w( R.string.warning_songs_duration_exceeds_limit )
            return@launch
        }

        // Let user know how many songs were excluded.
        if( mediaItems.size < items.size ) {
            val excludedByDurationLimit = items.size - mediaItems.size
            Toaster.w(
                R.string.warning_num_songs_exlucded_because_duration_limit,
                appContext().resources.getQuantityString(
                    R.plurals.song,
                    excludedByDurationLimit,
                    excludedByDurationLimit
                )
            )
        }

        withContext( Dispatchers.Main ) {
            setMediaItems( mediaItems, startIndex, C.INDEX_UNSET.toLong() )
            playWhenReady()
        }
    }

@JvmName("forcePlayMediaItemsAtIndex")
@SuppressLint("Range")
@UnstableApi
fun Player.forcePlayAtIndex(mediaItems: List<MediaItem>, mediaItemIndex: Int) {
    if ( mediaItems.isEmpty() ) return

    // This will prevent UI from freezing up during conversion
    CoroutineScope( Dispatchers.Default ).launch {
        val cleanedMediaItems = mediaItems.fastMap( MediaItem::cleaned ).fastDistinctBy( MediaItem::mediaId )

        runBlocking( Dispatchers.Main ) {
            setMediaItems( cleanedMediaItems, mediaItemIndex, C.TIME_UNSET )
            playWhenReady()
        }
    }
}

@JvmName("forcePlaySongsAtIndex")
fun Player.forcePlayAtIndex( songs: List<Song>, startIndex: Int ) {
    forcePlayAtIndex( songs, startIndex, Song::asCleanedMediaItem ) {
        durationToMillis( it.durationText.orEmpty() )
    }
}

@JvmName("playSongsShuffled")
fun Player.playShuffled( songs: List<Song> ) {
    if( songs.isEmpty() ) {
        Toaster.w( R.string.warning_nothing_to_shuffle )
        return
    }

    val shuffled = songs.shuffled()
    forcePlayAtIndex( shuffled, -1 )
}

@JvmName("playPodcastEpisodeShuffled")
@UnstableApi
fun Player.playShuffled(episodes: List<Innertube.Podcast.EpisodeItem> ) {
    if( episodes.isEmpty() ) {
        Toaster.w( R.string.warning_nothing_to_shuffle )
        return
    }

    val shuffled = episodes.shuffled()
    forcePlayAtIndex( shuffled, -1, Innertube.Podcast.EpisodeItem::asMediaItem ) {
        durationToMillis( it.durationString.orEmpty() )
    }
}

fun Player.playNext() {
    seekToNextMediaItem()
    //seekToNext()
    playWhenReady()
}

fun Player.playPrevious() {
    seekToPreviousMediaItem()
    //seekToPrevious()
    playWhenReady()
}

/**
 * If there's no [MediaItem] before this, or, when [Player.getCurrentPosition]
 * exceeded [Preferences.SMART_REWIND]'s value, player will be automatically
 * starts at the beginning.
 *
 * Else, it will move to previous [MediaItem]
 */
fun Player.smartRewind() =
    if( !hasPreviousMediaItem() || currentPosition > (Preferences.SMART_REWIND.value * 1000) )
        seekTo( 0 )
    else
        seekToPreviousMediaItem()

@UnstableApi
fun Player.addNext( mediaItem: MediaItem ) {
    if (excludeMediaItem(mediaItem)) return

    val itemIndex = findMediaItemIndexById(mediaItem.mediaId)
    if (itemIndex >= 0) removeMediaItem(itemIndex)

    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        forcePlay(mediaItem)
    } else {
        addMediaItem(currentMediaItemIndex + 1, mediaItem.cleaned)
    }
}

fun <T> Player.addNext(
    items: List<T>,
    toMediaItem: T.() -> MediaItem,
    getDuration: (T) -> Long
) = enqueue( items, currentMediaItemIndex + 1, toMediaItem, getDuration )

@JvmName("addMediaItemsNext")
fun Player.addNext( songs: List<Song> ) {
    addNext( songs, Song::asCleanedMediaItem ) {
        durationToMillis( it.durationText.orEmpty() )
    }
}


fun Player.enqueue( mediaItem: MediaItem ) {
     if ( excludeMediaItem(mediaItem) ) return

    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        forcePlay(mediaItem)
    } else {
        addMediaItem(mediaItemCount, mediaItem.cleaned)
    }
}

fun <T> Player.enqueue(
    items: List<T>,
    index: Int,
    toMediaItem: T.() -> MediaItem,
    getDuration: (T) -> Long
) =
    CoroutineScope(Dispatchers.Default).launch {
        val mediaItems = filterDurationAndLimit( items, toMediaItem, getDuration )
        if( mediaItems.isEmpty() ) {
            Toaster.w( R.string.warning_no_valid_songs )
            return@launch
        }

        // Let user know how many songs were excluded.
        if( mediaItems.size < items.size ) {
            val excludedByDurationLimit = items.size - mediaItems.size
            Toaster.w(
                R.string.warning_num_songs_exlucded_because_duration_limit,
                appContext().resources.getQuantityString(
                    R.plurals.song,
                    excludedByDurationLimit,
                    excludedByDurationLimit
                )
            )
        }

        val (mediaId, queue) = withContext( Dispatchers.Main ) {
            if ( playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED ) {
                setMediaItems( mediaItems, true )
                playWhenReady()

                null to emptyList()
            } else
                currentMediaItem?.mediaId to this@enqueue.mediaItems.toList()       // Make a copy because next steps access it outside of Main thread
        }
        if( mediaId == null ) return@launch

        // This step goes through the queue from the bottom and remove
        // MediaItems that are present in [mediaItems] list
        val addingIds = mediaItems.fastMap( MediaItem::mediaId ).toSet()
        for( i in queue.lastIndex downTo 0 ) {
            // Remove non-playing songs in the queue
            val queueMediaId = queue[i].mediaId
            if( queueMediaId !in addingIds || queueMediaId == mediaId )
                continue

            withContext( Dispatchers.Main ) {
                removeMediaItem( i )
            }
        }

        val realList = mediaItems.fastFilter { it.mediaId != mediaId }
        withContext( Dispatchers.Main ) {
            addMediaItems( index, realList )
        }
    }

@MainThread
fun <T> Player.enqueue(
    items: List<T>,
    toMediaItem: T.() -> MediaItem,
    getDuration: (T) -> Long
) = enqueue( items, mediaItemCount + 1, toMediaItem, getDuration )

@JvmName("enqueueSongs")
fun Player.enqueue( songs: List<Song> ) {
    enqueue( songs, Song::asCleanedMediaItem ) {
        durationToMillis( it.durationText.orEmpty() )
    }
}

fun Player.findMediaItemIndexById(mediaId: String): Int {
    for (i in currentMediaItemIndex until mediaItemCount) {
        if (getMediaItemAt(i).mediaId == mediaId) {
            return i
        }
    }
    return -1
}

fun Player.excludeMediaItems(mediaItems: List<MediaItem>, context: Context): List<MediaItem> {
    var filteredMediaItems = mediaItems
    runCatching {
        val excludeSongWithDurationLimit by Preferences.LIMIT_SONGS_WITH_DURATION

        if (excludeSongWithDurationLimit != DurationInMinutes.Disabled) {
            filteredMediaItems = mediaItems.filter {
                it.mediaMetadata.extras?.getString("durationText")?.let { it1 ->
                    durationTextToMillis(it1)
                }!! < excludeSongWithDurationLimit.asMillis
            }

            val excludedSongs = mediaItems.size - filteredMediaItems.size
            if (excludedSongs > 0)
                Toaster.n( R.string.message_excluded_s_songs, arrayOf( excludedSongs ) )
        }
    }.onFailure {
        Timber.e(it.message)
    }

    return filteredMediaItems
}
fun Player.excludeMediaItem(mediaItem: MediaItem): Boolean {
    runCatching {
        val excludeSongWithDurationLimit by Preferences.LIMIT_SONGS_WITH_DURATION
        if (excludeSongWithDurationLimit != DurationInMinutes.Disabled) {
            val excludedSong = mediaItem.mediaMetadata.extras?.getString("durationText")?.let { it1 ->
                    durationTextToMillis(it1)
                }!! <= excludeSongWithDurationLimit.asMillis

            if (excludedSong)
                Toaster.n( R.string.message_excluded_s_songs, arrayOf( 1 ) )

            return excludedSong
        }
    }.onFailure {
        //it.printStackTrace()
        Timber.e(it.message)
        return false
    }

    return false

}

val Player.mediaItems: List<MediaItem>
    get() = object : AbstractList<MediaItem>() {
        override val size: Int
            get() = mediaItemCount

        override fun get(index: Int): MediaItem = getMediaItemAt(index)
    }

fun Player.toggleRepeatMode() {
    repeatMode = when (repeatMode) {
        REPEAT_MODE_OFF -> REPEAT_MODE_ALL
        REPEAT_MODE_ALL -> REPEAT_MODE_ONE
        REPEAT_MODE_ONE -> REPEAT_MODE_OFF
        else -> throw IllegalStateException()
    }
}

fun Player.toggleShuffleMode() {
    shuffleModeEnabled = !shuffleModeEnabled
}
