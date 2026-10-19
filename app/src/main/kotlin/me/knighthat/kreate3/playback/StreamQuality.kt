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
package me.knighthat.kreate3.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import androidx.core.net.ConnectivityManagerCompat
import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.youtube.YouTubeProvider

/**
 * The quality the songs of [origin] are streamed in now: the mobile data setting of the origin
 * on mobile data and other metered networks, its Wi-Fi setting on the others, like
 * [Preferences.YOUTUBE_QUALITY_WIFI]. YouTube has others while someone is signed in, like
 * [Preferences.YOUTUBE_QUALITY_WIFI_PREMIUM]. Songs on the device are [StreamQuality.ORIGINAL].
 */
internal suspend fun Context.streamQuality(
    origin: Origin,
    youTube: YouTubeProvider,
): StreamQuality {
    val (wifi, mobile) = when (origin) {
        Origin.LOCAL -> return StreamQuality.ORIGINAL
        Origin.YOUTUBE ->
            // The session Original is asked in, so its settings follow it
            if (youTube.isSignedIn())
                Preferences.YOUTUBE_QUALITY_WIFI_PREMIUM to
                        Preferences.YOUTUBE_QUALITY_MOBILE_PREMIUM
            else Preferences.YOUTUBE_QUALITY_WIFI to Preferences.YOUTUBE_QUALITY_MOBILE

        Origin.OPEN_SUBSONIC ->
            Preferences.OPEN_SUBSONIC_QUALITY_WIFI to Preferences.OPEN_SUBSONIC_QUALITY_MOBILE
    }
    val metered = getSystemService<ConnectivityManager>()
        ?.let(ConnectivityManagerCompat::isActiveNetworkMetered)
        ?: false
    return (if (metered) mobile else wifi).value
}

/** Whether the device has a network that reaches the internet, so songs can be streamed. */
internal fun Context.isOnline(): Boolean {
    val connectivity = getSystemService<ConnectivityManager>() ?: return true
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
