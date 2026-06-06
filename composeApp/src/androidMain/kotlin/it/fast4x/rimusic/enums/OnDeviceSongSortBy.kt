package it.fast4x.rimusic.enums

import android.provider.MediaStore
import app.kreate.component.Drawable
import app.kreate.component.TextView
import app.kreate.constant.SortCategory
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.album
import kreate.resources.generated.resources.artist
import kreate.resources.generated.resources.event
import kreate.resources.generated.resources.hourglass
import kreate.resources.generated.resources.sort_album_title
import kreate.resources.generated.resources.sort_artist
import kreate.resources.generated.resources.sort_date_played
import kreate.resources.generated.resources.sort_song_duration
import kreate.resources.generated.resources.sort_title
import kreate.resources.generated.resources.text_fields
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

enum class OnDeviceSongSortBy(
    @field:MagicConstant(valuesFromClass = MediaStore.Audio.Media::class)
    val value: String,
    override val textId: StringResource,
    override val iconId: DrawableResource,
    override val isRandom: Boolean = false
): TextView, Drawable, SortCategory {

    Title(MediaStore.Audio.Media.TITLE, Res.string.sort_title, Res.drawable.text_fields),

    DateAdded(MediaStore.Audio.Media.DATE_ADDED, Res.string.sort_date_played, Res.drawable.event),

    Artist(MediaStore.Audio.Media.ARTIST, Res.string.sort_artist, Res.drawable.artist),

    Duration(MediaStore.Audio.Media.DURATION, Res.string.sort_song_duration, Res.drawable.hourglass),

    Album(MediaStore.Audio.Media.ALBUM, Res.string.sort_album_title, Res.drawable.album);
}
