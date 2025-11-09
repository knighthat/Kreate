package app.kreate.constant

import app.kreate.component.Drawable
import app.kreate.component.TextView
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
import me.knighthat.kreate.composeapp.generated.resources.position
import me.knighthat.kreate.composeapp.generated.resources.recent_actors
import me.knighthat.kreate.composeapp.generated.resources.sort_album_and_artist
import me.knighthat.kreate.composeapp.generated.resources.sort_album_title
import me.knighthat.kreate.composeapp.generated.resources.sort_album_year
import me.knighthat.kreate.composeapp.generated.resources.sort_artist
import me.knighthat.kreate.composeapp.generated.resources.sort_date_added
import me.knighthat.kreate.composeapp.generated.resources.sort_date_liked
import me.knighthat.kreate.composeapp.generated.resources.sort_date_played
import me.knighthat.kreate.composeapp.generated.resources.sort_listening_time
import me.knighthat.kreate.composeapp.generated.resources.sort_position
import me.knighthat.kreate.composeapp.generated.resources.sort_random
import me.knighthat.kreate.composeapp.generated.resources.sort_relative_listening_time
import me.knighthat.kreate.composeapp.generated.resources.sort_song_duration
import me.knighthat.kreate.composeapp.generated.resources.sort_title
import me.knighthat.kreate.composeapp.generated.resources.title
import me.knighthat.kreate.composeapp.generated.resources.year
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource


enum class PlaylistSongSortBy(
    override val iconId: DrawableResource,
    override val textId: StringResource,
    override val isRandom: Boolean = false
): Drawable, TextView, SortCategory {

    RANDOM(Res.drawable.cross_shuffle, Res.string.sort_random, true),

    ALBUM(Res.drawable.album, Res.string.sort_album_title),

    ALBUM_YEAR(Res.drawable.year, Res.string.sort_album_year),

    ARTIST(Res.drawable.artist, Res.string.sort_artist),

    ARTIST_AND_ALBUM(Res.drawable.recent_actors, Res.string.sort_album_and_artist),

    DATE_PLAYED(Res.drawable.autoplay, Res.string.sort_date_played),

    TOTAL_PLAY_TIME(Res.drawable.hourglass_arrow_up, Res.string.sort_listening_time),

    RELATIVE_PLAY_TIME(Res.drawable.bar_chart, Res.string.sort_relative_listening_time),

    POSITION(Res.drawable.position, Res.string.sort_position),

    TITLE(Res.drawable.title, Res.string.sort_title),

    DURATION(Res.drawable.clock_loader, Res.string.sort_song_duration),

    DATE_LIKED(Res.drawable.heart, Res.string.sort_date_liked),

    DATE_ADDED(Res.drawable.calendar, Res.string.sort_date_added);
}