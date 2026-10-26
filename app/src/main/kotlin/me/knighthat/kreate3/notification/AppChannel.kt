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
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import me.knighthat.kreate3.R

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
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val importance: Int,
    val isSilent: Boolean = false,
) {
    PLAYBACK(
        id = "playback",
        titleRes = R.string.notification_playback_title_channel,
        descriptionRes = R.string.notification_playback_subtitle_channel,
        importance = NotificationManagerCompat.IMPORTANCE_DEFAULT,
    ),

    /** One-off notices, not something that happens again and again like each backup. */
    OTHER(
        id = "other",
        titleRes = R.string.notification_other_title_channel,
        descriptionRes = R.string.notification_other_subtitle_channel,
        importance = NotificationManagerCompat.IMPORTANCE_DEFAULT,
    ),

    LOCAL_SCAN(
        id = "local_scan",
        titleRes = R.string.notification_local_scan_title_channel,
        descriptionRes = R.string.notification_local_scan_subtitle_channel,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
    ),

    /** It stays while the timer runs. Silent, the user may be asleep. */
    SLEEP_TIMER(
        id = "sleep_timer",
        titleRes = R.string.notification_sleep_timer_title_channel,
        descriptionRes = R.string.notification_sleep_timer_subtitle_channel,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
        isSilent = true,
    ),

    /**
     * Shows for a moment while the last queue plays again from headphones or the system's media
     * controls, until the media notification takes over.
     */
    RESUME(
        id = "resume",
        titleRes = R.string.notification_resume_title_channel,
        descriptionRes = R.string.notification_resume_subtitle_channel,
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
                .setName(getString(it.titleRes))
                .setDescription(getString(it.descriptionRes))
                .apply {
                    if (it.isSilent) {
                        setSound(null, null)
                        setVibrationEnabled(false)
                    }
                }
                .build()
        }
    )
