package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

enum class MaxTopPlaylistItems {
    `10`,
    `20`,
    `30`,
    `40`,
    `50`,
    `70`,
    `90`,
    `100`,
    `150`,
    `200`;

    fun toInt(): Int = this.name.toInt()
}

enum class TopPlaylistPeriod(
    val duration: Duration,
    @field:DrawableRes override val iconId: Int,
    @field:StringRes override val textId: Int
): Drawable, TextView {

    PastDay( 1.days, R.drawable.stat_today, R.string.past_day ),

    PastWeek( 7.days, R.drawable.stat_week, R.string.past_week ),

    PastMonth( 30.days, R.drawable.stat_month, R.string.past_month ),

    PastYear( 365.days, R.drawable.stat_year, R.string.past_year ),

    AllTime( Duration.INFINITE, R.drawable.stat, R.string.all_time );
}