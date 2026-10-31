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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import me.knighthat.kreate3.R

/**
 * Opens a link in another app, or says no app can when none is installed (an emulator may have
 * no browser).
 */
@Composable
internal fun rememberOpenLink(): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    val notifier = LocalNotifier.current
    val noBrowser = stringResource(R.string.error_no_browser)
    return remember(uriHandler, notifier, noBrowser) {
        { uri ->
            try {
                uriHandler.openUri(uri)
            } catch (_: IllegalArgumentException) {
                // How the handler says no app can open it
                notifier.post(noBrowser)
            }
        }
    }
}
