package app.kreate.constant

import app.kreate.component.Drawable
import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.calendar_month
import kreate.resources.generated.resources.shuffle
import kreate.resources.generated.resources.sort_date_added
import kreate.resources.generated.resources.sort_random
import kreate.resources.generated.resources.sort_title
import kreate.resources.generated.resources.text_fields
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource


enum class ArtistSortBy(
    override val iconId: DrawableResource,
    override val textId: StringResource,
    override val isRandom: Boolean = false
): Drawable, TextView, SortCategory {

    RANDOM(Res.drawable.shuffle, Res.string.sort_random, true),

    TITLE(Res.drawable.text_fields, Res.string.sort_title),

    DATE_ADDED(Res.drawable.calendar_month, Res.string.sort_date_added);
}