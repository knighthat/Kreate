package it.fast4x.rimusic.utils


import android.annotation.SuppressLint
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
import app.kreate.android.utils.innertube.toMediaItem
import app.kreate.database.models.Song
import app.kreate.util.toDuration
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.enums.DurationInMinutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.utils.Toaster
import org.jetbrains.annotations.Blocking


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

fun <T> Player.forcePlay(
    item: T,
    toMediaItem: T.() -> MediaItem,
    getDuration: (T) -> Long
) =
    CoroutineScope(Dispatchers.Default).launch {
        val mediaItems = filterDurationAndLimit( listOf(item), toMediaItem, getDuration )
        if( mediaItems.isEmpty() ) {
            Toaster.w( R.string.warning_songs_duration_exceeds_limit )
            return@launch
        }

        withContext( Dispatchers.Main ) {
            setMediaItems( mediaItems, true )
            playWhenReady()
        }
    }

fun Player.forcePlay( song: Song ) {
    forcePlay( song, Song::asCleanedMediaItem ) {
        it.durationText.toDuration().inWholeMilliseconds
    }
}

@UnstableApi
fun Player.forcePlay( song: Innertube.SongItem ) {
    forcePlay( song, Innertube.SongItem::asMediaItem ) {
        it.durationText.toDuration().inWholeMilliseconds
    }
}

@UnstableApi
fun Player.forcePlay( video: Innertube.VideoItem ) {
    forcePlay( video, Innertube.VideoItem::asMediaItem ) {
        it.durationText.toDuration().inWholeMilliseconds
    }
}

fun Player.forcePlay( song: InnertubeSong ) {
    forcePlay( song, InnertubeSong::toMediaItem ) {
        it.durationText.toDuration().inWholeMilliseconds
    }
}

fun Player.forcePlay(mediaItem: MediaItem) {
    forcePlay( mediaItem, { this } ) {
        it.mediaMetadata.durationMs ?: 0L
    }
}

fun Player.playVideo(mediaItem: MediaItem) {
    setMediaItem(mediaItem.cleaned, true)
    pause()
}

fun Player.playAtIndex(mediaItemIndex: Int) {
    seekToDefaultPosition( mediaItemIndex )
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
        it.durationText.toDuration().inWholeMilliseconds
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
        it.durationString.toDuration().inWholeMilliseconds
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

@MainThread
fun <T> Player.addNext(
    item: T,
    toMediaItem: T.() -> MediaItem,
    getDuration: (T) -> Long
) = enqueue( item, currentMediaItemIndex + 1, toMediaItem, getDuration )

@MainThread
fun Player.addNext( mediaItem: MediaItem ) {
    addNext( mediaItem, { this }, { it.mediaMetadata.durationMs ?: 0L } )
}

@MainThread
fun Player.addNext( song: Song ) {
    addNext( song, Song::asCleanedMediaItem ) {
        it.durationText.toDuration().inWholeMilliseconds
    }
}

@UnstableApi
@MainThread
fun Player.addNext( video: Innertube.VideoItem ) {
    addNext( video, Innertube.VideoItem::asMediaItem ) {
        it.durationText.toDuration().inWholeMilliseconds
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
        it.durationText.toDuration().inWholeMilliseconds
    }
}

fun <T> Player.enqueue(
    item: T,
    index: Int,
    toMediaItem: T.() -> MediaItem,
    getDuration: (T) -> Long
) =
    CoroutineScope(Dispatchers.Default).launch {
        val mediaItems = filterDurationAndLimit( listOf(item), toMediaItem, getDuration )
        if( mediaItems.isEmpty() ) {
            Toaster.w( R.string.warning_songs_duration_exceeds_limit )
            return@launch
        }

        withContext( Dispatchers.Main ) {
            if( playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED ) {
                setMediaItems( mediaItems, true )
                playWhenReady()
            } else
                addMediaItems( index, mediaItems )
        }
    }

@MainThread
fun <T> Player.enqueue(
    item: T,
    toMediaItem: T.() -> MediaItem,
    getDuration: (T) -> Long
) = enqueue( item, mediaItemCount + 1, toMediaItem, getDuration )

@MainThread
fun Player.enqueue( song: Song ) {
    enqueue( song, Song::asCleanedMediaItem ) {
        it.durationText.toDuration().inWholeMilliseconds
    }
}

@UnstableApi
@MainThread
fun Player.enqueue( video: Innertube.VideoItem ) {
    enqueue( video, Innertube.VideoItem::asMediaItem ) {
        it.durationText.toDuration().inWholeMilliseconds
    }
}

@MainThread
fun Player.enqueue( mediaItem: MediaItem ) {
    enqueue( mediaItem, { this }, { it.mediaMetadata.durationMs ?: 0L } )
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
        it.durationText.toDuration().inWholeMilliseconds
    }
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
