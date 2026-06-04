package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import app.kreate.component.Drawable
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.all_inclusive
import kreate.resources.generated.resources.stat_3months
import kreate.resources.generated.resources.stat_6months
import kreate.resources.generated.resources.stat_month
import kreate.resources.generated.resources.stat_today
import kreate.resources.generated.resources.stat_week
import kreate.resources.generated.resources.stat_year
import me.knighthat.enums.TextView
import org.jetbrains.compose.resources.DrawableResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

enum class StatisticsType(
    val duration: Duration,
    override val iconId: DrawableResource,
    @field:StringRes override val androidTextId: Int
): Drawable, TextView {

    Today(1.days, Res.drawable.stat_today, R.string.today),

    OneWeek(7.days, Res.drawable.stat_week, R.string._1_week),

    OneMonth(30.days, Res.drawable.stat_month, R.string._1_month),

    ThreeMonths(90.days, Res.drawable.stat_3months, R.string._3_month),

    SixMonths(180.days, Res.drawable.stat_6months, R.string._6_month),

    OneYear(365.days, Res.drawable.stat_year, R.string._1_year),

    All(Duration.INFINITE, Res.drawable.all_inclusive, R.string.all);

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