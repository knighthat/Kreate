package it.fast4x.rimusic.enums

import app.kreate.component.Drawable
import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.all_inclusive
import kreate.resources.generated.resources.chip_all
import kreate.resources.generated.resources.chip_cached
import kreate.resources.generated.resources.chip_downloaded
import kreate.resources.generated.resources.chip_liked
import kreate.resources.generated.resources.chip_most_played
import kreate.resources.generated.resources.chip_on_device
import kreate.resources.generated.resources.download_done
import kreate.resources.generated.resources.favorite_filled
import kreate.resources.generated.resources.mobile_check
import kreate.resources.generated.resources.sync
import kreate.resources.generated.resources.trending_up
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

enum class BuiltInPlaylist(
    override val iconId: DrawableResource,
    override val textId: StringResource
): Drawable, TextView {

    All(Res.drawable.all_inclusive, Res.string.chip_all),

    Favorites(Res.drawable.favorite_filled, Res.string.chip_liked),

    Offline(Res.drawable.sync, Res.string.chip_cached),

    Downloaded(Res.drawable.download_done, Res.string.chip_downloaded),

    Top(Res.drawable.trending_up, Res.string.chip_most_played),

    OnDevice(Res.drawable.mobile_check, Res.string.chip_on_device)
}
