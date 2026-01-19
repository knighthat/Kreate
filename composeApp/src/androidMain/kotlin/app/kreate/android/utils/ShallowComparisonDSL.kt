package app.kreate.android.utils

import androidx.media3.common.MediaItem
import app.kreate.database.models.Song
import it.fast4x.innertube.Innertube
import me.knighthat.innertube.model.InnertubeSong
import org.jetbrains.annotations.Contract
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract


/**
 * @return `true` if this [MediaItem] and [other] point to the
 * same memory object, or their [MediaItem.mediaId] are the same.
 * `false` otherwise.
 */
@OptIn(ExperimentalContracts::class)
@Contract("null->false")
fun MediaItem.shallowCompare( other: MediaItem? ): Boolean {
    contract {
        returns( true ) implies( other != null )
    }
    return other != null && (other === this || other.mediaId == this.mediaId)
}

/**
 * @return `true` when their ids ([MediaItem.mediaId] & [InnertubeSong.id])
 * are equal. `false` if [other] is null.
 */
@OptIn(ExperimentalContracts::class)
@Contract("null->false")
fun InnertubeSong.shallowCompare( other: MediaItem? ): Boolean {
    contract {
        returns( true ) implies( other != null )
    }

    return other != null && other.mediaId == this.id
}

/**
 * @return `true` when their ids ([MediaItem.mediaId] & [Song.id])
 * are equal. `false` if [other] is null.
 */
@OptIn(ExperimentalContracts::class)
@Contract("null->false")
fun Song.shallowCompare( other: MediaItem? ): Boolean {
    contract {
        returns( true ) implies( other != null )
    }

    return other != null && other.mediaId == this.id
}

/**
 * @return `true` when their ids ([MediaItem.mediaId] & [Innertube.SongItem.key])
 * are equal. `false` if [other] is null.
 */
@OptIn(ExperimentalContracts::class)
@Contract("null->false")
fun Innertube.SongItem.shallowCompare( other: MediaItem? ): Boolean {
    contract {
        returns( true ) implies( other != null )
    }

    return other != null && other.mediaId == this.key
}

/**
 * @return `true` when their ids ([MediaItem.mediaId] & [Innertube.VideoItem.key])
 * are equal. `false` if [other] is null.
 */
@OptIn(ExperimentalContracts::class)
@Contract("null->false")
fun Innertube.VideoItem.shallowCompare( other: MediaItem? ): Boolean {
    contract {
        returns( true ) implies( other != null )
    }

    return other != null && other.mediaId == this.key
}

/**
 * @return `true` when their ids ([MediaItem.mediaId] & [Innertube.Podcast.EpisodeItem.videoId])
 * are equal. `false` if [other] is null.
 */
@OptIn(ExperimentalContracts::class)
@Contract("null->false")
fun Innertube.Podcast.EpisodeItem.shallowCompare( other: MediaItem? ): Boolean {
    contract {
        returns( true ) implies( other != null )
    }

    return other != null && other.mediaId == this.videoId
}
