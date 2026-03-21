package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import app.kreate.android.R
import app.kreate.android.service.playback.MediaLibrarySessionCallback
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
        @OptIn(UnstableApi::class)
        get() = when (this) {
            Download    -> MediaLibrarySessionCallback.Command.download
            Favorites   -> MediaLibrarySessionCallback.Command.like
            Repeat      -> MediaLibrarySessionCallback.Command.cycleRepeat
            Shuffle     -> MediaLibrarySessionCallback.Command.toggleShuffle
            Radio       -> MediaLibrarySessionCallback.Command.toggleRadio
            Search      -> MediaLibrarySessionCallback.Command.search
        }
}