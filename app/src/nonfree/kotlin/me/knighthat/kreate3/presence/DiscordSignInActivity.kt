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

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import me.knighthat.kreate3.AppLanguage
import me.knighthat.kreate3.R
import me.knighthat.kreate3.notification.Notifier
import org.koin.android.ext.android.get

/**
 * The "Sign in" button of [postDiscordSignedOut]'s notification: starts a sign in of the active
 * profile and opens Discord's page, showing nothing of its own. An activity, not a receiver:
 * Android 12 and up don't let a receiver started from a notification open another app.
 */
class DiscordSignInActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opened again from the recent apps, the page was already opened
        if (savedInstanceState == null) openSignIn()
        finish()
    }

    private fun openSignIn() {
        val account = get<DiscordAccount>()
        val page = Intent(Intent.ACTION_VIEW, Uri.parse(account.start()))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(page)
        } catch (_: ActivityNotFoundException) {
            account.cancel()
            get<Notifier>().post(AppLanguage.wrap(this).resources.getString(R.string.error_no_browser))
        }
    }
}
