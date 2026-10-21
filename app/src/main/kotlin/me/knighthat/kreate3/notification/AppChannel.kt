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

package me.knighthat.kreate3.notification

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat

/**
 * A kind of system notification, which the user can turn off or silence on its own in the
 * system's settings. The media notification isn't one, Media3 makes its own channel.
 *
 * Renaming [id] makes a new channel and leaves the old one, with its settings, behind.
 *
 * @param importance one of the `NotificationManagerCompat.IMPORTANCE_*`, only the first time
 * the channel is made, the user's choice wins after that
 * @param isSilent whether its notifications never make a sound or vibrate, whatever the user
 * sets the channel to, see [notificationBuilder]
 */
enum class AppChannel(
    val id: String,
    val title: String,
    val description: String,
    val importance: Int,
    val isSilent: Boolean = false,
) {
    PLAYBACK(
        id = "playback",
        title = "Playback problems",
        description = "A song couldn't be played",
        importance = NotificationManagerCompat.IMPORTANCE_DEFAULT,
    ),

    /** One-off notices, not something that happens again and again like each backup. */
    OTHER(
        id = "other",
        title = "Other",
        description = "One-off notices, like a backup that couldn't be restored",
        importance = NotificationManagerCompat.IMPORTANCE_DEFAULT,
    ),

    LOCAL_SCAN(
        id = "local_scan",
        title = "Music on this device",
        description = "Progress of reading the device's music and measuring its loudness",
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
    ),

    /** It stays while the timer runs. Silent, the user may be asleep. */
    SLEEP_TIMER(
        id = "sleep_timer",
        title = "Sleep timer",
        description = "Time left until the music pauses, with buttons to change it",
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
        isSilent = true,
    ),

    /**
     * Shows for a moment while the last queue plays again from headphones or the system's media
     * controls, until the media notification takes over.
     */
    RESUME(
        id = "resume",
        title = "Resuming music",
        description = "Shows for a moment while headphones or the system's media controls play the last queue again",
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
        isSilent = true,
    );

    /** The id of a [Message]'s notification, a new one replaces the one already showing. */
    val notificationId: Int
        // Clear of Media3's media notification, 1001, LocalScanNotifications' 3000s and the
        // sleep timer's 4000
        get() = 2000 + ordinal
}

/** Makes every [AppChannel], or updates their names. Safe to call at every start. */
internal fun Context.createNotificationChannels() =
    NotificationManagerCompat.from(this).createNotificationChannelsCompat(
        AppChannel.entries.map {
            NotificationChannelCompat.Builder(it.id, it.importance)
                .setName(it.title)
                .setDescription(it.description)
                .apply {
                    if (it.isSilent) {
                        setSound(null, null)
                        setVibrationEnabled(false)
                    }
                }
                .build()
        }
    )
