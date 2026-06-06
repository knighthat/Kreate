package it.fast4x.rimusic.enums

import app.kreate.component.Drawable
import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.artist
import kreate.resources.generated.resources.hourglass
import kreate.resources.generated.resources.sort_artist
import kreate.resources.generated.resources.sort_title
import kreate.resources.generated.resources.sort_total_duration
import kreate.resources.generated.resources.text_fields
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

enum class OnDeviceFolderSortBy(
    override val textId: StringResource,
    override val iconId: DrawableResource
): TextView, Drawable {

    Title(Res.string.sort_title, Res.drawable.text_fields),

    Artist(Res.string.sort_artist, Res.drawable.artist),

    Duration(Res.string.sort_total_duration, Res.drawable.hourglass);
}
