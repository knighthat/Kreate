package it.fast4x.rimusic.enums

import app.kreate.component.Drawable
import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.all_inclusive
import kreate.resources.generated.resources.category_albums
import kreate.resources.generated.resources.category_artists
import kreate.resources.generated.resources.category_playlists
import kreate.resources.generated.resources.category_songs
import kreate.resources.generated.resources.stat_3months
import kreate.resources.generated.resources.stat_6months
import kreate.resources.generated.resources.stat_month
import kreate.resources.generated.resources.stat_today
import kreate.resources.generated.resources.stat_week
import kreate.resources.generated.resources.stat_year
import kreate.resources.generated.resources.time_range_all
import kreate.resources.generated.resources.time_range_one_month
import kreate.resources.generated.resources.time_range_one_week
import kreate.resources.generated.resources.time_range_one_year
import kreate.resources.generated.resources.time_range_six_months
import kreate.resources.generated.resources.time_range_three_months
import kreate.resources.generated.resources.time_range_today
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

enum class StatisticsType(
    val duration: Duration,
    override val iconId: DrawableResource,
    override val textId: StringResource
): Drawable, TextView {

    Today(1.days, Res.drawable.stat_today, Res.string.time_range_today),

    OneWeek(7.days, Res.drawable.stat_week, Res.string.time_range_one_week),

    OneMonth(30.days, Res.drawable.stat_month, Res.string.time_range_one_month),

    ThreeMonths(90.days, Res.drawable.stat_3months, Res.string.time_range_three_months),

    SixMonths(180.days, Res.drawable.stat_6months, Res.string.time_range_six_months),

    OneYear(365.days, Res.drawable.stat_year, Res.string.time_range_one_year),

    All(Duration.INFINITE, Res.drawable.all_inclusive, Res.string.time_range_all);

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
    override val textId: StringResource
): TextView {

    Songs(Res.string.category_songs),

    Artists(Res.string.category_artists),

    Albums(Res.string.category_albums),

    Playlists(Res.string.category_playlists);
}