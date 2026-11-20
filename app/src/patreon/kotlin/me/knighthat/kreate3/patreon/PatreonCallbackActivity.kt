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

package me.knighthat.kreate3.patreon

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import me.knighthat.kreate3.KreateActivity
import org.koin.android.ext.android.get

/**
 * Where the API sends the browser once the user answered Patreon's page ([PATREON_REDIRECT]).
 * Hands the answer to [PatreonAccount] and brings the app back, showing nothing of its own.
 */
class PatreonCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opened again from the recent apps, the answer was already taken
        if (savedInstanceState == null)
            intent?.data?.let { get<PatreonAccount>().finish(it) }

        startActivity(
            Intent(this, KreateActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
        finish()
    }
}
