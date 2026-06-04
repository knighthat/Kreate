package it.fast4x.rimusic.enums

import android.provider.MediaStore
import androidx.annotation.StringRes
import app.kreate.android.R
import app.kreate.component.Drawable
import app.kreate.constant.SortCategory
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.album
import kreate.resources.generated.resources.artist
import kreate.resources.generated.resources.event
import kreate.resources.generated.resources.hourglass
import kreate.resources.generated.resources.text_fields
import me.knighthat.enums.TextView
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.compose.resources.DrawableResource

enum class OnDeviceSongSortBy(
    @field:MagicConstant(valuesFromClass = MediaStore.Audio.Media::class)
    val value: String,
    @field:StringRes override val androidTextId: Int,
    override val iconId: DrawableResource,
    override val isRandom: Boolean = false
): TextView, Drawable, SortCategory {

    Title(MediaStore.Audio.Media.TITLE, R.string.sort_title, Res.drawable.text_fields),

    DateAdded(MediaStore.Audio.Media.DATE_ADDED, R.string.sort_date_played, Res.drawable.event),

    Artist(MediaStore.Audio.Media.ARTIST, R.string.sort_artist, Res.drawable.artist),

    Duration(MediaStore.Audio.Media.DURATION, R.string.sort_duration, Res.drawable.hourglass),

    Album(MediaStore.Audio.Media.ALBUM, R.string.sort_album, Res.drawable.album);
}
