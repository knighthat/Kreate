package it.fast4x.rimusic.enums

import app.kreate.component.Drawable
import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.action_change_repeat
import kreate.resources.generated.resources.action_download
import kreate.resources.generated.resources.action_like_dislike
import kreate.resources.generated.resources.action_search
import kreate.resources.generated.resources.action_start_radio
import kreate.resources.generated.resources.action_toggle_shuffle
import kreate.resources.generated.resources.cell_tower
import kreate.resources.generated.resources.download
import kreate.resources.generated.resources.favorite_filled
import kreate.resources.generated.resources.repeat
import kreate.resources.generated.resources.search
import kreate.resources.generated.resources.shuffle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

enum class NotificationButtons(
    override val textId: StringResource,
    override val iconId: DrawableResource
): TextView, Drawable {

    Download(Res.string.action_download, Res.drawable.download),

    Favorites(Res.string.action_like_dislike, Res.drawable.favorite_filled),

    Repeat(Res.string.action_change_repeat, Res.drawable.repeat),

    Shuffle(Res.string.action_toggle_shuffle, Res.drawable.shuffle),

    Radio(Res.string.action_start_radio, Res.drawable.cell_tower),

    Search(Res.string.action_search, Res.drawable.search);
}