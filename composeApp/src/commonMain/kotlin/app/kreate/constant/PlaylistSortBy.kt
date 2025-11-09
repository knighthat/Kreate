package app.kreate.constant

import me.knighthat.kreate.composeapp.generated.resources.Res
import me.knighthat.kreate.composeapp.generated.resources.bookmark_stacks
import me.knighthat.kreate.composeapp.generated.resources.calendar
import me.knighthat.kreate.composeapp.generated.resources.cross_shuffle
import me.knighthat.kreate.composeapp.generated.resources.hourglass_arrow_up
import me.knighthat.kreate.composeapp.generated.resources.sort_date_added
import me.knighthat.kreate.composeapp.generated.resources.sort_listening_time
import me.knighthat.kreate.composeapp.generated.resources.sort_random
import me.knighthat.kreate.composeapp.generated.resources.sort_songs_count
import me.knighthat.kreate.composeapp.generated.resources.sort_title
import me.knighthat.kreate.composeapp.generated.resources.title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource


enum class PlaylistSortBy(
    override val iconId: DrawableResource,
    override val textId: StringResource,
    override val isRandom: Boolean = false
): Drawable, TextView, SortCategory {

    RANDOM(Res.drawable.cross_shuffle, Res.string.sort_random, true),

    TOTAL_PLAY_TIME(Res.drawable.hourglass_arrow_up, Res.string.sort_listening_time),

    TITLE(Res.drawable.title, Res.string.sort_title),

    DATE_ADDED(Res.drawable.calendar, Res.string.sort_date_added),

    SONG_COUNT(Res.drawable.bookmark_stacks, Res.string.sort_songs_count);
}