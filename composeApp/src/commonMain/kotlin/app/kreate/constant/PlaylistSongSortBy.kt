package app.kreate.constant

import app.kreate.component.Drawable
import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.acute
import kreate.resources.generated.resources.album
import kreate.resources.generated.resources.artist
import kreate.resources.generated.resources.avg_time
import kreate.resources.generated.resources.calendar_month
import kreate.resources.generated.resources.favorite
import kreate.resources.generated.resources.history_2
import kreate.resources.generated.resources.hourglass
import kreate.resources.generated.resources.position
import kreate.resources.generated.resources.recent_actors
import kreate.resources.generated.resources.shuffle
import kreate.resources.generated.resources.sort_album_and_artist
import kreate.resources.generated.resources.sort_album_title
import kreate.resources.generated.resources.sort_album_year
import kreate.resources.generated.resources.sort_artist
import kreate.resources.generated.resources.sort_date_added
import kreate.resources.generated.resources.sort_date_liked
import kreate.resources.generated.resources.sort_date_played
import kreate.resources.generated.resources.sort_listening_time
import kreate.resources.generated.resources.sort_position
import kreate.resources.generated.resources.sort_random
import kreate.resources.generated.resources.sort_relative_listening_time
import kreate.resources.generated.resources.sort_song_duration
import kreate.resources.generated.resources.sort_title
import kreate.resources.generated.resources.text_fields
import kreate.resources.generated.resources.year
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource


enum class PlaylistSongSortBy(
    override val iconId: DrawableResource,
    override val textId: StringResource,
    override val isRandom: Boolean = false
): Drawable, TextView, SortCategory {

    RANDOM(Res.drawable.shuffle, Res.string.sort_random, true),

    ALBUM(Res.drawable.album, Res.string.sort_album_title),

    ALBUM_YEAR(Res.drawable.year, Res.string.sort_album_year),

    ARTIST(Res.drawable.artist, Res.string.sort_artist),

    ARTIST_AND_ALBUM(Res.drawable.recent_actors, Res.string.sort_album_and_artist),

    DATE_PLAYED(Res.drawable.history_2, Res.string.sort_date_played),

    TOTAL_PLAY_TIME(Res.drawable.acute, Res.string.sort_listening_time),

    RELATIVE_PLAY_TIME(Res.drawable.avg_time, Res.string.sort_relative_listening_time),

    POSITION(Res.drawable.position, Res.string.sort_position),

    TITLE(Res.drawable.text_fields, Res.string.sort_title),

    DURATION(Res.drawable.hourglass, Res.string.sort_song_duration),

    DATE_LIKED(Res.drawable.favorite, Res.string.sort_date_liked),

    DATE_ADDED(Res.drawable.calendar_month, Res.string.sort_date_added);
}