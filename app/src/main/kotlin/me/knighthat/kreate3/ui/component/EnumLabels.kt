/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package me.knighthat.kreate3.ui.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.knighthat.kreate3.AppIcon
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.AutoTapAction
import me.knighthat.kreate3.constant.ContrastLevel
import me.knighthat.kreate3.constant.OpenSubsonicAuth
import me.knighthat.kreate3.constant.PlaybackFailureAction
import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.constant.SyncMode
import me.knighthat.kreate3.constant.ThemeMode
import me.knighthat.kreate3.model.LyricsProvider
import me.knighthat.kreate3.ui.screen.account.HistoryRangePreset
import me.knighthat.kreate3.ui.screen.account.StatisticsPeriod

/**
 * The texts of [options] for a choice, read from [res] once so a non-composable `label` can look
 * them up.
 */
@Composable
fun <T> labelsOf(options: Iterable<T>, res: (T) -> Int): (T) -> String {
    val labels = options.associateWith { stringResource(res(it)) }
    return { labels.getValue(it) }
}

/** The name of a lyrics provider, as its source is called. */
@get:StringRes
val LyricsProvider.sourceRes: Int
    get() = when (this) {
        LyricsProvider.BETTER_LYRICS -> R.string.source_better_lyrics
        LyricsProvider.LYRICS_PLUS -> R.string.source_lyrics_plus
        LyricsProvider.LRCLIB -> R.string.source_lrclib
        LyricsProvider.YOUTUBE -> R.string.source_ytm
    }

@get:StringRes
val HistoryRangePreset.labelRes: Int
    get() = when (this) {
        HistoryRangePreset.ALL_TIME -> R.string.constant_history_range_all_time
        HistoryRangePreset.TODAY -> R.string.constant_history_range_today
        HistoryRangePreset.LAST_7_DAYS -> R.string.constant_history_range_last_7_days
        HistoryRangePreset.LAST_30_DAYS -> R.string.constant_history_range_last_30_days
        HistoryRangePreset.THIS_YEAR -> R.string.constant_history_range_this_year
    }

@get:StringRes
val StatisticsPeriod.labelRes: Int
    get() = when (this) {
        StatisticsPeriod.ALL_TIME -> R.string.constant_statistics_period_all_time
        StatisticsPeriod.THIS_MONTH -> R.string.constant_statistics_period_this_month
    }

@get:StringRes
val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.AUTOMATIC -> R.string.constant_theme_mode_automatic
        ThemeMode.AUTOMATIC_DYNAMIC -> R.string.constant_theme_mode_automatic_dynamic
        ThemeMode.LIGHT -> R.string.constant_theme_mode_light
        ThemeMode.LIGHT_DYNAMIC -> R.string.constant_theme_mode_light_dynamic
        ThemeMode.DARK -> R.string.constant_theme_mode_dark
        ThemeMode.DARK_DYNAMIC -> R.string.constant_theme_mode_dark_dynamic
        ThemeMode.PITCH_BLACK -> R.string.constant_theme_mode_pitch_black
        ThemeMode.THUMBNAIL -> R.string.constant_theme_mode_thumbnail
    }

@get:StringRes
val ContrastLevel.labelRes: Int
    get() = when (this) {
        ContrastLevel.NORMAL -> R.string.constant_contrast_level_normal
        ContrastLevel.MEDIUM -> R.string.constant_contrast_level_medium
        ContrastLevel.HIGH -> R.string.constant_contrast_level_high
    }

@get:StringRes
val AppIcon.labelRes: Int
    get() = when (this) {
        AppIcon.BLACK -> R.string.constant_app_icon_black
        AppIcon.GRADIENT -> R.string.constant_app_icon_gradient
    }

@get:StringRes
val SyncMode.labelRes: Int
    get() = when (this) {
        SyncMode.OFF -> R.string.constant_sync_mode_off
        SyncMode.LIKED -> R.string.constant_sync_mode_liked
        SyncMode.ALL -> R.string.constant_sync_mode_all
    }

@get:StringRes
val OpenSubsonicAuth.labelRes: Int
    get() = when (this) {
        OpenSubsonicAuth.PASSWORD -> R.string.constant_opensubsonic_auth_password
        OpenSubsonicAuth.API_KEY -> R.string.constant_opensubsonic_auth_api_key
    }

@get:StringRes
val AutoTapAction.labelRes: Int
    get() = when (this) {
        AutoTapAction.OPEN -> R.string.constant_auto_tap_action_open
        AutoTapAction.PLAY_FROM_START -> R.string.constant_auto_tap_action_play_from_start
        AutoTapAction.PLAY_RANDOMLY -> R.string.constant_auto_tap_action_play_randomly
    }

@get:StringRes
val PlaybackFailureAction.labelRes: Int
    get() = when (this) {
        PlaybackFailureAction.SKIP -> R.string.constant_playback_failure_action_skip
        PlaybackFailureAction.RETRY_THEN_SKIP -> R.string.constant_playback_failure_action_retry_then_skip
        PlaybackFailureAction.RETRY_THEN_STOP -> R.string.constant_playback_failure_action_retry_then_stop
        PlaybackFailureAction.STOP -> R.string.constant_playback_failure_action_stop
    }

/** How good a song of YouTube is, what each quality means there. */
@get:StringRes
val StreamQuality.youTubeLabelRes: Int
    get() = when (this) {
        StreamQuality.LOW -> R.string.constant_stream_quality_ytm_low
        StreamQuality.MEDIUM -> R.string.constant_stream_quality_ytm_medium
        StreamQuality.HIGH -> R.string.constant_stream_quality_ytm_high
        StreamQuality.ORIGINAL -> R.string.constant_stream_quality_ytm_original
    }

/** How good a song of the OpenSubsonic server is, what each quality means there. */
@get:StringRes
val StreamQuality.openSubsonicLabelRes: Int
    get() = when (this) {
        StreamQuality.LOW -> R.string.constant_stream_quality_opensubsonic_low
        StreamQuality.MEDIUM -> R.string.constant_stream_quality_opensubsonic_medium
        StreamQuality.HIGH -> R.string.constant_stream_quality_opensubsonic_high
        StreamQuality.ORIGINAL -> R.string.constant_stream_quality_opensubsonic_original
    }
