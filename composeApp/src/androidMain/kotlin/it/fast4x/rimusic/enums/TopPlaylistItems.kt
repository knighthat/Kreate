package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import it.fast4x.rimusic.R
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
    @field:DrawableRes override val iconId: Int
): Drawable {
    PastDay( 1.days, R.drawable.stat_today ),
    PastWeek( 7.days, R.drawable.stat_week ),
    PastMonth( 30.days, R.drawable.stat_month),
    PastYear( 365.days, R.drawable.stat_year ),
    AllTime( Duration.INFINITE, R.drawable.stat );
}