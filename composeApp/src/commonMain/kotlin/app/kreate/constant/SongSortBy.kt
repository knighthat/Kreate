package app.kreate.constant

import app.kreate.component.Drawable
import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.acute
import kreate.resources.generated.resources.album
import kreate.resources.generated.resources.artist
import kreate.resources.generated.resources.bar_chart
import kreate.resources.generated.resources.calendar_month
import kreate.resources.generated.resources.favorite
import kreate.resources.generated.resources.history_2
import kreate.resources.generated.resources.hourglass
import kreate.resources.generated.resources.shuffle
import kreate.resources.generated.resources.sort_album_title
import kreate.resources.generated.resources.sort_artist
import kreate.resources.generated.resources.sort_date_added
import kreate.resources.generated.resources.sort_date_liked
import kreate.resources.generated.resources.sort_date_played
import kreate.resources.generated.resources.sort_listening_time
import kreate.resources.generated.resources.sort_random
import kreate.resources.generated.resources.sort_relative_listening_time
import kreate.resources.generated.resources.sort_song_duration
import kreate.resources.generated.resources.sort_title
import kreate.resources.generated.resources.text_fields
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource


enum class SongSortBy(
    override val iconId: DrawableResource,
    override val textId: StringResource,
    override val isRandom: Boolean = false
): Drawable, TextView, SortCategory {

    RANDOM(Res.drawable.shuffle, Res.string.sort_random, true),

    TOTAL_PLAY_TIME(Res.drawable.acute, Res.string.sort_listening_time),

    RELATIVE_PLAY_TIME(Res.drawable.bar_chart, Res.string.sort_relative_listening_time),

    TITLE(Res.drawable.text_fields, Res.string.sort_title),

    DATE_ADDED(Res.drawable.calendar_month, Res.string.sort_date_added),

    DATE_PLAYED(Res.drawable.history_2, Res.string.sort_date_played),

    DATE_LIKED(Res.drawable.favorite, Res.string.sort_date_liked),

    ARTIST(Res.drawable.artist, Res.string.sort_artist),

    DURATION(Res.drawable.hourglass, Res.string.sort_song_duration),

    ALBUM(Res.drawable.album, Res.string.sort_album_title);
}