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

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import me.knighthat.kreate3.R

/** Whether a notification would show, Android 13 and later ask the user first. */
internal fun Context.canPostNotifications(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled()
            && (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED)

/**
 * A notification on [channel] that opens the app when tapped, as the launcher would, through the
 * launcher icon the user picked. On a [AppChannel.isSilent] channel it is silent, even when the
 * user raised the channel's importance.
 */
internal fun Context.notificationBuilder(channel: AppChannel): NotificationCompat.Builder {
    val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let {
        PendingIntent.getActivity(
            this,
            0,
            it,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
    return NotificationCompat.Builder(this, channel.id)
        // The media notification's icon
        .setSmallIcon(R.drawable.ic_monochrome)
        .setContentIntent(openApp)
        // Channels decide this from Android 8 on, before that the notification does
        .setPriority(
            if (channel.importance >= NotificationManagerCompat.IMPORTANCE_DEFAULT)
                NotificationCompat.PRIORITY_DEFAULT
            else NotificationCompat.PRIORITY_LOW
        )
        .setSilent(channel.isSilent)
}

/** Shows [notification] as [id], in place of the one showing with it. */
internal fun Context.notify(id: Int, notification: Notification) = notify(null, id, notification)

/** Shows [notification] as [tag] and [id], in place of the one showing with both. */
internal fun Context.notify(tag: String?, id: Int, notification: Notification) {
    if (!canPostNotifications()) return
    try {
        NotificationManagerCompat.from(this).notify(tag, id, notification)
    } catch (_: SecurityException) {
        // The permission was taken back meanwhile
    }
}

/**
 * Shows a [Message] as a system notification on its [Message.channel], for when the app isn't on
 * screen. Tapping it opens the app; the message's action button isn't offered, it belongs to a
 * screen. Without the user's permission to notify, nothing shows.
 */
class SystemNotifications(private val context: Context) {

    /** Shows [message] in place of the last one of its channel, nothing when it has none. */
    fun post(message: Message) {
        val channel = message.channel ?: return

        val notification = context.notificationBuilder(channel)
            .setContentText(message.text)
            // A long message wraps once the notification is expanded
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setAutoCancel(true)
            .build()
        context.notify(channel.notificationId, notification)
    }
}
