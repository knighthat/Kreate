package app.kreate.constant

import app.kreate.component.Drawable
import app.kreate.component.TextView
import me.knighthat.kreate.composeapp.generated.resources.Res
import me.knighthat.kreate.composeapp.generated.resources.calendar
import me.knighthat.kreate.composeapp.generated.resources.cross_shuffle
import me.knighthat.kreate.composeapp.generated.resources.sort_date_added
import me.knighthat.kreate.composeapp.generated.resources.sort_random
import me.knighthat.kreate.composeapp.generated.resources.sort_title
import me.knighthat.kreate.composeapp.generated.resources.title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource


enum class ArtistSortBy(
    override val iconId: DrawableResource,
    override val textId: StringResource,
    override val isRandom: Boolean = false
): Drawable, TextView, SortCategory {

    RANDOM(Res.drawable.cross_shuffle, Res.string.sort_random, true),

    TITLE(Res.drawable.title, Res.string.sort_title),

    DATE_ADDED(Res.drawable.calendar, Res.string.sort_date_added);
}