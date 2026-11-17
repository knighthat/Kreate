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

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalInspectionMode
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle

// Screens asking for it now; a screen leaving during a transition can dispose after the next one
// started, so the flag is cleared only when none is left
private var secureScreens = 0

/**
 * Keeps the window out of screenshots, screen recordings and the recents preview while the
 * calling screen is shown, for screens where credentials are typed or shown. Does nothing while
 * `Preferences.SCREENSHOT_CREDENTIALS` allows them, and in previews. Dialogs and sheets are
 * windows of their own and need `SecureFlagPolicy.SecureOn` themselves.
 */
@Composable
fun SecureScreen() {
    if (LocalInspectionMode.current) return
    val window = LocalActivity.current?.window ?: return
    val allowed by Preferences.SCREENSHOT_CREDENTIALS.collectAsStateWithLifecycle()
    if (allowed) return

    DisposableEffect(window) {
        if (secureScreens++ == 0)
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        onDispose {
            if (--secureScreens == 0)
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
