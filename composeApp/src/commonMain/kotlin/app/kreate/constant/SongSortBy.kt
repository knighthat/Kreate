package app.kreate.constant

import me.knighthat.kreate.composeapp.generated.resources.Res
import me.knighthat.kreate.composeapp.generated.resources.album
import me.knighthat.kreate.composeapp.generated.resources.artist
import me.knighthat.kreate.composeapp.generated.resources.autoplay
import me.knighthat.kreate.composeapp.generated.resources.bar_chart
import me.knighthat.kreate.composeapp.generated.resources.calendar
import me.knighthat.kreate.composeapp.generated.resources.clock_loader
import me.knighthat.kreate.composeapp.generated.resources.cross_shuffle
import me.knighthat.kreate.composeapp.generated.resources.heart
import me.knighthat.kreate.composeapp.generated.resources.hourglass_arrow_up
import me.knighthat.kreate.composeapp.generated.resources.sort_album_title
import me.knighthat.kreate.composeapp.generated.resources.sort_artist
import me.knighthat.kreate.composeapp.generated.resources.sort_date_added
import me.knighthat.kreate.composeapp.generated.resources.sort_date_liked
import me.knighthat.kreate.composeapp.generated.resources.sort_date_played
import me.knighthat.kreate.composeapp.generated.resources.sort_listening_time
import me.knighthat.kreate.composeapp.generated.resources.sort_random
import me.knighthat.kreate.composeapp.generated.resources.sort_relative_listening_time
import me.knighthat.kreate.composeapp.generated.resources.sort_song_duration
import me.knighthat.kreate.composeapp.generated.resources.sort_title
import me.knighthat.kreate.composeapp.generated.resources.title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource


enum class SongSortBy(
    override val iconId: DrawableResource,
    override val textId: StringResource,
    override val isRandom: Boolean = false
): Drawable, TextView, SortCategory {

    RANDOM(Res.drawable.cross_shuffle, Res.string.sort_random, true),

    TOTAL_PLAY_TIME(Res.drawable.hourglass_arrow_up, Res.string.sort_listening_time),

    RELATIVE_PLAY_TIME(Res.drawable.bar_chart, Res.string.sort_relative_listening_time),

    TITLE(Res.drawable.title, Res.string.sort_title),

    DATE_ADDED(Res.drawable.calendar, Res.string.sort_date_added),

    DATE_PLAYED(Res.drawable.autoplay, Res.string.sort_date_played),

    DATE_LIKED(Res.drawable.heart, Res.string.sort_date_liked),

    ARTIST(Res.drawable.artist, Res.string.sort_artist),

    DURATION(Res.drawable.clock_loader, Res.string.sort_song_duration),

    ALBUM(Res.drawable.album, Res.string.sort_album_title);
}