package it.fast4x.rimusic.enums

import android.provider.MediaStore
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.kreate.android.R
import app.kreate.constant.SortCategory
import me.knighthat.enums.TextView
import org.intellij.lang.annotations.MagicConstant

enum class OnDeviceSongSortBy(
    @field:MagicConstant(valuesFromClass = MediaStore.Audio.Media::class)
    val value: String,
    @field:StringRes override val androidTextId: Int,
    @field:DrawableRes override val androidIconId: Int,
    override val isRandom: Boolean = false
): TextView, Drawable, SortCategory {

    Title( MediaStore.Audio.Media.TITLE, R.string.sort_title, R.drawable.text ),

    DateAdded( MediaStore.Audio.Media.DATE_ADDED, R.string.sort_date_played, R.drawable.calendar ),

    Artist( MediaStore.Audio.Media.ARTIST, R.string.sort_artist, R.drawable.artist ),

    Duration( MediaStore.Audio.Media.DURATION, R.string.sort_duration, R.drawable.time ),

    Album( MediaStore.Audio.Media.ALBUM, R.string.sort_album, R.drawable.album );
}
