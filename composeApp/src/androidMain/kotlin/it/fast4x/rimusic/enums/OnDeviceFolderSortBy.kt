package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import app.kreate.component.Drawable
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.artist
import kreate.resources.generated.resources.hourglass
import kreate.resources.generated.resources.text_fields
import me.knighthat.enums.TextView
import org.jetbrains.compose.resources.DrawableResource

enum class OnDeviceFolderSortBy(
    @field:StringRes override val androidTextId: Int,
    override val iconId: DrawableResource
): TextView, Drawable {

    Title(R.string.sort_title, Res.drawable.text_fields),

    Artist(R.string.sort_artist, Res.drawable.artist),

    Duration(R.string.sort_duration, Res.drawable.hourglass);
}
