package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import app.kreate.component.Drawable
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.all_inclusive
import kreate.resources.generated.resources.download_done
import kreate.resources.generated.resources.favorite_filled
import kreate.resources.generated.resources.mobile_check
import kreate.resources.generated.resources.sync
import kreate.resources.generated.resources.trending_up
import me.knighthat.enums.TextView
import org.jetbrains.compose.resources.DrawableResource

enum class BuiltInPlaylist(
    override val iconId: DrawableResource,
    @field:StringRes override val androidTextId: Int
): Drawable, TextView {

    All(Res.drawable.all_inclusive, R.string.songs),

    Favorites(Res.drawable.favorite_filled, R.string.favorites),

    Offline(Res.drawable.sync, R.string.cached),

    Downloaded(Res.drawable.download_done, R.string.downloaded),

    Top(Res.drawable.trending_up, R.string.playlist_top),

    OnDevice(Res.drawable.mobile_check, R.string.on_device)
}
