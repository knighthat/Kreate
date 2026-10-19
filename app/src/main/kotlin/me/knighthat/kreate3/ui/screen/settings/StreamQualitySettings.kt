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
package me.knighthat.kreate3.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.preferences.Preference
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle

/**
 * How good the songs of a source streamed on Wi-Fi and on mobile data are, a slider for each.
 * A quality that isn't one of [options] shows as [StreamQuality.HIGH], which it plays as.
 */
@Composable
fun StreamQualitySettings(
    wifi: Preference<StreamQuality>,
    mobile: Preference<StreamQuality>,
    label: (StreamQuality) -> String,
    options: List<StreamQuality> = StreamQuality.entries,
) {
    val onWifi by wifi.collectAsStateWithLifecycle()
    val onMobile by mobile.collectAsStateWithLifecycle()

    SliderSetting(
        title = "On Wi-Fi",
        value = onWifi.offeredIn(options),
        options = options,
        label = label,
        onSelect = wifi::update,
    )
    SliderSetting(
        title = "On mobile data",
        value = onMobile.offeredIn(options),
        options = options,
        label = label,
        onSelect = mobile::update,
    )
}

private fun StreamQuality.offeredIn(options: List<StreamQuality>): StreamQuality =
    if (this in options) this else StreamQuality.HIGH
