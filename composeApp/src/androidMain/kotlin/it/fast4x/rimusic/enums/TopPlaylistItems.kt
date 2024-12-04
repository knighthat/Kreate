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

    val number: Long
        get() = when (this) {
            `10` -> 10
            `20` -> 20
            `30` -> 30
            `40` -> 40
            `50` -> 50
            `70` -> 70
            `90` -> 90
            `100` -> 100
            `150` -> 150
            `200` -> 200
        }

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