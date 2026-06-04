package app.kreate.constant

import app.kreate.component.Drawable
import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.artist
import kreate.resources.generated.resources.bookmark_stacks
import kreate.resources.generated.resources.calendar_month
import kreate.resources.generated.resources.hourglass
import kreate.resources.generated.resources.shuffle
import kreate.resources.generated.resources.sort_album_year
import kreate.resources.generated.resources.sort_artist
import kreate.resources.generated.resources.sort_date_added
import kreate.resources.generated.resources.sort_random
import kreate.resources.generated.resources.sort_songs_count
import kreate.resources.generated.resources.sort_title
import kreate.resources.generated.resources.sort_total_duration
import kreate.resources.generated.resources.text_fields
import kreate.resources.generated.resources.year
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource


enum class AlbumSortBy(
    override val iconId: DrawableResource,
    override val textId: StringResource,
    override val isRandom: Boolean = false
): Drawable, TextView, SortCategory {

    RANDOM(Res.drawable.shuffle, Res.string.sort_random, true),

    TITLE(Res.drawable.text_fields, Res.string.sort_title),

    YEAR(Res.drawable.year, Res.string.sort_album_year),

    DATE_ADDED(Res.drawable.calendar_month, Res.string.sort_date_added),

    ARTIST(Res.drawable.artist, Res.string.sort_artist),

    SONGS_COUNT(Res.drawable.bookmark_stacks, Res.string.sort_songs_count),

    TOTAL_DURATION(Res.drawable.hourglass, Res.string.sort_total_duration);
}