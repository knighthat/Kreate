package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.media3.session.SessionCommand
import app.kreate.android.R
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandSearch
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandStartRadio
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandToggleDownload
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandToggleLike
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandToggleRepeatMode
import it.fast4x.rimusic.service.modern.MediaSessionConstants.CommandToggleShuffle
import me.knighthat.enums.TextView

enum class NotificationButtons(
    @field:StringRes override val androidTextId: Int,
    @field:DrawableRes override val androidIconId: Int
): TextView, Drawable {

    Download( R.string.download, R.drawable.download ),

    Favorites( R.string.favorites, R.drawable.heart_outline ),

    Repeat( R.string.repeat, R.drawable.repeat ),

    Shuffle( R.string.shuffle, R.drawable.shuffle ),

    Radio( R.string.start_radio, R.drawable.radio ),

    Search( android.R.string.search_go, R.drawable.search );

    val sessionCommand: SessionCommand
    get() = when (this) {
        Download -> CommandToggleDownload
        Favorites -> CommandToggleLike
        Repeat -> CommandToggleRepeatMode
        Shuffle -> CommandToggleShuffle
        Radio -> CommandStartRadio
        Search -> CommandSearch
    }
}