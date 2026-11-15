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

package me.knighthat.kreate3.presence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.knighthat.kreate3.AppLanguage
import me.knighthat.kreate3.R
import me.knighthat.kreate3.notification.AppChannel
import me.knighthat.kreate3.notification.notificationBuilder
import me.knighthat.kreate3.notification.notify

/** Clear of the other notifications' ids, see [AppChannel.notificationId]. */
private const val SIGNED_OUT_ID = 8000

/**
 * Tells the user Discord no longer takes the active profile's sign in, with a "Sign in" button
 * that opens Discord's sign in page ([DiscordSignInActivity]).
 */
internal fun Context.postDiscordSignedOut() {
    val resources = AppLanguage.wrap(this).resources
    val discord = resources.getString(R.string.discord)
    val text = resources.getString(R.string.notification_other_subtitle_discord_signed_out)
    val signIn = PendingIntent.getActivity(
        this,
        0,
        Intent(this, DiscordSignInActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notification = notificationBuilder(AppChannel.OTHER)
        .setContentTitle(
            resources.getString(
                R.string.notification_other_title_discord_signed_out,
                discord
            )
        )
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .addAction(0, resources.getString(R.string.action_sign_in), signIn)
        .setAutoCancel(true)
        .build()
    notify(SIGNED_OUT_ID, notification)
}

/** Takes back [postDiscordSignedOut]'s notification, once signed in again. */
internal fun Context.cancelDiscordSignedOut() =
    NotificationManagerCompat.from(this).cancel(SIGNED_OUT_ID)
