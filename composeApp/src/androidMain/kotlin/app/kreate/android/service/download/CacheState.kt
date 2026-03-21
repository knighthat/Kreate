package app.kreate.android.service.download

import app.kreate.component.Drawable
import app.kreate.database.models.Format
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import me.knighthat.kreate.composeapp.generated.resources.Res
import me.knighthat.kreate.composeapp.generated.resources.download
import me.knighthat.kreate.composeapp.generated.resources.download_progress
import me.knighthat.kreate.composeapp.generated.resources.downloaded
import org.jetbrains.compose.resources.DrawableResource


interface CacheState {

    /**
     * A logbook that keeps track of submitted [androidx.media3.exoplayer.offline.Download].
     *
     * This should only be used for batch filter, map, etc.
     * If you need to check whether a song is downloaded, use [isDownloaded].
     *
     * Or, to know whether song is cached, downloaded, or not, use
     */
    val downloaded: StateFlow<Map<String, Int>>

    /**
     * @return whether song is fully downloaded to device
     */
    fun isDownloaded( songId: String ): Boolean

    /**
     * Deeper inspection to cache state of the song.
     * Whether it's cached (both partially and fully),
     * downloaded fully, or not cached at all.
     */
    fun stateOf( songId: String ): Flow<State>

    /**
     * Perform series of checks to sync [downloaded]
     * with current cache storage.
     */
    suspend fun sync()

    sealed interface State : Drawable {

        /**
         * Information about data of song cached on system.
         *
         * @param amount of data cached on the system
         * @param isFullyCached if the amount of data stored on system matches
         * what's reported in the Format record.
         */
        class Cached(val amount: Long, val format: Format?, val isFullyCached: Boolean) : State {

            override val iconId: DrawableResource = Res.drawable.download
        }

        /**
         * Song is fully downloaded.
         */
        object Downloaded : State {

            override val iconId: DrawableResource = Res.drawable.downloaded
        }

        /**
         * Song is being downloaded
         */
        object Downloading : State {

            override val iconId: DrawableResource = Res.drawable.download_progress
        }

        /**
         * This is a fallback value, returned when song neither downloaded
         * nor cached, but sometimes it also means not enough information
         * to make a conclusion.
         */
        object Unknown : State {

            override val iconId: DrawableResource = Res.drawable.download
        }
    }
}