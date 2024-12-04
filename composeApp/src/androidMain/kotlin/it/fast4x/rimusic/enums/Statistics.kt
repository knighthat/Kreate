package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class StatisticsType(
    @field:DrawableRes override val iconId: Int,
    @field:StringRes override val textId: Int
): Drawable, TextView {

    Today( R.drawable.stat_today, R.string.today ),

    OneWeek( R.drawable.stat_week, R.string._1_week ),

    OneMonth( R.drawable.stat_month, R.string._1_month ),

    ThreeMonths( R.drawable.stat_3months, R.string._3_month ),

    SixMonths( R.drawable.stat_6months, R.string._6_month ),

    OneYear( R.drawable.stat_year, R.string._1_year ),

    All( R.drawable.calendar_clear, R.string.all );
}

enum class StatisticsCategory(
    @field:StringRes override val textId: Int
): TextView {

    Songs( R.string.songs ),

    Artists( R.string.artists ),

    Albums( R.string.albums ),

    Playlists( R.string.playlists );
}