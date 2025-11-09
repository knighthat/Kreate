package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

enum class StatisticsType(
    val duration: Duration,
    @field:DrawableRes override val androidIconId: Int,
    @field:StringRes override val androidTextId: Int
): Drawable, TextView {

    Today( 1.days, R.drawable.stat_today, R.string.today ),

    OneWeek( 7.days, R.drawable.stat_week, R.string._1_week ),

    OneMonth( 30.days, R.drawable.stat_month, R.string._1_month ),

    ThreeMonths( 90.days, R.drawable.stat_3months, R.string._3_month ),

    SixMonths( 180.days, R.drawable.stat_6months, R.string._6_month ),

    OneYear( 365.days, R.drawable.stat_year, R.string._1_year ),

    All( Duration.INFINITE, R.drawable.calendar_clear, R.string.all );

    /**
     * For example:
     * - March 10th 2025 at 14:30 has timestamp of `1,741,617,000,000`.
     * [PastDay.timeStampInMillis] returns `1,741,530,600,000` instead of
     * [Duration.inWholeMilliseconds] which is `86,400,000`.
     *
     * @return real timestamp in millis.
     */
    fun timeStampInMillis() = System.currentTimeMillis() - this.duration.inWholeMilliseconds
}

enum class StatisticsCategory(
    @field:StringRes override val androidTextId: Int
): TextView {

    Songs( R.string.songs ),

    Artists( R.string.artists ),

    Albums( R.string.albums ),

    Playlists( R.string.playlists );
}