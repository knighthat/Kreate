package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class PlayEventsType(
    @field:StringRes override val androidTextId: Int,
    @field:DrawableRes override val androidIconId: Int
): TextView, Drawable {

    MostPlayed( R.string.by_most_played_song, R.drawable.chevron_up ),
    LastPlayed( R.string.by_last_played_song, R.drawable.chevron_down ),
    CasualPlayed( R.string.by_casual_played_song, R.drawable.random );
}