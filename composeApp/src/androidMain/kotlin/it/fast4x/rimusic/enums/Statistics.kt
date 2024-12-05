package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import it.fast4x.rimusic.R

enum class StatisticsType(
    @field:DrawableRes override val iconId: Int
): Drawable {

    Today( R.drawable.stat_today ),

    OneWeek( R.drawable.stat_week ),

    OneMonth( R.drawable.stat_month ),

    ThreeMonths( R.drawable.stat_3months ),

    SixMonths( R.drawable.stat_6months ),

    OneYear( R.drawable.stat_year ),

    All( R.drawable.calendar_clear );
}

enum class StatisticsCategory {
    Songs,
    Artists,
    Albums,
    Playlists
}